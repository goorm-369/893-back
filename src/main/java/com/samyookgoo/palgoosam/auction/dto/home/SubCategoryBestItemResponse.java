package com.samyookgoo.palgoosam.auction.dto.home;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubCategoryBestItemResponse {
    private Long subCategoryId;
    private String subCategoryName;
    private List<BestItemResponse> items;
}
