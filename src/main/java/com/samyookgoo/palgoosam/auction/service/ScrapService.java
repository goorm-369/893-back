package com.samyookgoo.palgoosam.auction.service;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.repository.AuctionRepository;
import com.samyookgoo.palgoosam.auth.service.AuthService;
import com.samyookgoo.palgoosam.user.domain.Scrap;
import com.samyookgoo.palgoosam.user.domain.User;
import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ScrapService {

    private final AuctionRepository auctionRepository;
    private final ScrapRepository scrapRepository;
    private final AuthService authService;

    @Transactional
    public boolean addScrap(Long auctionId) {
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UsernameNotFoundException("유저를 찾을 수 없습니다.");
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new EntityNotFoundException("경매 상품 없음"));

        if (scrapRepository.existsByUserAndAuction(user, auction)) {
            return false;
        }

        Scrap scrap = new Scrap();
        scrap.setUser(user);
        scrap.setAuction(auction);
        scrapRepository.save(scrap);
        return true;
    }

    @Transactional
    public boolean removeScrap(Long auctionId) {
        User user = authService.getCurrentUser();
        if (user == null) {
            throw new UsernameNotFoundException("유저를 찾을 수 없습니다.");
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new EntityNotFoundException("경매 상품 없음"));

        Optional<Scrap> optionalScrap = scrapRepository.findByUserAndAuction(user, auction);

        if (optionalScrap.isEmpty()) {
            return false;
        }
        scrapRepository.delete(optionalScrap.get());
        return true;
    }

}
