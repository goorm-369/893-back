package com.samyookgoo.palgoosam.auction.service;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.domain.AuctionImage;
import com.samyookgoo.palgoosam.auction.domain.AuctionStatus;
import com.samyookgoo.palgoosam.auction.domain.Category;
import com.samyookgoo.palgoosam.auction.dto.AuctionListItemDto;
import com.samyookgoo.palgoosam.auction.dto.AuctionSearchRequestDto;
import com.samyookgoo.palgoosam.auction.dto.AuctionSearchResponseDto;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionCreateRequest;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionImageRequest;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionUpdateRequest;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionCreateResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionDetailResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionImageResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionUpdatePageResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionUpdateResponse;
import com.samyookgoo.palgoosam.auction.dto.response.CategoryResponse;
import com.samyookgoo.palgoosam.auction.dto.response.RelatedAuctionResponse;
import com.samyookgoo.palgoosam.auction.file.FileStore;
import com.samyookgoo.palgoosam.auction.file.ResultFileStore;
import com.samyookgoo.palgoosam.auction.repository.AuctionImageRepository;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.auction.repository.CategoryRepository;
import com.samyookgoo.palgoosam.auth.service.AuthService;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.notification.service.NotificationService;
import com.samyookgoo.palgoosam.notification.subscription.constant.SubscriptionType;
import com.samyookgoo.palgoosam.payment.domain.Payment;
import com.samyookgoo.palgoosam.user.domain.Scrap;
import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuctionService {

    private final AuctionImageRepository auctionImageRepository;
    private final AuctionRepository auctionRepository;
    private final CategoryRepository categoryRepository;
    private final ScrapRepository scrapRepository;
    private final FileStore fileStore;
    private final BidRepository bidRepository;
    private final AuthService authService;
    private final NotificationService notificationService;


    public List<Long> getAuctionIdsByAuctions(List<Auction> auctions) {
        return auctions.stream()
                .map(Auction::getId)
                .distinct()
                .toList();
    }

    public List<Long> getAuctionIdsByBids(List<Bid> bids) {
        return bids.stream()
                .map(b -> b.getAuction().getId())
                .distinct()
                .toList();
    }

    public List<Long> getAuctionIdsByScraps(List<Scrap> scraps) {
        return scraps.stream()
                .map(s -> s.getAuction().getId())
                .distinct()
                .toList();
    }

    public List<Long> getAuctionIdsByPayment(List<Payment> payments) {
        return payments.stream()
                .map(p -> p.getAuction().getId())
                .distinct()
                .toList();
    }

    public List<Auction> getAuctionsByAuctionIds(List<Long> auctionIds) {
        return auctionRepository.findAllById(auctionIds);
    }

    @Transactional
    public AuctionCreateResponse createAuction(AuctionCreateRequest request, List<ResultFileStore> resultFileStores) {
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UsernameNotFoundException("유저를 찾을 수 없습니다.");
        }

        Category category = categoryRepository.findById(request.getCategory().getId())
                .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다."));

        validateLeafCategory(category);

        validateAuctionTime(request.getStartDelay(), request.getDurationTime());

        validateBasePrice(request.getBasePrice());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.plusMinutes(request.getStartDelay());
        LocalDateTime endTime = startTime.plusMinutes(request.getDurationTime());

        Auction auction = Auction.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .itemCondition(request.getItemCondition())
                .startTime(startTime)
                .endTime(endTime)
                .category(category)
                .seller(user)
                .status(AuctionStatus.pending)
                .build();

        auctionRepository.save(auction);

        List<AuctionImageResponse> imageResponses = new ArrayList<>();
        for (int i = 0; i < resultFileStores.size(); i++) {
            ResultFileStore file = resultFileStores.get(i);
            AuctionImage image = ResultFileStore.toEntity(file, auction, i);
            auctionImageRepository.save(image);

            imageResponses.add(
                    AuctionImageResponse.builder()
                            .originalName(image.getOriginalName())
                            .storeName(image.getStoreName())
                            .url(image.getUrl())
                            .imageSeq(image.getImageSeq())
                            .build());
        }
        notificationService.subscribe(auction.getId(), SubscriptionType.SELLER);

        return AuctionCreateResponse.builder()
                .title(auction.getTitle())
                .description(auction.getDescription())
                .basePrice(auction.getBasePrice())
                .itemCondition(auction.getItemCondition())
                .startDelay(request.getStartDelay())
                .durationTime(request.getDurationTime())
                .category(CategoryResponse.builder()
                        .id(category.getId())
                        .mainCategory(request.getCategory().getMainCategory())
                        .subCategory(request.getCategory().getSubCategory())
                        .detailCategory(request.getCategory().getDetailCategory()).build())
                .images(imageResponses)
                .build();
    }

    @Transactional(readOnly = true)
    public AuctionDetailResponse getAuctionDetail(Long auctionId) {
        User user = authService.getCurrentUser();

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NoSuchElementException("경매 상품이 존재하지 않습니다."));

        List<AuctionImage> images = auctionImageRepository.findByAuctionId(auctionId);

        List<AuctionImageResponse> imageResponses = images.stream()
                .map(AuctionImageResponse::from)
                .collect(Collectors.toList());

        AuctionImageResponse mainImage = imageResponses.stream()
                .filter(img -> img.getImageSeq() == 0).findFirst()
                .orElse(null);

        CategoryResponse categoryResponse = CategoryResponse.from(auction.getCategory());
        int scrapCount = scrapRepository.countByAuctionId(auctionId);
        String maskedEmail = maskEmail(auction.getSeller().getEmail());
        if (user != null) {
            boolean isScrap = scrapRepository.existsByUserIdAndAuctionId(user.getId(), auctionId);
            boolean isSeller = auction.getSeller().getId().equals(user.getId());

            return AuctionDetailResponse.builder()
                    .auctionId(auction.getId())
                    .categoryId(auction.getCategory().getId())
                    .title(auction.getTitle())
                    .description(auction.getDescription())
                    .sellerEmailMasked(maskedEmail)
                    .status(auction.getStatus())
                    .itemCondition(auction.getItemCondition())
                    .basePrice(auction.getBasePrice())
                    .isScrap(isScrap)
                    .scrapCount(scrapCount)
                    .isSeller(isSeller)
                    .category(categoryResponse)
                    .startTime(auction.getStartTime())
                    .endTime(auction.getEndTime())
                    .mainImage(mainImage)
                    .images(imageResponses)
                    .build();
        } else {
            return AuctionDetailResponse.builder()
                    .auctionId(auction.getId())
                    .categoryId(auction.getCategory().getId())
                    .title(auction.getTitle())
                    .description(auction.getDescription())
                    .sellerEmailMasked(maskedEmail)
                    .status(auction.getStatus())
                    .itemCondition(auction.getItemCondition())
                    .basePrice(auction.getBasePrice())
                    .isScrap(false)
                    .scrapCount(scrapCount)
                    .isSeller(false)
                    .category(categoryResponse)
                    .startTime(auction.getStartTime())
                    .endTime(auction.getEndTime())
                    .mainImage(mainImage)
                    .images(imageResponses)
                    .build();
        }

    }

    @Transactional(readOnly = true)
    public AuctionUpdatePageResponse getAuctionUpdate(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NoSuchElementException("경매 상품이 존재하지 않습니다."));

        List<AuctionImage> images = auctionImageRepository.findByAuctionId(auctionId);

        List<AuctionImageResponse> imageResponses = images.stream()
                .map(AuctionImageResponse::from)
                .collect(Collectors.toList());

        AuctionImageResponse mainImage = imageResponses.stream()
                .filter(img -> img.getImageSeq() == 0).findFirst()
                .orElse(null);

        CategoryResponse categoryResponse = CategoryResponse.from(auction.getCategory());

        return AuctionUpdatePageResponse.builder()
                .auctionId(auction.getId())
                .categoryId(auction.getCategory().getId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .status(auction.getStatus())
                .itemCondition(auction.getItemCondition())
                .basePrice(auction.getBasePrice())
                .category(categoryResponse)
                .mainImage(mainImage)
                .images(imageResponses)
                .build();
    }

    @Transactional
    public AuctionUpdateResponse updateAuction(Long auctionId, AuctionUpdateRequest request,
                                               List<MultipartFile> images) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NoSuchElementException("경매 상품이 존재하지 않습니다."));

        if (auction.getStartTime().minusMinutes(10).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("경매 시작 10분 전부터는 수정이 불가능합니다.");
        }

        if (request.getTitle() != null) {
            auction.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            auction.setDescription(request.getDescription());
        }
        if (request.getBasePrice() != null) {
            validateBasePrice(request.getBasePrice());
            auction.setBasePrice(request.getBasePrice());
        }
        if (request.getItemCondition() != null) {
            auction.setItemCondition(request.getItemCondition());
        }

        Category category = auction.getCategory();

        if (request.getCategory() != null && request.getCategory().getId() != null) {
            category = categoryRepository.findById(request.getCategory().getId())
                    .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다."));

            validateLeafCategory(category);
            auction.setCategory(category);
        }

        if (request.getStartDelay() == null || request.getDurationTime() == null) {
            throw new IllegalArgumentException("경매 시작 시간 및 경매 소요 시간은 필수입니다.");
        }

        validateAuctionTime(request.getStartDelay(), request.getDurationTime());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.plusMinutes(request.getStartDelay());
        LocalDateTime endTime = startTime.plusMinutes(request.getDurationTime());

        auction.setStartTime(startTime);
        auction.setEndTime(endTime);

        List<AuctionImage> existingImages = auctionImageRepository.findByAuctionId(auctionId);
        Map<Long, AuctionImage> existingImageMap = existingImages.stream()
                .collect(Collectors.toMap(AuctionImage::getId, Function.identity()));

        List<AuctionImageRequest> imageRequests = request.getImages() != null ? request.getImages() : List.of();

        boolean hasMainImage = imageRequests.stream()
                .anyMatch(img -> img.getImageSeq() != null && img.getImageSeq() == 0);

        if (!hasMainImage) {
            throw new IllegalArgumentException("대표 이미지를 반드시 포함해야 합니다. (imageSeq == 0)");
        }

        Set<Long> requestedIds = imageRequests.stream()
                .map(AuctionImageRequest::getImageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<AuctionImage> imagesToDelete = existingImages.stream()
                .filter(img -> !requestedIds.contains(img.getId()))
                .collect(Collectors.toList());

        for (AuctionImage image : imagesToDelete) {
            auctionImageRepository.delete(image);
            fileStore.delete(image.getStoreName());
        }

        List<ResultFileStore> storedFiles = fileStore.storeFiles(images);
        int newFileIndex = 0;

        List<AuctionImageResponse> imageResponses = new ArrayList<>();

        for (AuctionImageRequest imageRequest : imageRequests) {
            Long imageId = imageRequest.getImageId();
            Integer imageSeq = imageRequest.getImageSeq();

            if (imageId != null && existingImageMap.containsKey(imageId)) {
                AuctionImage existing = existingImageMap.get(imageId);
                existing.setImageSeq(imageSeq);
                imageResponses.add(AuctionImageResponse.from(existing));

            } else {
                ResultFileStore file = storedFiles.get(newFileIndex++);
                AuctionImage newImage = ResultFileStore.toEntity(file, auction, imageSeq);
                auctionImageRepository.save(newImage);
                imageResponses.add(AuctionImageResponse.from(newImage));
            }
        }

        CategoryResponse categoryResponse = (request.getCategory() != null)
                ? CategoryResponse.from(category, request.getCategory())
                : CategoryResponse.from(category);

        return AuctionUpdateResponse.builder()
                .auctionId(auction.getId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .basePrice(auction.getBasePrice())
                .itemCondition(auction.getItemCondition())
                .startDelay(request.getStartDelay())
                .durationTime(request.getDurationTime())
                .category(categoryResponse)
                .images(imageResponses)
                .build();
    }

    @Transactional
    public void deleteAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new EntityNotFoundException("경매 상품 찾을 수 없음."));

        List<AuctionImage> images = auctionImageRepository.findByAuctionId(auctionId);

        for (AuctionImage image : images) {
            fileStore.delete(image.getStoreName());
        }

        auctionImageRepository.deleteAll(images);
        auctionRepository.delete(auction);
        notificationService.unsubscribe(auctionId, SubscriptionType.SELLER);
    }

    @Transactional(readOnly = true)
    public List<RelatedAuctionResponse> getRelatedAuctions(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NoSuchElementException("경매 상품이 존재하지 않습니다."));

        User user = authService.getCurrentUser();

        Long detailCategoryId = auction.getCategory().getId();

        Long subCategoryId = auction.getCategory().getParent() != null
                ? auction.getCategory().getParent().getId()
                : null;

        List<Auction> detailCategoryList = auctionRepository.findByCategoryIdAndStatus(detailCategoryId,
                        AuctionStatus.active)
                .stream()
                .filter(a -> !a.getId().equals(auctionId))
                .limit(10)
                .collect(Collectors.toList());

        if (detailCategoryList.size() < 10 && subCategoryId != null) {
            List<Auction> subCategoryList = auctionRepository.findByParentCategoryIdAndStatus(subCategoryId,
                    AuctionStatus.active);

            Set<Long> existingIds = detailCategoryList.stream().map(Auction::getId).collect(Collectors.toSet());

            for (Auction a : subCategoryList) {
                if (!existingIds.contains(a.getId()) && !a.getId().equals(auctionId)) {
                    detailCategoryList.add(a);
                    if (detailCategoryList.size() == 10) {
                        break;
                    }
                }
            }
        }

        List<Long> auctionIds = detailCategoryList.stream().map(Auction::getId).toList();
        Map<Long, String> auctionIdToImageUrl = auctionImageRepository.findMainImagesByAuctionIds(auctionIds).stream()
                .collect(Collectors.toMap(a -> a.getAuction().getId(), AuctionImage::getUrl));

        return detailCategoryList.stream()
                .map(a -> RelatedAuctionResponse.of(
                        a,
                        auctionIdToImageUrl.get(a.getId()),
                        user != null ? user.getId() : null,
                        scrapRepository,
                        bidRepository))
                .collect(Collectors.toList());
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf("@");
        String domain = email.substring(atIndex);
        String localPart = email.substring(0, atIndex);

        String masked = "*".repeat(localPart.length() - 3);

        return localPart.substring(0, 3) + masked + domain;
    }

    private void validateLeafCategory(Category category) {
        if (!category.getChildren().isEmpty()) {
            throw new IllegalArgumentException("소분류 카테고리만 선택할 수 있습니다.");
        }
    }

    private void validateBasePrice(int basePrice) {
        if (basePrice < 0) {
            throw new IllegalArgumentException("시작가는 0원 이상이어야 합니다.");
        }
    }

    private void validateAuctionTime(int startDelay, int durationTime) {
        if (startDelay < 0 || startDelay > 1440) {
            throw new IllegalArgumentException("경매 오픈 시간은 현재 시각 이후부터 24시간 이내여야 합니다.");
        }
        if (durationTime < 10 || durationTime > 1440) {
            throw new IllegalArgumentException("경매 소요 시간은 10분 이상, 24시간(1440분) 이내여야 합니다.");
        }
    }


    public AuctionSearchResponseDto search(AuctionSearchRequestDto auctionSearchRequestDto) {
        List<Auction> auctionList = findAuctionList(auctionSearchRequestDto);
        Long auctionCount = (long) auctionList.size();

        log.info("{}개의 경매를 찾았습니다.", auctionList.size());

        if (auctionList.isEmpty()) {
            return new AuctionSearchResponseDto(auctionCount, new ArrayList<>());
        }

        auctionList.forEach(auction -> log.debug("찾은 경매 정보: {}", auction.toString()));

        List<Long> auctionIdList = getAuctionIdList(auctionList);
        Map<Long, String> thumbnailMap = getThumbnailMap(auctionIdList);
        Map<Long, List<Bid>> bidsByAuctionMap = getBidListByAuctionMap(auctionIdList);
        Map<Long, List<Scrap>> scrapsByAuctionMap = getScrapListByAuctionMap(auctionIdList);
        User user = authService.getCurrentUser();
        List<AuctionListItemDto> resultWithoutSort;
        if (user == null) {
            resultWithoutSort = auctionList.stream().map(auction -> {
                        Long auctionId = auction.getId();
                        String thumbnailUrl = thumbnailMap.get(auctionId);
                        List<Bid> bids = bidsByAuctionMap.get(auctionId);
                        List<Scrap> scraps = scrapsByAuctionMap.get(auctionId);
                        return AuctionListItemDto.builder().id(auction.getId())
                                .title(auction.getTitle())
                                .startTime(auction.getStartTime())
                                .endTime(auction.getEndTime())
                                .status(auction.getStatus())
                                .basePrice(auction.getBasePrice())
                                .thumbnailUrl(thumbnailUrl)
                                .bidderCount((long) (bids != null ? bids.size() : 0))
                                .currentPrice(bids != null ? bids.getFirst().getPrice() : auction.getBasePrice())
                                .scrapCount((long) (scraps != null ? scraps.size() : 0))
                                .isScrapped(false)
                                .build();
                    }
            ).toList();
        } else {
            Map<Long, Scrap> isScrappedMap = scrapRepository.findAllByUser_Id(user.getId()).stream()
                    .collect(Collectors.toMap(scrap -> scrap.getAuction().getId(), scrap -> scrap));
            resultWithoutSort = auctionList.stream().map(auction -> {
                        Long auctionId = auction.getId();
                        String thumbnailUrl = thumbnailMap.get(auctionId);
                        List<Bid> bids = bidsByAuctionMap.get(auctionId);
                        List<Scrap> scraps = scrapsByAuctionMap.get(auctionId);

                        return AuctionListItemDto.builder().id(auction.getId())
                                .title(auction.getTitle())
                                .startTime(auction.getStartTime())
                                .endTime(auction.getEndTime())
                                .status(auction.getStatus())
                                .basePrice(auction.getBasePrice())
                                .thumbnailUrl(thumbnailUrl)
                                .bidderCount((long) (bids != null ? bids.size() : 0))
                                .currentPrice(bids != null ? bids.getFirst().getPrice() : auction.getBasePrice())
                                .scrapCount((long) (scraps != null ? scraps.size() : 0))
                                .isScrapped(isScrappedMap.get(auctionId) != null)
                                .build();
                    }
            ).toList();
        }

        return new AuctionSearchResponseDto(auctionCount,
                this.sortAuctionListItemDtoList(resultWithoutSort, auctionSearchRequestDto.getSortBy()).stream()
                        .skip(auctionSearchRequestDto.getLimit() * (auctionSearchRequestDto.getPage() - 1L))
                        .limit(auctionSearchRequestDto.getLimit())
                        .toList());
    }

    private List<Auction> findAuctionList(AuctionSearchRequestDto auctionSearchRequestDto) {
        return auctionRepository.findAllWithDetails(
                auctionSearchRequestDto.toAuctionSearchParam());
    }

    private List<Long> getAuctionIdList(List<Auction> auctionList) {
        return auctionList.stream().map(Auction::getId).collect(Collectors.toList());
    }

    private Map<Long, String> getThumbnailMap(List<Long> auctionIList) {
        List<AuctionImage> thumbnailList = auctionImageRepository.findThumbnailsByAuctionIds(auctionIList);

        return thumbnailList.stream()
                .collect(Collectors.toMap(
                        image -> image.getAuction().getId(),
                        AuctionImage::getUrl,
                        (existing, replacement) -> existing
                ));
    }

    private Map<Long, List<Bid>> getBidListByAuctionMap(List<Long> auctionIdList) {
        List<Bid> bidList = bidRepository.findByAuctionIdList(auctionIdList);
        return bidList.stream()
                .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
    }

    private Map<Long, List<Scrap>> getScrapListByAuctionMap(List<Long> auctionIdList) {

        List<Scrap> scrapList = scrapRepository.findByAuctionIdList(auctionIdList);
        return scrapList.stream()
                .collect(Collectors.groupingBy(scrap -> scrap.getAuction().getId()));
    }

    private List<AuctionListItemDto> sortAuctionListItemDtoList(
            List<AuctionListItemDto> auctionSearchResponseDtoList, String sortBy
    ) {
        if (sortBy.equals("price_asc")) {
            return auctionSearchResponseDtoList.stream()
                    .sorted(Comparator.comparing(AuctionListItemDto::getBasePrice)).toList();
        } else if (sortBy.equals("price_desc")) {
            return auctionSearchResponseDtoList.stream()
                    .sorted(Comparator.comparing(AuctionListItemDto::getBasePrice).reversed()).toList();
        } else if (sortBy.equals("scrap_count_desc")) {
            return auctionSearchResponseDtoList.stream()
                    .sorted(Comparator.comparing(AuctionListItemDto::getScrapCount).reversed()).toList();
        } else if (sortBy.equals("bidder_count_desc")) {
            return auctionSearchResponseDtoList.stream()
                    .sorted(Comparator.comparing(AuctionListItemDto::getBidderCount).reversed()).toList();
        }

        return auctionSearchResponseDtoList;
    }
}
