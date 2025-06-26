package com.samyookgoo.palgoosam.auction.service;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.auth.service.AuthService;
import com.samyookgoo.palgoosam.user.domain.Scrap;
import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ScrapService {

    private final AuctionRepository auctionRepository;
    private final ScrapRepository scrapRepository;
    private final AuthService authService;
    private final ScrapRedisService scrapRedisService;

    @Transactional
    public void addScrap(Long auctionId) {
        User user = getCurrentUser();
        Auction auction = getAuction(auctionId);

        if (scrapRepository.existsByUserAndAuction(user, auction)) {
            throw new IllegalArgumentException("이미 스크랩된 상품입니다.");
        }

        Scrap scrap = Scrap.of(user, auction);
        scrapRepository.save(scrap);
        scrapRedisService.incrementScrapCount(auctionId);

        log.info("사용자 {}가 경매 상품 {}을 스크랩했습니다.", user.getId(), auction.getId());
    }

    @Transactional
    public void removeScrap(Long auctionId) {
        User user = getCurrentUser();
        Auction auction = getAuction(auctionId);

        Scrap scrap = scrapRepository.findByUserAndAuction(user, auction)
                .orElseThrow(() -> new IllegalStateException("스크랩된 상태가 아닙니다"
                        + "."));

        scrapRepository.delete(scrap);
        scrapRedisService.decrementScrapCount(auctionId);

        log.info("사용자 {}가 경매 상품 {}의 스크랩을 취소했습니다.", user.getId(), auction.getId());
    }

    private User getCurrentUser() {
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private Auction getAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new EntityNotFoundException("경매 상품을 찾을 수 없습니다."));
    }
}
