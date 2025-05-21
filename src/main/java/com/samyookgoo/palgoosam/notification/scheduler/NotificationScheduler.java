package com.samyookgoo.palgoosam.notification.scheduler;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.domain.AuctionImage;
import com.samyookgoo.palgoosam.auction.repository.AuctionImageRepository;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.notification.constant.NotificationTemplates;
import com.samyookgoo.palgoosam.notification.dto.NotificationRequestDto;
import com.samyookgoo.palgoosam.notification.service.NotificationService;
import com.samyookgoo.palgoosam.notification.subscription.constant.SubscriptionType;
import com.samyookgoo.palgoosam.notification.subscription.domain.AuctionSubscription;
import com.samyookgoo.palgoosam.notification.subscription.repository.AuctionSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {
    private final NotificationService notificationService;
    private final AuctionRepository auctionRepository;
    private final AuctionSubscriptionRepository auctionSubscriptionRepository;
    private final AuctionImageRepository auctionImageRepository;

    @Scheduled(fixedRate = 60000)
    @Async
    public void checkAuctionStart() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteAgo = now.minusMinutes(1);
        List<Auction> auctionList = auctionRepository.findByStartTimeInOneMinute(now, oneMinuteAgo);
        auctionList.forEach(auction -> {
            List<AuctionSubscription> subscriberList = auctionSubscriptionRepository.findAllByAuction(auction);
            List<Long> scrapperIdList = subscriberList.stream().filter(auctionSubscription ->
                    auctionSubscription.getType().equals(SubscriptionType.SCRAPPER)
            ).map(auctionSubscription -> auctionSubscription.getUser().getId()).toList();
            AuctionImage image = auctionImageRepository.findMainImageByAuctionId(auction.getId()).orElse(null);

            NotificationRequestDto bidderRequestDto = NotificationRequestDto.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .message(NotificationTemplates.getAuctionStartingTemplate(auction.getTitle()))
                    .subscriptionType(SubscriptionType.SCRAPPER)
                    .imageUrl(image != null ? image.getUrl() : null).build();
            notificationService.sendTopicMessage(bidderRequestDto, scrapperIdList);

            List<Long> sellerIdList = subscriberList.stream().filter(auctionSubscription ->
                    auctionSubscription.getType().equals(SubscriptionType.SELLER)
            ).map(auctionSubscription -> auctionSubscription.getUser().getId()).toList();
            NotificationRequestDto sellerRequestDto = NotificationRequestDto.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .message(NotificationTemplates.getAuctionStartingTemplate(auction.getTitle()))
                    .subscriptionType(SubscriptionType.SELLER)
                    .imageUrl(image != null ? image.getUrl() : null).build();
            notificationService.sendTopicMessage(sellerRequestDto, sellerIdList);
        });
    }

    @Scheduled(fixedRate = 60000)
    @Async
    public void checkAuctionCompletion() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteAgo = now.minusMinutes(1);
        List<Auction> auctionList = auctionRepository.findByEndTimeInOneMinute(now, oneMinuteAgo);
        auctionList.forEach(auction -> {
            List<AuctionSubscription> subscriberList = auctionSubscriptionRepository.findAllByAuction(auction);
            AuctionImage image = auctionImageRepository.findMainImageByAuctionId(auction.getId()).orElse(null);

            List<Long> bidderIdList = subscriberList.stream().filter(auctionSubscription ->
                    auctionSubscription.getType().equals(SubscriptionType.BIDDER)
            ).map(auctionSubscription -> auctionSubscription.getUser().getId()).toList();
            NotificationRequestDto bidderRequestDto = NotificationRequestDto.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .message(NotificationTemplates.getAuctionEndedTemplate(auction.getTitle()))
                    .subscriptionType(SubscriptionType.BIDDER)
                    .imageUrl(image != null ? image.getUrl() : null).build();
            notificationService.sendTopicMessage(bidderRequestDto, bidderIdList);

            List<Long> sellerList = subscriberList.stream().filter(auctionSubscription ->
                    auctionSubscription.getType().equals(SubscriptionType.SELLER)
            ).map(auctionSubscription -> auctionSubscription.getUser().getId()).toList();
            NotificationRequestDto sellerRequestDto = NotificationRequestDto.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .message(NotificationTemplates.getAuctionEndedTemplate(auction.getTitle()))
                    .subscriptionType(SubscriptionType.BIDDER)
                    .imageUrl(image != null ? image.getUrl() : null).build();
            notificationService.sendTopicMessage(sellerRequestDto, sellerList);

            List<Long> scrapperList = subscriberList.stream().filter(auctionSubscription ->
                    auctionSubscription.getType().equals(SubscriptionType.SCRAPPER)
            ).map(auctionSubscription -> auctionSubscription.getUser().getId()).toList();
            NotificationRequestDto scrapperRequestDto = NotificationRequestDto.builder()
                    .auctionId(auction.getId())
                    .title(auction.getTitle())
                    .message(NotificationTemplates.getAuctionEndedTemplate(auction.getTitle()))
                    .subscriptionType(SubscriptionType.SCRAPPER)
                    .imageUrl(image != null ? image.getUrl() : null).build();
            notificationService.sendTopicMessage(scrapperRequestDto, scrapperList);
        });
    }

}
