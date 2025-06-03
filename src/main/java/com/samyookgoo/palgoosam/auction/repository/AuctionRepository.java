package com.samyookgoo.palgoosam.auction.repository;

import com.samyookgoo.palgoosam.auction.constant.AuctionStatus;
import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.domain.AuctionForMyPageProjection;
import com.samyookgoo.palgoosam.auction.projection.AuctionBidCount;
import com.samyookgoo.palgoosam.auction.projection.AuctionScrapCount;
import com.samyookgoo.palgoosam.auction.projection.RankingAuction;
import com.samyookgoo.palgoosam.auction.projection.SubCategoryBestItem;
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

    long countByStatus(AuctionStatus status);

    List<Auction> findTop3ByStatusAndStartTimeAfterOrderByStartTimeAsc(AuctionStatus status, LocalDateTime now);

    List<Auction> findTop6ByStatusInOrderByCreatedAtDesc(List<AuctionStatus> statuses);

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

    @Query(value = """
            SELECT a.id as auctionId, a.title as title, a.end_time as endTime, a.start_time as startTime, a.status as status, ai.url as mainImageUrl
            FROM auction as a
            JOIN auction_image as ai ON ai.auction_id = a.id AND ai.image_seq = 0
            WHERE a.seller_id = :sellerId
            """, nativeQuery = true)
    List<AuctionForMyPageProjection> findAllAuctionProjectionBySellerId(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT a.id as auctionId, a.title as title, a.end_time as endTime, a.start_time as startTime, a.status as status, ai.url as mainImageUrl
            FROM auction as a
            JOIN scrap as s ON s.auction_id = a.id AND s.user_id = :userId
            JOIN auction_image as ai ON ai.auction_id = a.id AND ai.image_seq = 0
            """, nativeQuery = true)
    List<AuctionForMyPageProjection> findAllAuctionProjectionWithScrapByUserId(@Param("userId") Long userId);
           
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
    List<SubCategoryBestItem> findTop3BySubCategoryId(@Param("subCategoryId") Long subCategoryId, Pageable pageable);

}
