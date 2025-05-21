package com.samyookgoo.palgoosam.notification.subscription.repository;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.notification.subscription.domain.AuctionSubscription;
import com.samyookgoo.palgoosam.user.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionSubscriptionRepository extends JpaRepository<AuctionSubscription, Long> {
    List<AuctionSubscription> findAllByUserAndAuction(User user, Auction auction);

    List<AuctionSubscription> findAllByUser(User user);

    List<AuctionSubscription> findAllByAuction(Auction auction);
}
