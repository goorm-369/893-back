package com.samyookgoo.palgoosam.bid.service;

import com.samyookgoo.palgoosam.auction.exception.AuctionNotFoundException;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.bid.controller.response.BidEventResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidOverviewResponse;
import com.samyookgoo.palgoosam.bid.controller.response.BidResponse;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.exception.BidInvalidStateException;
import com.samyookgoo.palgoosam.bid.exception.BidNotFoundException;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.bid.service.response.BidStatsResponse;
import com.samyookgoo.palgoosam.common.lock.LockInfo;
import com.samyookgoo.palgoosam.common.lock.LockRetryHandler;
import com.samyookgoo.palgoosam.common.lock.TaskWrapper;
import com.samyookgoo.palgoosam.common.service.RedisLockService;
import com.samyookgoo.palgoosam.global.exception.ErrorCode;
import com.samyookgoo.palgoosam.user.domain.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final SseService sseService;
    private final RedisLockService redisLockService;
    private final BidExecutorService bidExecutorService;
    private final LockRetryHandler lockRetryHandler;

    @Transactional(readOnly = true)
    public BidOverviewResponse getBidOverview(Long auctionId, User user) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException();
        }

        List<Bid> allBids = findByAuctionIdOrderByPriceDesc(auctionId);

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

    public void placeBidWithLock(Long auctionId, User user, int price) {
        String lockKey = "lock:bid:" + auctionId;
        String uniqueId = UUID.randomUUID().toString();

        if (!redisLockService.tryAcquire(lockKey, Duration.ofSeconds(10), uniqueId)) {
            TaskWrapper wrapper = new TaskWrapper(() -> {
                String innerUniqueId = UUID.randomUUID().toString();

                if (!redisLockService.tryAcquire(lockKey, Duration.ofSeconds(10), innerUniqueId)) {
                    log.warn("TaskWrapper 실행 중 락 획득 실패 - lockKey={}", lockKey);
                    return;
                }

                bidExecutorService.placeBid(auctionId, user, price, new LockInfo(lockKey, innerUniqueId));

            }, SecurityContextHolder.getContext());

            lockRetryHandler.register(lockKey, wrapper);
            log.info("입찰 요청이 등록 되었습니다. auctionId = {}, price = {}", auctionId, price);
            return;
        }

        bidExecutorService.placeBid(auctionId, user, price, new LockInfo(lockKey, uniqueId));
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

        BidEventResponse event = createBidEventResponse(auctionId, bid, true);
        broadcastBidEvent(auctionId, event);
    }

    private List<Bid> findByAuctionIdOrderByPriceDesc(Long auctionId) {
        return bidRepository.findByAuctionIdOrderByPriceDesc(auctionId);
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

    private void activateNewWinningBid(Long auctionId) {
        bidRepository.findTopValidBidByAuctionId(auctionId)
                .ifPresent(Bid::win);
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

    public void broadcastBidEvent(Long auctionId, BidEventResponse event) {
        sseService.broadcastBidUpdate(auctionId, event);
    }
}
