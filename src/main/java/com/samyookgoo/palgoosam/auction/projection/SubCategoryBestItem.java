package com.samyookgoo.palgoosam.auction.projection;

import com.samyookgoo.palgoosam.auction.constant.AuctionStatus;
import com.samyookgoo.palgoosam.auction.constant.ItemCondition;
import java.time.LocalDateTime;

public interface SubCategoryBestItem {
    Long getAuctionId();

    String getTitle();

    AuctionStatus getStatus();

    ItemCondition getItemCondition();

    String getThumbnailUrl();

    LocalDateTime getStartTime();
}
