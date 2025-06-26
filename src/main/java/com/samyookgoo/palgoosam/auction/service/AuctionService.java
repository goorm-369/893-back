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
import com.samyookgoo.palgoosam.auction.exception.AuctionCategoryException;
import com.samyookgoo.palgoosam.auction.exception.AuctionForbiddenException;
import com.samyookgoo.palgoosam.auction.exception.AuctionImageException;
import com.samyookgoo.palgoosam.auction.exception.AuctionInvalidStateException;
import com.samyookgoo.palgoosam.auction.exception.AuctionNotFoundException;
import com.samyookgoo.palgoosam.auction.exception.AuctionUpdateLockedException;
import com.samyookgoo.palgoosam.auction.exception.CategoryNotFoundException;
import com.samyookgoo.palgoosam.auction.repository.AuctionImageRepository;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.auction.repository.AuctionSearchRepository;
import com.samyookgoo.palgoosam.auction.repository.CategoryRepository;
import com.samyookgoo.palgoosam.auction.service.dto.AuctionSearchDto;
import com.samyookgoo.palgoosam.auth.service.AuthService;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.exception.BidNotFoundException;
import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import com.samyookgoo.palgoosam.bid.service.BidRedisService;
import com.samyookgoo.palgoosam.common.s3.S3Service;
import com.samyookgoo.palgoosam.global.exception.ErrorCode;
import com.samyookgoo.palgoosam.payment.constant.PaymentStatus;
import com.samyookgoo.palgoosam.payment.domain.Payment;
import com.samyookgoo.palgoosam.payment.exception.PaymentNotFoundException;
import com.samyookgoo.palgoosam.payment.repository.PaymentRepository;
import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.exception.UserNotFoundException;
import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuctionService {

    private final AuctionImageRepository auctionImageRepository;
    private final AuctionRepository auctionRepository;
    private final CategoryRepository categoryRepository;
    private final ScrapRepository scrapRepository;
    private final BidRepository bidRepository;
    private final AuthService authService;
    private final PaymentRepository paymentRepository;
    private final AuctionSearchRepository auctionSearchRepository;
    private final S3Service s3Service;
    private final ScrapRedisService scrapRedisService;
    private final BidRedisService bidRedisService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Transactional
    public AuctionCreateResponse createAuction(AuctionCreateRequest request) {
        User user = getValidatedCurrentUser();

        Category category = getValidatedCategory(request.getCategory().getId());

        validateLeafCategory(category);
        validateAuctionTime(request.getStartDelay(), request.getDurationTime());
        validateBasePrice(request.getBasePrice());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.plusMinutes(request.getStartDelay());
        LocalDateTime endTime = startTime.plusMinutes(request.getDurationTime());

        Auction auction = Auction.from(request, category, user, startTime, endTime);
        auctionRepository.save(auction);

        List<AuctionImageResponse> imageResponses = saveAuctionImages(request.getImages(), auction);

        return AuctionCreateResponse.of(auction, imageResponses, category,
                request.getStartDelay(), request.getDurationTime());
    }

    @Transactional(readOnly = true)
    public AuctionDetailResponse getAuctionDetail(Long auctionId) {
        User user = authService.getCurrentUser();

        Auction auction = getValidatedAuction(auctionId);

        List<AuctionImage> images = auctionImageRepository.findByAuctionId(auctionId);
        List<AuctionImageResponse> imageResponses = images.stream()
                .map(image -> {
                    try {
                        return AuctionImageResponse.from(image.getId(), image.getStoreName(), image.getUrl(),
                                image.getImageSeq());
                    } catch (Exception e) {
                        log.warn("Presigned URL 생성 실패: {}", image.getStoreName(), e);
                        return AuctionImageResponse.from(image.getId(), image.getStoreName(), null,
                                image.getImageSeq()); // or 빈 이미지 URL
                    }
                })
                .collect(Collectors.toList());

        AuctionImageResponse mainImage = getMainImage(imageResponses);

        CategoryResponse categoryResponse = CategoryResponse.from(auction.getCategory());
        int scrapCount = scrapRepository.countByAuctionId(auctionId);
        String maskedEmail = maskEmail(auction.getSeller().getEmail());

        Optional<Bid> winningBid = bidRepository.findByAuctionIdAndIsWinningTrue(auctionId);

        AuctionDetailResponse.AuctionDetailResponseBuilder builder = AuctionDetailResponse.builder()
                .auctionId(auction.getId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .sellerEmailMasked(maskedEmail)
                .status(auction.getStatus())
                .itemCondition(auction.getItemCondition())
                .basePrice(auction.getBasePrice())
                .scrapCount(scrapCount)
                .category(categoryResponse)
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .mainImage(mainImage)
                .images(imageResponses);

        if (user != null) {
            boolean isScraped = scrapRepository.existsByUserIdAndAuctionId(user.getId(), auctionId);
            boolean isSeller = auction.getSeller().getId().equals(user.getId());
            boolean hasBeenPaid = paymentRepository.existsByAuction_IdAndStatus(auctionId, PaymentStatus.PAID);
            boolean isCurrentUserWinner = winningBid
                    .map(bid -> bid.getBidder().getId().equals(user.getId()))
                    .orElse(false);

            builder.isScraped(isScraped)
                    .isSeller(isSeller)
                    .isCurrentUserBuyer(isCurrentUserWinner)
                    .hasBeenPaid(hasBeenPaid);
        } else {
            builder.isScraped(false)
                    .isSeller(false)
                    .isCurrentUserBuyer(false)
                    .hasBeenPaid(false);
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public AuctionUpdatePageResponse getAuctionUpdate(Long auctionId) {
        Auction auction = getValidatedAuction(auctionId);

        List<AuctionImage> images = auctionImageRepository.findByAuctionId(auctionId);

        List<AuctionImageResponse> imageResponses = images.stream()
                .map(image -> {
                    return AuctionImageResponse.from(image.getId(), image.getStoreName(), image.getUrl(),
                            image.getImageSeq());
                })
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
    public AuctionUpdateResponse updateAuction(Long auctionId, AuctionUpdateRequest request) {
        Auction auction = getValidatedAuction(auctionId);
        User user = getValidatedCurrentUser();

        if (user == null) {
            throw new UserNotFoundException();
        }

        if (!auction.getSeller().getId().equals(user.getId())) {
            throw new AuctionForbiddenException(ErrorCode.AUCTION_UPDATE_FORBIDDEN);
        }

        if (auction.getStartTime().minusMinutes(10).isBefore(LocalDateTime.now())) {
            throw new AuctionUpdateLockedException();
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
            throw new AuctionImageException(ErrorCode.AUCTION_MAIN_IMAGE_REQUIRED);
        }

        Set<Long> requestedIds = imageRequests.stream()
                .map(AuctionImageRequest::getImageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<AuctionImageResponse> imageResponses = new ArrayList<>();

        List<AuctionImage> imagesToDelete = existingImages.stream()
                .filter(img -> !requestedIds.contains(img.getId()))
                .collect(Collectors.toList());

        for (AuctionImage image : imagesToDelete) {
            s3Service.deleteObject(image.getStoreName());
            auctionImageRepository.delete(image);
        }

        for (AuctionImageRequest imageRequest : imageRequests) {
            Long imageId = imageRequest.getImageId();
            Integer imageSeq = imageRequest.getImageSeq();
            String storeName = imageRequest.getStoreName();

            if (imageId != null && existingImageMap.containsKey(imageId)) {
                AuctionImage existing = existingImageMap.get(imageId);
                existing.setImageSeq(imageSeq);

                String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + existing.getStoreName();

                imageResponses.add(AuctionImageResponse.from(
                        imageId,
                        storeName,
                        publicUrl,
                        imageSeq
                ));

            } else if (imageId == null && storeName != null) {
                String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + storeName;

                AuctionImage newImage = AuctionImage.builder()
                        .auction(auction)
                        .storeName(storeName)
                        .originalName(imageRequest.getOriginalName())
                        .url(publicUrl)
                        .imageSeq(imageSeq)
                        .build();
                auctionImageRepository.save(newImage);

                imageResponses.add(AuctionImageResponse.from(imageId, storeName, publicUrl, imageSeq));

            } else {
                throw new IllegalArgumentException("imageId와 storeName 중 하나는 반드시 있어야 합니다.");
            }
        }

        CategoryResponse categoryResponse = (request.getCategory() != null)
                ? CategoryResponse.from(request.getCategory())
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
        User user = getValidatedCurrentUser();

        if (!auction.getSeller().getId().equals(user.getId())) {
            throw new AuctionForbiddenException(ErrorCode.AUCTION_DELETE_FORBIDDEN);
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
                throw new BidNotFoundException(ErrorCode.WINNING_BID_NOT_FOUND);
            }

            Payment payment = paymentRepository.findByAuctionId(auctionId)
                    .orElseThrow(PaymentNotFoundException::new);

            if (payment.getStatus() != PaymentStatus.PAID) {
                throw new PaymentNotFoundException(ErrorCode.PAYMENT_NOT_COMPLETED);
            }

            softDeleteAuction(auctionId, auction);
            return;
        }

        throw new AuctionForbiddenException(ErrorCode.AUCTION_DELETE_AFTER_START_FORBIDDEN);
    }

    @Transactional(readOnly = true)
    public List<RelatedAuctionResponse> getRelatedAuctions(Long auctionId) {
        Auction auction = getValidatedAuction(auctionId);

        User user = authService.getCurrentUser();

        Category category = auction.getCategory();

        Long subCategoryId = category.getParent() != null ? category.getParent().getId() : null;

        List<Auction> detailCategoryList = auctionRepository.findByCategoryIdAndStatus(category.getId(),
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

    private User getValidatedCurrentUser() {
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UserNotFoundException();
        }
        return user;
    }

    private Auction getValidatedAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(AuctionNotFoundException::new);
    }

    private Category getValidatedCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);
    }

    private void validateLeafCategory(Category category) {
        if (!category.getChildren().isEmpty()) {
            throw new AuctionCategoryException(ErrorCode.AUCTION_CATEGORY_NOT_LEAF);
        }
    }

    private void validateBasePrice(Integer basePrice) {
        if (basePrice == null || basePrice < 0) {
            throw new AuctionInvalidStateException(ErrorCode.AUCTION_INVALID_MIN_BASE_PRICE);
        }
        if (basePrice > 100_000_000) {
            throw new AuctionInvalidStateException(ErrorCode.AUCTION_INVALID_MAX_BASE_PRICE);
        }
    }

    private void validateAuctionTime(int startDelay, int durationTime) {
        if (startDelay < 0 || startDelay > 1440) {
            throw new AuctionInvalidStateException(ErrorCode.AUCTION_OPEN_TIME_INVALID);
        }
        if (durationTime < 10 || durationTime > 1440) {
            throw new AuctionInvalidStateException(ErrorCode.AUCTION_DURATION_INVALID);
        }
    }

    private AuctionImageResponse getMainImage(List<AuctionImageResponse> images) {
        return images.stream()
                .filter(img -> img.getImageSeq() == 0)
                .findFirst()
                .orElse(null);
    }

    private List<AuctionImageResponse> saveAuctionImages(List<AuctionImageRequest> images, Auction auction) {
        if (images == null || images.isEmpty()) {
            throw new AuctionImageException(ErrorCode.AUCTION_MAIN_IMAGE_REQUIRED);
        }

        return IntStream.range(0, images.size())
                .mapToObj(i -> {
                    AuctionImageRequest info = images.get(i);
                    return saveSingleAuctionImage(info.getStoreName(), info.getOriginalName(), auction, i);
                })
                .toList();
    }

    private AuctionImageResponse saveSingleAuctionImage(String imageKey, String originalName, Auction auction,
                                                        int order) {
        try {
            String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + imageKey;

            AuctionImage image = AuctionImage.builder()
                    .auction(auction)
                    .storeName(imageKey)
                    .originalName(originalName)
                    .imageSeq(order)
                    .url(publicUrl)
                    .build();

            auctionImageRepository.save(image);

            return AuctionImageResponse.from(
                    image.getId(),
                    image.getStoreName(),
                    publicUrl,
                    image.getImageSeq());
        } catch (Exception e) {
            log.error("이미지 저장 실패: {}", e.getMessage(), e);
            throw new AuctionImageException(ErrorCode.AUCTION_IMAGE_SAVE_FAILED);
        }
    }

    private void softDeleteAuction(Long auctionId, Auction auction) {
        softDeleteAuctionImages(auctionId);

        auction.setStatus(AuctionStatus.deleted);
        auction.setIsDeleted(true);
        auctionRepository.save(auction);
    }

    private void softDeleteAuctionImages(Long auctionId) {
        List<AuctionImage> images = auctionImageRepository.findByAuctionIdAndIsDeletedFalse(auctionId);

        images.forEach(image -> {
            try {
                if (image.getImageSeq() == 0) {
                    image.setIsDeleted(true);
                    auctionImageRepository.save(image);
                } else {
                    s3Service.deleteObject(image.getStoreName());
                    auctionImageRepository.delete(image);
                }
            } catch (Exception e) {
                throw new AuctionImageException(ErrorCode.AUCTION_IMAGE_DELETE_FAILED);
            }
        });
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf("@");
        String domain = email.substring(atIndex);
        String localPart = email.substring(0, atIndex);

        String masked = "*".repeat(localPart.length() - 3);

        return localPart.substring(0, 3) + masked + domain;
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
                        .bidderCount(bidRedisService.getOrCalculateBidderCount(auction.getId()))
                        .currentPrice(auction.getCurrentPrice())
                        .scrapCount(scrapRedisService.getOrCalculateScrapCount(auction.getId()))
                        .isScraped(user != null && scrapRepository.existsByUserIdAndAuctionId(user.getId(),
                                auction.getId()))
                        .build()
        ).toList();
        return new AuctionSearchResponseDto(auctionCount, resultDtoList);
    }
}
