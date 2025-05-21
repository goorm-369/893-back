package com.samyookgoo.palgoosam.notification.constant;

public class NotificationTemplates {

    public static String getAuctionStartingTemplate(String auctionTitle) {
        return String.format("곧 경매 '%s'가 시작합니다! 지금 참여하세요.", auctionTitle);
    }

    public static String getNewBidTemplate(String auctionTitle, int bidAmount) {
        return String.format("경매 '%s'에 새로운 입찰가 %d원이 제시되었습니다.", auctionTitle, bidAmount);
    }

    public static String getAuctionEndedTemplate(String auctionTitle) {
        return String.format("경매 '%s'가 종료되었습니다.", auctionTitle);
    }

    public static String getAuctionWonTemplate(String auctionTitle) {
        return String.format("축하합니다! '%s' 경매에서 낙찰되었습니다.", auctionTitle);
    }
}
