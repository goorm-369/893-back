package com.samyookgoo.palgoosam.bid.service;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.bid.controller.response.BidEventResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidListResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidResponse;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.projection.AuctionMaxBid;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.notification.service.NotificationService;
import com.samyookgoo.palgoosam.notification.subscription.constant.SubscriptionType;
import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BidService {
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public Map<Long, Integer> getAuctionMaxPrices(List<Long> auctionIds) {
        return bidRepository
                .findMaxBidPricesByAuctionIds(auctionIds)
                .stream()
                .collect(Collectors.toMap(
                        AuctionMaxBid::getAuctionId,
                        AuctionMaxBid::getMaxPrice
                ));
    }

    public BidListResponse getBidsByAuctionId(Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new NoSuchElementException("해당 경매를 찾을 수 없습니다.");
        }

        List<Bid> allBids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);

        List<BidResponse> bids = allBids.stream()
                .filter(b -> !b.getIsDeleted())
                .map(this::mapToResponse)
                .toList();

        List<BidResponse> cancelledBids = allBids.stream()
                .filter(Bid::getIsDeleted)
                .map(this::mapToResponse)
                .toList();

        int totalBid = allBids.size();
        int totalBidder = (int) allBids.stream()
                .map(b -> b.getBidder().getId())
                .distinct()
                .count();

        return BidListResponse.builder()
                .auctionId(auctionId)
                .totalBid(totalBid)
                .totalBidder(totalBidder)
                .bids(bids)
                .cancelledBids(cancelledBids)
                .build();
    }

    @Transactional
    public BidEventResponse placeBid(Long auctionId, Long userId, int price) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NoSuchElementException("해당 경매를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("해당 사용자를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(auction.getStartTime()) || now.isAfter(auction.getEndTime())) {
            throw new IllegalStateException("현재는 입찰 가능한 시간이 아닙니다.");
        }

        if (auction.getBasePrice() > price) {
            throw new IllegalArgumentException("시작가보다 높은 금액을 입력해야 합니다.");
        }

        Integer highestPrice = bidRepository.findMaxBidPriceByAuctionId(auctionId);
        if (highestPrice != null && price <= highestPrice) {
            throw new IllegalArgumentException("현재 최고가보다 높은 금액을 입력해야 합니다.");
        }

        clearPreviousWinningBid(auctionId);

        Bid bid = Bid.builder()
                .auction(auction)
                .bidder(user)
                .price(price)
                .isWinning(Boolean.TRUE)
                .isDeleted(Boolean.FALSE)
                .build();

        Bid savedBid = bidRepository.save(bid);
        // TODO: MapStruct, ModelMapper 논의 후 사용 예정
        BidResponse bidResponse = mapToResponse(savedBid);
        notificationService.subscribe(auctionId, SubscriptionType.BIDDER);
        return createBidEventResponse(auctionId, bidResponse, false);
    }

    @Transactional
    public BidEventResponse cancelBid(Long auctionId, Long bidId, User currentUser) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new NoSuchElementException("해당 입찰 내역이 존재하지 않습니다."));

        if (!bid.getBidder().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 입찰만 취소할 수 있습니다.");
        }

        if (bid.getIsDeleted() == Boolean.TRUE) {
            throw new IllegalStateException("이미 취소된 입찰입니다.");
        }

        LocalDateTime cancellableUntil = bid.getCreatedAt().plusMinutes(1);
        if (LocalDateTime.now().isAfter(cancellableUntil)) {
            throw new IllegalStateException("입찰 후 1분 이내에만 취소할 수 있습니다.");
        }

        Integer highestPrice = bidRepository.findMaxBidPriceByAuctionId(auctionId);
        if (!Objects.equals(bid.getPrice(), highestPrice)) {
            throw new IllegalStateException("최고 입찰가가 아니면 취소할 수 없습니다.");
        }

        bid.setIsWinning(Boolean.FALSE);
        bid.setIsDeleted(Boolean.TRUE);

        updateWinningBid(auctionId);

        BidResponse bidResponse = mapToResponse(bid);
        notificationService.unsubscribe(auctionId, SubscriptionType.BIDDER);
        return createBidEventResponse(auctionId, bidResponse, true);
    }

    private void clearPreviousWinningBid(Long auctionId) {
        bidRepository.findByAuctionIdAndIsWinningTrue(auctionId)
                .ifPresent(prev -> prev.setIsWinning(false));
    }

    private void updateWinningBid(Long auctionId) {
        bidRepository.findTopValidBidByAuctionId(auctionId)
                .ifPresent(newWinner -> {
                    newWinner.setIsWinning(true);
                    bidRepository.save(newWinner);
                });
    }

    private BidEventResponse createBidEventResponse(Long auctionId, BidResponse bidResponse, boolean isCancelled) {
        Integer currentPrice = bidRepository.findMaxBidPriceByAuctionId(auctionId);
        int totalBid = bidRepository.countByAuctionId(auctionId);
        int totalBidder = bidRepository.countDistinctBidderByAuctionId(auctionId);

        return BidEventResponse.builder()
                .currentPrice(currentPrice)
                .totalBid(totalBid)
                .totalBidder(totalBidder)
                .isCancelled(isCancelled)
                .bid(bidResponse)
                .build();
    }

    private BidResponse mapToResponse(Bid bid) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return BidResponse.builder()
                .bidId(bid.getId())
                .bidderEmail(bid.getBidder().getEmail())
                .bidPrice(bid.getPrice())
                .createdAt(bid.getCreatedAt().format(formatter))
                .updatedAt(bid.getUpdatedAt() != null ? bid.getUpdatedAt().format(formatter) : null)
                .build();
    }
}
