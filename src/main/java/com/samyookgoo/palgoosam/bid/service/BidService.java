package com.samyookgoo.palgoosam.bid.service;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.exception.AuctionNotFoundException;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.bid.controller.response.BidEventResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidOverviewResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidResultResponse;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.exception.BidBadRequestException;
import com.samyookgoo.palgoosam.bid.exception.BidInvalidStateException;
import com.samyookgoo.palgoosam.bid.exception.BidNotFoundException;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.bid.service.response.BidStatsResponse;
import com.samyookgoo.palgoosam.global.exception.ErrorCode;
import com.samyookgoo.palgoosam.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BidService {
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final SseService sseService;
    private final BidRedisService bidRedisService;

    @Transactional(readOnly = true)
    public BidOverviewResponse getBidOverview(Long auctionId, User user) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException();
        }

        List<Bid> allBids = getByAuctionIdOrderByCreatedAtDesc(auctionId);

        Map<Boolean, List<Bid>> partitioned = allBids.stream()
                .collect(Collectors.partitioningBy(Bid::isCancelled));

        List<BidResponse> activeBids = partitioned.get(false).stream()
                .map(BidResponse::from)
                .collect(Collectors.toList());

        List<BidResponse> cancelledBids = partitioned.get(true).stream()
                .map(BidResponse::from)
                .collect(Collectors.toList());

        BidResponse recentUserBid = null;
        if (user != null && !hasUserCancelledBid(auctionId, user.getId())) {
            LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
            recentUserBid = findRecentUserBid(partitioned.get(false), user.getId(), oneMinuteAgo);
        }

        BidStatsResponse bidStats = getBidStatsByAuctionId(auctionId);
        return BidOverviewResponse.builder()
                .auctionId(auctionId)
                .currentPrice(bidStats.getMaxPrice())
                .totalBid(bidStats.getTotalBid())
                .totalBidder(bidStats.getTotalBidder())
                .canCancelBid(recentUserBid != null)
                .recentUserBid(recentUserBid)
                .bids(activeBids)
                .cancelledBids(cancelledBids)
                .build();
    }

    @Transactional
    public BidResultResponse placeBid(Long auctionId, User user, int price) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(AuctionNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        Bid newBid = createValidatedBid(auction, user, price, now);

        deactivatePreviousWinningBid(auctionId);

        bidRepository.save(newBid);
        bidRedisService.incrementBidderCount(auctionId, user.getId());
        BidEventResponse event = createBidEventResponse(auctionId, newBid, false);
        broadcastBidEvent(auctionId, event);

        boolean canCancelBid = !hasUserCancelledBid(auctionId, user.getId());
        return BidResultResponse.from(BidResponse.from(newBid), canCancelBid);
    }

    @Transactional
    public void cancelBid(Long auctionId, Long bidId, Long userId, LocalDateTime now) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException();
        }

        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(BidNotFoundException::new);

        validateBidCancelable(auctionId, userId, bid, now);

        bid.cancel();

        activateNewWinningBid(auctionId);
        bidRedisService.decrementBidderCount(auctionId, userId);
        BidEventResponse event = createBidEventResponse(auctionId, bid, true);
        broadcastBidEvent(auctionId, event);
    }

    private List<Bid> getByAuctionIdOrderByCreatedAtDesc(Long auctionId) {
        return bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);
    }

    private BidResponse findRecentUserBid(List<Bid> activeBids, Long userId, LocalDateTime thresholdTime) {
        return activeBids.stream()
                .filter(bid -> bid.isOwner(userId) && bid.getCreatedAt().isAfter(thresholdTime))
                .findFirst()
                .map(BidResponse::from)
                .orElse(null);
    }

    private void validateBidCancelable(Long auctionId, Long userId, Bid bid, LocalDateTime now) {
        bid.validateCancelConditions(userId, now);

        if (hasUserCancelledBid(auctionId, userId)) {
            throw new BidInvalidStateException(ErrorCode.BID_CANCEL_LIMIT_EXCEEDED);
        }
    }

    private boolean hasUserCancelledBid(Long auctionId, Long userId) {
        return bidRepository.existsByAuctionIdAndBidderIdAndIsDeletedTrue(auctionId, userId);
    }

    private Bid createValidatedBid(Auction auction, User user, int price, LocalDateTime now) {
        if (price > 1_000_000_000) {
            throw new BidBadRequestException(ErrorCode.BID_EXCEEDS_MAXIMUM);
        }

        auction.validateBidConditions(user.getId(), price, now);
        validatePriceIsHighest(auction.getId(), price);

        return Bid.placeBy(auction, user, price);
    }

    private void deactivatePreviousWinningBid(Long auctionId) {
        bidRepository.findTopValidBidByAuctionId(auctionId)
                .ifPresent(prev -> prev.setIsWinning(false));
    }

    private void activateNewWinningBid(Long auctionId) {
        bidRepository.findTopValidBidByAuctionId(auctionId)
                .ifPresent(newWinner -> newWinner.setIsWinning(true));
    }


    private void validatePriceIsHighest(Long auctionId, int price) {
        Integer highestPrice = bidRepository.findMaxBidPriceByAuctionId(auctionId);
        if (highestPrice != null && price <= highestPrice) {
            throw new BidBadRequestException(ErrorCode.BID_NOT_HIGHEST);
        }
    }

    private BidEventResponse createBidEventResponse(Long auctionId, Bid bid, boolean isCancelled) {
        BidStatsResponse bidStats = getBidStatsByAuctionId(auctionId);
        return BidEventResponse.builder()
                .currentPrice(bidStats.getMaxPrice())
                .totalBid(bidStats.getTotalBid())
                .totalBidder(bidStats.getTotalBidder())
                .isCancelled(isCancelled)
                .bid(BidResponse.from(bid))
                .build();
    }

    private BidStatsResponse getBidStatsByAuctionId(Long auctionId) {
        return bidRepository.findBidStatsByAuctionId(auctionId);
    }

    private void broadcastBidEvent(Long auctionId, BidEventResponse event) {
        sseService.broadcastBidUpdate(auctionId, event);
    }
}
