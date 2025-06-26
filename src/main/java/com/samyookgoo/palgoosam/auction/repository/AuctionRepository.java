package com.samyookgoo.palgoosam.auction.repository;

import com.samyookgoo.palgoosam.auction.constant.AuctionStatus;
import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.domain.AuctionForMyPageProjection;
import com.samyookgoo.palgoosam.auction.projection.AuctionBidCount;
import com.samyookgoo.palgoosam.auction.projection.AuctionScrapCount;
import com.samyookgoo.palgoosam.auction.projection.DashboardProjection;
import com.samyookgoo.palgoosam.auction.projection.RankingAuction;
import com.samyookgoo.palgoosam.auction.projection.RecentAuction;
import com.samyookgoo.palgoosam.auction.projection.SubCategoryBestItem;
import com.samyookgoo.palgoosam.auction.projection.UpcomingAuction;
import com.samyookgoo.palgoosam.bid.domain.BidForHighestPriceProjection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Optional<Auction> findById(Long id);

    List<Auction> findByCategoryIdAndStatus(Long categoryId, AuctionStatus status);

    @Query("SELECT a FROM Auction a WHERE a.category.parent.id = :parentId AND a.status = :status")
    List<Auction> findByParentCategoryIdAndStatus(@Param("parentId") Long parentId,
                                                  @Param("status") AuctionStatus status);

    @Modifying
    @Query("""
                UPDATE Auction a
                SET a.status = 'active'
                WHERE a.status = 'pending'
                  AND a.startTime <= :now
            """)
    int updateStatusToActive(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
                UPDATE Auction a
                SET a.status = 'completed'
                WHERE a.status = 'active'
                  AND a.endTime <= :now
            """)
    int updateStatusToCompleted(@Param("now") LocalDateTime now);

    @Query("""
            SELECT 
                 a.id AS auctionId, 
                 a.title AS title, 
                 a.description AS description, 
                 a.itemCondition AS itemCondition,
                 a.basePrice AS basePrice, 
                 ai.url AS thumbnailUrl, 
                 a.startTime AS startTime
            FROM Auction a
            LEFT JOIN AuctionImage ai ON ai.auction.id = a.id AND ai.imageSeq = 0
            WHERE a.status = :status AND a.startTime > CURRENT_TIMESTAMP
            ORDER BY a.startTime ASC
            """)
    List<UpcomingAuction> findTop3UpcomingAuctions(@Param("status") AuctionStatus status, Pageable pageable);

    @Query("""
            SELECT 
                a.id AS auctionId,
                a.title AS title,
                a.description AS description,
                a.status AS status,
                a.basePrice AS basePrice,
                ai.url AS thumbnailUrl
            FROM Auction a
            LEFT JOIN AuctionImage ai ON ai.auction.id = a.id AND ai.imageSeq = 0
            WHERE a.status IN :statuses
            ORDER BY a.createdAt DESC
            """)
    List<RecentAuction> findTop6RecentAuctions(@Param("statuses") List<AuctionStatus> statuses, Pageable pageable);


    @Query("""
            SELECT a.id AS auctionId, a.title AS title, a.description AS description,
                   a.itemCondition AS itemCondition, img.url AS thumbnailUrl
            FROM Auction a
            LEFT JOIN AuctionImage img ON img.auction.id = a.id AND img.imageSeq = 0
            WHERE a.id IN :auctionIds
            """)
    List<RankingAuction> findRankingByIds(@Param("auctionIds") List<Long> auctionIds);

    @Query("""
            SELECT a.id AS auctionId, COUNT(b.id) AS bidCount
            FROM Auction a
            LEFT JOIN Bid b ON b.auction.id = a.id
            WHERE a.status = 'ACTIVE'
            GROUP BY a.id
            ORDER BY COUNT(b.id) DESC, a.id ASC
            """)
    List<AuctionBidCount> findTop8AuctionBidCounts(Pageable pageable);

    @Query("""
            SELECT a.id AS auctionId, COUNT(s.id) AS scrapCount
            FROM Auction a
            LEFT JOIN Scrap s ON s.auction.id = a.id
            WHERE a.status = 'pending'
            GROUP BY a.id
            ORDER BY COUNT(s.id) DESC, a.id ASC
            """)
    List<AuctionScrapCount> findTop8AuctionScrapCounts(Pageable pageable);

    @Query("""
                SELECT a.id AS auctionId, a.title AS title, a.status AS status, a.itemCondition AS itemCondition,
                       img.url AS thumbnailUrl, a.startTime AS startTime
                FROM Auction a
                JOIN a.category c
                JOIN c.parent mid
                LEFT JOIN AuctionImage img ON img.auction.id = a.id AND img.imageSeq = 0
                WHERE mid.id = :subCategoryId AND a.status IN ('pending', 'active')
                ORDER BY (SELECT COUNT(s.id) FROM Scrap s WHERE s.auction.id = a.id) DESC, a.id ASC
            """)
    List<SubCategoryBestItem> findTop50BySubCategoryId(@Param("subCategoryId") Long subCategoryId, Pageable pageable);


    @Query(value = """
            SELECT a.id as auctionId, a.title as title, a.end_time as endTime, a.start_time as startTime, a.status as status, ai.url as mainImageUrl
            FROM auction as a
            LEFT JOIN auction_image as ai ON ai.auction_id = a.id AND ai.image_seq = 0
            WHERE a.seller_id = :sellerId AND a.is_deleted = false
            """, nativeQuery = true)
    List<AuctionForMyPageProjection> findAllAuctionProjectionBySellerId(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT a.id as auctionId, a.title as title, a.end_time as endTime, a.start_time as startTime, a.status as status, ai.url as mainImageUrl
            FROM auction as a
            JOIN scrap as s ON s.auction_id = a.id AND s.user_id = :userId
            LEFT JOIN auction_image as ai ON ai.auction_id = a.id AND ai.image_seq = 0
            WHERE a.is_deleted = false
            """, nativeQuery = true)
    List<AuctionForMyPageProjection> findAllAuctionProjectionWithScrapByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(MAX(b.price), 0) as bidHighestPrice, a.id as auctionId
            FROM auction as a
            LEFT JOIN bid as b ON b.auction_id = a.id AND b.is_deleted = false
            JOIN user as u ON u.id = a.seller_id
            WHERE u.id = :userId AND a.is_deleted = false
            GROUP BY a.id
            """, nativeQuery = true)
    List<BidForHighestPriceProjection> findHighestBidProjectsBySellerId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(MAX(b.price), 0) as bidHighestPrice, a.id as auctionId
            FROM auction as a
            LEFT JOIN bid as b ON b.auction_id = a.id AND b.is_deleted = false
            JOIN scrap as s ON s.auction_id = a.id AND s.user_id = :userId
            WHERE a.is_deleted = false
            GROUP BY a.id
            """, nativeQuery = true)
    List<BidForHighestPriceProjection> findHighestBidProjectsByScraperId(@Param("userId") Long id);

    @Query(value = """
            SELECT 
                (SELECT COUNT(*) FROM user) AS totalUserCount,
                (SELECT COUNT(*) FROM auction) AS totalAuctionCount,
                (SELECT COUNT(*) FROM auction WHERE status = 'active') AS activeAuctionCount
            """, nativeQuery = true)
    DashboardProjection getDashboardCounts();

    List<AuctionStatus> status(AuctionStatus status);


    @Query(value = """
            SELECT a.id as auctionId
            FROM auction as a 
            WHERE status IN ('active', 'pending')
            ORDER BY a.id
            """, nativeQuery = true)
    List<Long> getActiveAuctionIds();
}
