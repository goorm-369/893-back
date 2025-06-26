package com.samyookgoo.palgoosam.auction.scheduler;

import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.auction.service.ScrapRedisService;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.bid.service.BidRedisService;
import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionStatusScheduler {
    private final AuctionRepository auctionRepository;
    private final BidRedisService bidRedisService;
    private final ScrapRedisService scrapRedisService;
    private final ScrapRepository scrapRepository;
    private final BidRepository bidRepository;

//
//    @Scheduled(fixedDelay = 60000)  // 1분마다 실행
//    @Transactional
//    public void updateAuctionStatus() {
//        LocalDateTime now = LocalDateTime.now();
//
//        int activeUpdated = auctionRepository.updateStatusToActive(now);
//        int completedUpdated = auctionRepository.updateStatusToCompleted(now);
//
//        log.info("경매 상태 업데이트: ACTIVE {}건", activeUpdated);
//        log.info("경매 상태 업데이트: COMPLETED {}건", completedUpdated);
//    }
//
//    @Scheduled(fixedDelay = 120000)  // 2분마다 실행
//    @Transactional(readOnly = true)
//    public void warmUpActiveAuctionCache() {
//
//        log.info("Cache warming 작업 시작");
//
//        try {
//            List<Long> activeAuctionIds = auctionRepository.getActiveAuctionIds();
//            log.info("캐시 대상 경매 수: {}", activeAuctionIds.size());
//
//            int successCount = 0;
//            int failureCount = 0;
//
//            for (Long auctionId : activeAuctionIds) {
//                try {
//                    warmUpSingleAuction(auctionId);
//                    successCount++;
//
//                    if (successCount % 100 == 0) {
//                        Thread.sleep(100);
//                        log.info("Cache warming 진행 중... {}/{}", successCount, activeAuctionIds.size());
//                    }
//                } catch (Exception e) {
//                    failureCount++;
//                    log.warn("경매 ID {} 캐시 warming 실패: {}", auctionId, e.getMessage());
//                }
//
//            }
//
//            log.info("Cache warming 작업 완료. 성공: {}, 실패: {}", successCount, failureCount);
//        } catch (Exception e) {
//            log.error("Cache warming 작업 중 오류 발생", e);
//        }
//    }
//
//    private void warmUpSingleAuction(Long auctionId) {
//        // 1. 입찰자 수 계산 및 캐싱
//        Long bidderCount = bidRepository.countBidderByAuctionId(auctionId);
//        bidRedisService.warmUpBidderCount(auctionId, bidderCount);
//
//        // 2. 스크랩 수 계산 및 캐싱
//        Long scrapCount = scrapRepository.countScrapCountByAuctionId(auctionId);
//        scrapRedisService.warmUpScrapCount(auctionId, scrapCount);
//    }
}
