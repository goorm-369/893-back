package com.samyookgoo.palgoosam.bid.repository;

import com.samyookgoo.palgoosam.auction.projection.AuctionBidCount;
import com.samyookgoo.palgoosam.auction.projection.TopWinningBid;
import com.samyookgoo.palgoosam.bid.domain.Bid;
import com.samyookgoo.palgoosam.bid.domain.BidForHighestPriceProjection;
import com.samyookgoo.palgoosam.bid.domain.BidForMyPageProjection;
import com.samyookgoo.palgoosam.bid.service.response.BidStatsResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByAuctionIdOrderByCreatedAtDesc(Long auctionId);

    @Query("SELECT MAX(b.price) FROM Bid b WHERE b.auction.id = :auctionId AND b.isDeleted = false")
    Integer findMaxBidPriceByAuctionId(Long auctionId);

    @Query("SELECT b FROM Bid b WHERE b.auction.id = :auctionId " +
            "AND b.isDeleted = false " +
            "ORDER BY b.price DESC LIMIT 1")
    Optional<Bid> findTopValidBidByAuctionId(Long auctionId);

    Optional<Bid> findByAuctionIdAndIsWinningTrue(Long auctionId);

    boolean existsByAuctionIdAndIsWinningTrue(Long auctionId);

    @Query(value = """
            SELECT * 
            FROM bid b
            WHERE b.auction_id IN (:auctionIdList)
            ORDER BY b.auction_id, b.price DESC
            """, nativeQuery = true)
    List<Bid> findByAuctionIdList(@Param("auctionIdList") List<Long> auctionIdList);

    Integer countByAuctionIdAndIsDeletedFalse(Long auctionId);

    Boolean existsByAuctionIdAndBidderIdAndIsDeletedTrue(Long auctionId, Long bidderId);

    List<Bid> findByAuctionId(Long auctionId);

    @Query("SELECT b.auction.id AS auctionId, COUNT(b.id) AS bidCount FROM Bid b WHERE b.auction.id IN :auctionIds GROUP BY b.auction.id")
    List<AuctionBidCount> countBidsByAuctionIds(@Param("auctionIds") List<Long> auctionIds);


    @Query("""
            SELECT
                a.id AS auctionId,
                a.title AS title,
                a.basePrice AS basePrice,
                MAX(b.price) AS itemPrice,
                img.url AS thumbnailUrl,
                b.bidder.name AS buyer
            FROM Bid b
            JOIN b.auction a
            LEFT JOIN AuctionImage img ON img.auction.id = a.id AND img.imageSeq = 0
            WHERE b.isWinning = true
              AND a.status = 'COMPLETED'
              AND a.endTime >= :sevenDaysAgo
            GROUP BY a.id, a.title, a.basePrice, img.url, b.bidder.name
            ORDER BY itemPrice DESC
            """)
    List<TopWinningBid> findTop5WinningBids(@Param("sevenDaysAgo") LocalDateTime sevenDaysAgo, Pageable pageable);

    @Query("""
                SELECT new com.samyookgoo.palgoosam.bid.service.response.BidStatsResponse(
                    MAX(b.price),
                    COUNT(*),
                    COUNT(DISTINCT b.bidder)
                )
                FROM Bid b
                WHERE b.auction.id = :auctionId AND b.isDeleted = false
            """)
    BidStatsResponse findBidStatsByAuctionId(@Param("auctionId") Long auctionId);

    @Query(value = """
            SELECT bidId, isWinning, userPrice, title, endTime, startTime, status, auctionId, mainImageUrl
            FROM (
                SELECT
                    b.id as bidId,
                    b.is_winning as isWinning,
                    b.price as userPrice,
                    a.title as title,
                    a.end_time as endTime,
                    a.start_time as startTime,
                    a.status as status,
                    a.id as auctionId,
                    ai.url as mainImageUrl,
                    ROW_NUMBER() OVER (PARTITION BY a.id ORDER BY b.price DESC) as rn
                FROM bid as b
                JOIN user as u ON u.id = b.bidder_id
                JOIN auction as a ON b.auction_id = a.id
                LEFT JOIN auction_image as ai ON ai.auction_id = a.id AND ai.image_seq = 0
                WHERE u.id = :userId AND b.is_deleted=false
            ) as ranked
            WHERE rn = 1;
            """, nativeQuery = true)
    List<BidForMyPageProjection> findAllBidsByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT MAX(b2.price) as bidHighestPrice, a.id as auctionId
            FROM bid as b1
            JOIN auction as a ON a.id = b1.auction_id
            JOIN user as u ON u.id = b1.bidder_id
            LEFT JOIN bid as b2 ON b2.auction_id = a.id and b2.is_deleted = false
            WHERE u.id = :userId
            GROUP BY a.id
            """, nativeQuery = true)
    List<BidForHighestPriceProjection> findHighestBidProjectsByBidderId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(COUNT(DISTINCT b.bidder_id) ,0)
            FROM bid as b
            WHERE b.auction_id = :auctionId AND b.is_deleted = false
            """, nativeQuery = true)
    Long countBidderByAuctionId(@Param("auctionId") Long auctionId);
}
