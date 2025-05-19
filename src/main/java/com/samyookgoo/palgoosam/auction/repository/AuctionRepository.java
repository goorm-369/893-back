package com.samyookgoo.palgoosam.auction.repository;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.domain.AuctionStatus;
import com.samyookgoo.palgoosam.auction.dto.AuctionSearchParam;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    List<Auction> findAllBySeller_Id(Long sellerId);

    @Query(value = """
             SELECT a.*  FROM auction a
             JOIN user u ON a.seller_id = u.id
             JOIN category c ON a.category_id = c.id
             WHERE
             (:#{#request.keyword} IS NULL OR MATCH(a.title, a.description)
             AGAINST (:#{#request.keyword} IN NATURAL LANGUAGE MODE)) AND
             -- 카테고리 SQL
             (:#{#request.categoryId} IS NULL OR a.category_id IN (
                 WITH RECURSIVE CategoryHierarchy AS (
                 SELECT id FROM category WHERE id = :#{#request.categoryId}
                 UNION ALL
                 SELECT c.id FROM category c
                 JOIN CategoryHierarchy ch ON c.parent_id = ch.id
                 )
                 SELECT id FROM CategoryHierarchy
             )) AND
             --
             (:#{#request.minPrice} IS NULL OR a.base_price >= :#{#request.minPrice}) AND
             (:#{#request.maxPrice} IS NULL OR a.base_price <= :#{#request.maxPrice}) AND
                     (
                         ((:#{#request.isBrandNew} IS NULL OR :#{#request.isBrandNew} = FALSE) AND
                          (:#{#request.isLikeNew} IS NULL OR :#{#request.isLikeNew} = FALSE) AND
                          (:#{#request.isGentlyUsed} IS NULL OR :#{#request.isGentlyUsed} = FALSE) AND
                          (:#{#request.isHeavilyUsed} IS NULL OR :#{#request.isHeavilyUsed} = FALSE) AND
                          (:#{#request.isDamaged} IS NULL OR :#{#request.isDamaged} = FALSE))
                         OR
                         ((:#{#request.isBrandNew} = TRUE AND a.item_condition = :#{T(com.samyookgoo.palgoosam.auction.constant.ItemCondition).BRAND_NEW_VALUE}) OR
                          (:#{#request.isLikeNew} = TRUE AND a.item_condition = :#{T(com.samyookgoo.palgoosam.auction.constant.ItemCondition).LIKE_NEW_VALUE} ) OR
                          (:#{#request.isGentlyUsed} = TRUE AND a.item_condition = :#{T(com.samyookgoo.palgoosam.auction.constant.ItemCondition).GENTLY_USED_VALUE} ) OR
                          (:#{#request.isHeavilyUsed} = TRUE AND a.item_condition = :#{T(com.samyookgoo.palgoosam.auction.constant.ItemCondition).HEAVILY_USED_VALUE} ) OR
                          (:#{#request.isDamaged} = TRUE AND a.item_condition = :#{T(com.samyookgoo.palgoosam.auction.constant.ItemCondition).DAMAGED_VALUE} ))
                     ) AND
                     (
                         ((:#{#request.isPending} IS NULL OR :#{#request.isPending} = FALSE) AND
                          (:#{#request.isActive} IS NULL OR :#{#request.isActive} = FALSE) AND
                          (:#{#request.isCompleted} IS NULL OR :#{#request.isCompleted} = FALSE))
                         OR
                         ((:#{#request.isPending} = TRUE AND a.status = :#{T(com.samyookgoo.palgoosam.auction.constant.AuctionStatus).PENDING_VALUE} ) OR
                          (:#{#request.isActive} = TRUE AND a.status = :#{T(com.samyookgoo.palgoosam.auction.constant.AuctionStatus).ACTIVE_VALUE} ) OR
                          (:#{#request.isCompleted} = TRUE AND a.status = :#{T(com.samyookgoo.palgoosam.auction.constant.AuctionStatus).COMPLETED_VALUE} ))
                     )
            ORDER BY a.created_at DESC
            """, nativeQuery = true)
    List<Auction> findAllWithDetails(@Param("request") AuctionSearchParam auctionSearchParam);

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
            SELECT a
            FROM Auction a
            WHERE a.startTime BETWEEN :now AND :oneMinuteAgo
            """)
    List<Auction> findByStartTimeInOneMinute(@Param("now") LocalDateTime now,
                                             @Param("oneMinuteAgo") LocalDateTime oneMinuteAgo);

    @Query("""
            SELECT a
            FROM Auction a
            WHERE a.endTime BETWEEN :now AND :oneMinuteAgo AND a.status = 'completed'
            """)
    List<Auction> findByEndTimeInOneMinute(@Param("now") LocalDateTime now,
                                           @Param("oneMinuteAgo") LocalDateTime oneMinuteAgo);
}
