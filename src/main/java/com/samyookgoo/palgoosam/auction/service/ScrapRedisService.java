package com.samyookgoo.palgoosam.auction.service;

import com.samyookgoo.palgoosam.user.repository.ScrapRepository;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScrapRedisService {

    private final ScrapRepository scrapRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private static final Integer TTL_DAYS = 3;

    public void incrementScrapCount(Long auctionId) {
        calculateAuctionScrapCount(auctionId, 1L);
    }

    public void decrementScrapCount(Long auctionId) {
        calculateAuctionScrapCount(auctionId, -1L);
    }

    public Long getOrCalculateScrapCount(Long auctionId) {
        Long cached = getAuctionScrapCount(auctionId);
        if (cached != null) {
            return cached;
        }

        Long actual = scrapRepository.countScrapCountByAuctionId(auctionId);

        warmUpScrapCount(auctionId, actual);
        return actual;
    }

    private Long getAuctionScrapCount(Long auctionId) {
        HashOperations<String, String, Long> hashOperations = redisTemplate.opsForHash();

        String key = "auction:" + auctionId;
        String field = "scrapCount";

        return hashOperations.get(key, field);
    }

    public void warmUpScrapCount(Long auctionId, Long scrapCount) {
        HashOperations<String, String, Long> hashOperations = redisTemplate.opsForHash();

        String key = "auction:" + auctionId;
        String field = "scrapCount";

        hashOperations.put(key, field, scrapCount);
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }

    private void calculateAuctionScrapCount(Long auctionId, Long delta) {
        HashOperations<String, String, Long> hashOperations = redisTemplate.opsForHash();

        String key = "auction:" + auctionId;
        String field = "scrapCount";

        hashOperations.increment(key, field, delta);
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }
}
