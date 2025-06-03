package com.samyookgoo.palgoosam.auction.controller;

import com.samyookgoo.palgoosam.auction.dto.home.ActiveRankingResponse;
import com.samyookgoo.palgoosam.auction.dto.home.DashboardResponse;
import com.samyookgoo.palgoosam.auction.dto.home.PendingRankingResponse;
import com.samyookgoo.palgoosam.auction.dto.home.RecentAuctionResponse;
import com.samyookgoo.palgoosam.auction.dto.home.SubCategoryBestItemResponse;
import com.samyookgoo.palgoosam.auction.dto.home.TopBidResponse;
import com.samyookgoo.palgoosam.auction.dto.home.UpcomingAuctionResponse;
import com.samyookgoo.palgoosam.auction.service.HomeService;
import com.samyookgoo.palgoosam.common.response.BaseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final HomeService homeService;

    @GetMapping("/dashboard")
    public ResponseEntity<BaseResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(BaseResponse.success("대시보드 조회 성공", homeService.getDashboard()));
    }

    @GetMapping("/recentAuction")
    public ResponseEntity<BaseResponse<List<RecentAuctionResponse>>> getRecentAuction() {
        List<RecentAuctionResponse> responses = homeService.getRecentAuctions();
        return ResponseEntity.ok(BaseResponse.success("최근 등록한 상품 목록 조회 성공", responses));
    }


    @GetMapping("/upcoming")
    public ResponseEntity<BaseResponse<List<UpcomingAuctionResponse>>> getUpcomingAuctions() {
        List<UpcomingAuctionResponse> responses = homeService.getUpcomingAuctions();
        return ResponseEntity.ok(BaseResponse.success("임박한 경매 상품 목록 조회 성공", responses));
    }

    @GetMapping("/topBid")
    public ResponseEntity<BaseResponse<List<TopBidResponse>>> getTopBid() {
        List<TopBidResponse> responses = homeService.getTopBid();
        return ResponseEntity.ok(BaseResponse.success("이번주 최고 낙찰가 top 5 조회 성공", responses));
    }

    @GetMapping("/ranking/active")
    public ResponseEntity<BaseResponse<List<ActiveRankingResponse>>> getActiveRanking() {
        List<ActiveRankingResponse> responses = homeService.getActiveRanking();
        return ResponseEntity.ok(BaseResponse.success("경매중 실시간 랭킹 조회 성공", responses));
    }

    @GetMapping("/ranking/pending")
    public ResponseEntity<BaseResponse<List<PendingRankingResponse>>> getPendingRanking() {
        List<PendingRankingResponse> responses = homeService.getPendingRanking();
        return ResponseEntity.ok(BaseResponse.success("경매 예정 실시간 랭킹 조회 성공", responses));
    }

    @GetMapping("/best-sub-item")
    public ResponseEntity<BaseResponse<List<SubCategoryBestItemResponse>>> getBestItem() {
        List<SubCategoryBestItemResponse> responses = homeService.getSubCategoryBestItem();
        return ResponseEntity.ok(BaseResponse.success("중분류 카테고리별 베스트 상품 조회 성공", responses));
    }
}
