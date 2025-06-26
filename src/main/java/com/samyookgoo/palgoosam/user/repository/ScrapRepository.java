package com.samyookgoo.palgoosam.user.repository;

import com.samyookgoo.palgoosam.auction.domain.Auction;
import com.samyookgoo.palgoosam.auction.projection.AuctionScrapCount;
import com.samyookgoo.palgoosam.user.domain.Scrap;
import com.samyookgoo.palgoosam.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScrapRepository extends JpaRepository<Scrap, Long> {
    List<Scrap> findAllByUser_Id(Long auctionId);

    int countByAuctionId(Long auctionId);

    @Query(value = """
            SELECT *
            FROM scrap s
            WHERE s.auction_id IN (:auctionIdList)
            """, nativeQuery = true)
    List<Scrap> findByAuctionIdList(@Param("auctionIdList") List<Long> auctionIdList);

    boolean existsByUserAndAuction(User user, Auction auction);

    Optional<Scrap> findByUserAndAuction(User user, Auction auction);

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    @Query("""
            SELECT s.auction.id AS auctionId, COUNT(s) AS scrapCount
            FROM Scrap s
            WHERE s.auction.id IN :auctionIds
            GROUP BY s.auction.id
            """)
    List<AuctionScrapCount> countGroupedByAuctionIds(List<Long> auctionIds);

    @Query("SELECT s.auction.id FROM Scrap s WHERE s.user.id = :userId")
    List<Long> findAuctionIdsByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(COUNT(s.id), 0)
            FROM scrap as s
            WHERE s.auction_id = :auctionId
            """, nativeQuery = true)
    Long countScrapCountByAuctionId(@Param("auctionId") Long auctionId);
}
