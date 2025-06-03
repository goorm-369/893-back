package com.samyookgoo.palgoosam.auction.service;

import com.samyookgoo.palgoosam.auction.constant.AuctionStatus;
import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.domain.AuctionImage;
import com.samyookgoo.palgoosam.auction.domain.AuctionSearchProjection;
import com.samyookgoo.palgoosam.auction.domain.Category;
import com.samyookgoo.palgoosam.auction.dto.AuctionSearchResultDto;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionCreateRequest;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionImageRequest;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionSearchRequestDto;
import com.samyookgoo.palgoosam.auction.dto.request.AuctionUpdateRequest;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionCreateResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionDetailResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionImageResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionSearchResponseDto;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionUpdatePageResponse;
import com.samyookgoo.palgoosam.auction.dto.response.AuctionUpdateResponse;
import com.samyookgoo.palgoosam.auction.dto.response.CategoryResponse;
import com.samyookgoo.palgoosam.auction.dto.response.RelatedAuctionResponse;
import com.samyookgoo.palgoosam.auction.file.FileStore;
import com.samyookgoo.palgoosam.auction.file.ResultFileStore;
import com.samyookgoo.palgoosam.auction.repository.AuctionImageRepository;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.auction.repository.AuctionSearchRepository;
import com.samyookgoo.palgoosam.auction.repository.CategoryRepository;
import com.samyookgoo.palgoosam.auction.service.dto.AuctionSearchDto;
import com.samyookgoo.palgoosam.auth.service.AuthService;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.payment.constant.PaymentStatus;
import com.samyookgoo.palgoosam.payment.domain.Payment;
import com.samyookgoo.palgoosam.payment.repository.PaymentRepository;
import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    private final PaymentRepository paymentRepository;
    private final AuctionSearchRepository auctionSearchRepository;

    @Transactional
    public AuctionCreateResponse createAuction(AuctionCreateRequest request, List<ResultFileStore> resultFileStores) {
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UsernameNotFoundException("유저를 찾을 수 없습니다.");
        }

        Category category = getValidatedCategory(request.getCategory().getId());

        validateLeafCategory(category);
        validateAuctionTime(request.getStartDelay(), request.getDurationTime());
        validateBasePrice(request.getBasePrice());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.plusMinutes(request.getStartDelay());
        LocalDateTime endTime = startTime.plusMinutes(request.getDurationTime());

        Auction auction = Auction.from(request, category, user, startTime, endTime);
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

        return AuctionCreateResponse.builder()
                .auctionId(auction.getId())
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
        log.info("현재 로그인 유저: {}", user != null ? user.getEmail() : "null");

        Auction auction = getValidatedAuction(auctionId);

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

        Optional<Bid> winningBid = bidRepository.findByAuctionIdAndIsWinningTrue(auctionId);

        if (user != null) {
            boolean isScraped = scrapRepository.existsByUserIdAndAuctionId(user.getId(), auctionId);
            boolean isSeller = auction.getSeller().getId().equals(user.getId());
            boolean hasBeenPaid = paymentRepository.existsByAuction_IdAndStatus(auctionId, PaymentStatus.PAID);
            boolean isCurrentUserWinner = winningBid.map(bid -> bid.getBidder().getId().equals(user.getId()))
                    .orElse(false);

            return AuctionDetailResponse.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .description(auction.getDescription())
                    .sellerEmailMasked(maskedEmail)
                    .status(auction.getStatus())
                    .itemCondition(auction.getItemCondition())
                    .basePrice(auction.getBasePrice())
                    .isScraped(isScraped)
                    .scrapCount(scrapCount)
                    .isSeller(isSeller)
                    .category(categoryResponse)
                    .startTime(auction.getStartTime())
                    .endTime(auction.getEndTime())
                    .mainImage(mainImage)
                    .images(imageResponses)
                    .isCurrentUserBuyer(isCurrentUserWinner)
                    .hasBeenPaid(hasBeenPaid)
                    .build();
        } else {
            return AuctionDetailResponse.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .description(auction.getDescription())
                    .sellerEmailMasked(maskedEmail)
                    .status(auction.getStatus())
                    .itemCondition(auction.getItemCondition())
                    .basePrice(auction.getBasePrice())
                    .isScraped(false)
                    .scrapCount(scrapCount)
                    .isSeller(false)
                    .category(categoryResponse)
                    .startTime(auction.getStartTime())
                    .endTime(auction.getEndTime())
                    .mainImage(mainImage)
                    .images(imageResponses)
                    .isCurrentUserBuyer(false)
                    .hasBeenPaid(false)
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public AuctionUpdatePageResponse getAuctionUpdate(Long auctionId) {
        Auction auction = getValidatedAuction(auctionId);

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
        Auction auction = getValidatedAuction(auctionId);
        User user = authService.getCurrentUser();

        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }

        if (!auction.getSeller().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 경매만 삭제할 수 있습니다.");
        }

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
            category = getValidatedCategory(request.getCategory().getId());

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
        Auction auction = getValidatedAuction(auctionId);
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }

        if (!auction.getSeller().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 경매만 삭제할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        if (auction.getStartTime().minusMinutes(10).isAfter(now)) {
            softDeleteAuction(auctionId, auction);
            return;
        }

        if (auction.getStatus() == AuctionStatus.completed) {
            List<Bid> bids = bidRepository.findByAuctionId(auctionId);

            boolean allCancelled = bids.stream().allMatch(Bid::getIsDeleted);
            boolean hasValidBids = bids.stream().anyMatch(b -> !b.getIsDeleted());

            if (!hasValidBids || allCancelled) {
                softDeleteAuction(auctionId, auction);
                return;
            }

            boolean hasWinningBid = bidRepository.existsByAuctionIdAndIsWinningTrue(auctionId);
            if (!hasWinningBid) {
                throw new IllegalStateException("낙찰 정보가 존재하지 않아 삭제할 수 없습니다.");
            }

            Payment payment = paymentRepository.findByAuctionId(auctionId)
                    .orElseThrow(() -> new IllegalStateException("결제 정보가 존재하지 않아 삭제할 수 없습니다."));

            if (payment.getStatus() != PaymentStatus.PAID) {
                throw new IllegalStateException("낙찰자의 결제가 완료되지 않아 삭제할 수 없습니다.");
            }

            softDeleteAuction(auctionId, auction);
            return;
        }

        throw new IllegalStateException("경매 시작 10분 전이 지나거나, 경매 중에는 삭제할 수 없습니다.");
    }

    private void softDeleteAuction(Long auctionId, Auction auction) {
        // 대표이미지만 논리 삭제
        softDeleteAuctionImages(auctionId);

        // 논리 삭제
        auction.setStatus(AuctionStatus.deleted);
        auction.setIsDeleted(true);
        auctionRepository.save(auction);
    }

    private void softDeleteAuctionImages(Long auctionId) {
        // 이미지 처리
        List<AuctionImage> images = auctionImageRepository.findByAuctionId(auctionId);

        for (AuctionImage image : images) {
            if (image.getImageSeq() != null && image.getImageSeq() == 0) {
                // 대표 이미지는 soft delete
                image.setIsDeleted(true);
                auctionImageRepository.save(image);
            } else {
                // 나머지 이미지는 물리 삭제
                fileStore.delete(image.getStoreName());
                auctionImageRepository.delete(image);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RelatedAuctionResponse> getRelatedAuctions(Long auctionId) {
        Auction auction = getValidatedAuction(auctionId);

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

    private Auction getValidatedAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new NoSuchElementException("경매 상품이 존재하지 않습니다."));
    }

    private Category getValidatedCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다."));
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

    private void validateBasePrice(Integer basePrice) {
        if (basePrice == null || basePrice < 0) {
            throw new IllegalArgumentException("시작가는 0원 이상이어야 합니다.");
        }
        if (basePrice > 100_000_000) {
            throw new IllegalArgumentException("시작가는 초대 1억 원까지만 입력 가능합니다.");
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
        AuctionSearchDto auctionSearchDto = auctionSearchRequestDto.toAuctionSearchDto();
        List<AuctionSearchProjection> auctionList = auctionSearchRepository.search(auctionSearchDto);
        Long auctionCount = auctionSearchRepository.countAuctionsInList(auctionSearchDto);

        log.info("{}개의 경매를 찾았습니다.", auctionList.size());

        if (auctionList.isEmpty()) {
            return new AuctionSearchResponseDto(auctionCount, new ArrayList<>());
        }

        User user = authService.getCurrentUser();
        List<AuctionSearchResultDto> resultDtoList = auctionList.stream().map(auction ->
                AuctionSearchResultDto.builder().id(auction.getId())
                        .title(auction.getTitle())
                        .startTime(auction.getStartTime())
                        .endTime(auction.getEndTime())
                        .status(AuctionStatus.valueOf(auction.getStatus()))
                        .basePrice(auction.getBasePrice())
                        .thumbnailUrl(auction.getThumbnailUrl())
                        .bidderCount(auction.getBidderCount())
                        .currentPrice(auction.getCurrentPrice())
                        .scrapCount(auction.getScrapCount())
                        .isScraped(user != null && scrapRepository.existsByUserIdAndAuctionId(user.getId(),
                                auction.getId()))
                        .build()
        ).toList();
        return new AuctionSearchResponseDto(auctionCount, resultDtoList);
    }
}
