package com.samyookgoo.palgoosam.auction.dto.home;

import com.samyookgoo.palgoosam.auction.constant.AuctionStatus;
import com.samyookgoo.palgoosam.auction.constant.ItemCondition;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BestItemResponse {
    private Long auctionId;
    private String title;
    private AuctionStatus status;
    private ItemCondition itemCondition;
    private String thumbnailUrl;
    private Boolean isAuctionImminent;  // 경매 임박 여부
    private Integer scrapCount;
    private Integer rankNum;
}
