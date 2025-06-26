package com.samyookgoo.palgoosam.bid.service;

import com.samyookgoo.palgoosam.bid.repository.BidRepository;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BidRedisService {
    private final BidRepository bidRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private static final int TTL_DAYS = 3;

    public void incrementBidderCount(Long auctionId, Long userId) {
        calculateBidderCount(auctionId, userId, 1L);
    }

    public void decrementBidderCount(Long auctionId, Long userId) {
        calculateBidderCount(auctionId, userId, -1L);
    }

    public Long getOrCalculateBidderCount(Long auctionId) {

        Long cached = getAuctionBidderCount(auctionId);
        if (cached != null) {
            return cached;
        }

        Long actual = bidRepository.countBidderByAuctionId(auctionId);

        warmUpBidderCount(auctionId, actual);

        return actual;
    }

    public void warmUpBidderCount(Long auctionId, Long bidderCount) {
        HashOperations<String, String, Long> hashOperations = redisTemplate.opsForHash();

        String key = "auction:" + auctionId;
        String field = "bidderCount";

        hashOperations.put(key, field, bidderCount);
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }

    private Long getAuctionBidderCount(Long auctionId) {
        HashOperations<String, String, Long> hashOperations = redisTemplate.opsForHash();

        String key = "auction:" + auctionId;
        String field = "bidderCount";

        return hashOperations.get(key, field);
    }

    private void calculateBidderCount(Long auctionId, Long userId, Long delta) {
        HashOperations<String, String, Long> hashOperations = redisTemplate.opsForHash();

        String key = "auction:" + auctionId;
        String userField = "user:" + userId;
        String totalField = "bidderCount";

        Long userBidCount = hashOperations.increment(key, userField, delta);

        if (delta > 0 && userBidCount == 1) {
            hashOperations.increment(key, totalField, 1);
        } else if (delta < 0 && userBidCount <= 0) {
            hashOperations.delete(key, userField);
            hashOperations.increment(key, totalField, -1);
        }

        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }

}
