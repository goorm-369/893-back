package com.samyookgoo.palgoosam.auction.repository;

import com.samyookgoo.palgoosam.auction.constant.SortType;
import com.samyookgoo.palgoosam.auction.domain.AuctionSearchProjection;
import com.samyookgoo.palgoosam.auction.service.dto.AuctionSearchDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuctionSearchRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SEARCH_BASIC_QUERY = """
            SELECT
                a.id,
                a.title,
                a.start_time,
                a.end_time,
                a.status,
                a.base_price,
                i.url as thumbnail_url,
                COALESCE(
                    bid_price.price,
                    a.base_price
                ) as current_price
            FROM auction a
            LEFT JOIN (
                SELECT auction_id, price
                FROM bid as b
                WHERE b.is_deleted = false AND b.is_winning = true
                  AND (:minPrice IS NULL OR b.price >= :minPrice)
                  AND (:maxPrice IS NULL OR b.price <= :maxPrice)
            ) bid_price ON a.id = bid_price.auction_id
            JOIN auction_image i ON a.id = i.auction_id AND i.image_seq = 0
            JOIN category c ON a.category_id = c.id
            WHERE
                MATCH(a.title, a.description) AGAINST (:keyword IN NATURAL LANGUAGE MODE) AND
                (:categoryId IS NULL OR a.category_id IN (
                    WITH RECURSIVE CategoryHierarchy AS (
                        SELECT id FROM category WHERE id = :categoryId
                        UNION ALL
                        SELECT c.id FROM category c
                        JOIN CategoryHierarchy ch ON c.parent_id = ch.id
                    )
                    SELECT id FROM CategoryHierarchy
                )) AND
                (
                    ((:isBrandNew IS NULL OR :isBrandNew = FALSE) AND
                     (:isLikeNew IS NULL OR :isLikeNew = FALSE) AND
                     (:isGentlyUsed IS NULL OR :isGentlyUsed = FALSE) AND
                     (:isHeavilyUsed IS NULL OR :isHeavilyUsed = FALSE) AND
                     (:isDamaged IS NULL OR :isDamaged = FALSE))
                    OR
                    ((:isBrandNew = TRUE AND a.item_condition = 'brand_new') OR
                     (:isLikeNew = TRUE AND a.item_condition = 'like_new') OR
                     (:isGentlyUsed = TRUE AND a.item_condition = 'gently_used') OR
                     (:isHeavilyUsed = TRUE AND a.item_condition = 'heavily_used') OR
                     (:isDamaged = TRUE AND a.item_condition = 'damaged'))
                ) AND
                (
                    ((:isPending IS NULL OR :isPending = FALSE) AND
                     (:isActive IS NULL OR :isActive = FALSE) AND
                     (:isCompleted IS NULL OR :isCompleted = FALSE))
                    OR
                    ((:isPending = TRUE AND a.status = 'pending') OR
                     (:isActive = TRUE AND a.status = 'active') OR
                     (:isCompleted = TRUE AND a.status = 'completed'))
                ) AND
                (:minPrice IS NULL OR bid_price.price >= :minPrice OR a.base_price >= :minPrice) AND
                (:maxPrice IS NULL OR bid_price.price <= :maxPrice OR a.base_price <= :maxPrice)
            """;

    private static final String SEARCH_AUCTIONLIST_WITH_AGGREGATION_QUERY = """
            SELECT
                a.id,
                a.title,
                a.start_time,
                a.end_time,
                a.status,
                a.base_price,
                i.url as thumbnail_url,
                COALESCE(
                    bid_price.price,
                    a.base_price
                ) as current_price,
                COALESCE(
                    (
                    SELECT
                        COUNT(*)
                    FROM
                        user u
                    WHERE
                        EXISTS(
                            SELECT 1 FROM bid b
                            WHERE b.bidder_id = u.id
                              AND b.auction_id = a.id
                              AND b.is_deleted = false
                        )), 0
                ) as bidder_count,
                (SELECT COUNT(*) FROM scrap s WHERE s.auction_id = a.id) as scrap_count
            FROM auction a
            LEFT JOIN (
                SELECT auction_id, price
                FROM bid as b
                WHERE b.is_deleted = false AND b.is_winning = true
                  AND (:minPrice IS NULL OR b.price >= :minPrice)
                  AND (:maxPrice IS NULL OR b.price <= :maxPrice)
            ) bid_price ON a.id = bid_price.auction_id
            JOIN auction_image i ON a.id = i.auction_id AND i.image_seq = 0
            JOIN category c ON a.category_id = c.id
            WHERE
                MATCH(a.title, a.description) AGAINST (:keyword IN NATURAL LANGUAGE MODE) AND
                (:categoryId IS NULL OR a.category_id IN (
                    WITH RECURSIVE CategoryHierarchy AS (
                        SELECT id FROM category WHERE id = :categoryId
                        UNION ALL
                        SELECT c.id FROM category c
                        JOIN CategoryHierarchy ch ON c.parent_id = ch.id
                    )
                    SELECT id FROM CategoryHierarchy
                )) AND
                (
                    ((:isBrandNew IS NULL OR :isBrandNew = FALSE) AND
                     (:isLikeNew IS NULL OR :isLikeNew = FALSE) AND
                     (:isGentlyUsed IS NULL OR :isGentlyUsed = FALSE) AND
                     (:isHeavilyUsed IS NULL OR :isHeavilyUsed = FALSE) AND
                     (:isDamaged IS NULL OR :isDamaged = FALSE))
                    OR
                    ((:isBrandNew = TRUE AND a.item_condition = 'brand_new') OR
                     (:isLikeNew = TRUE AND a.item_condition = 'like_new') OR
                     (:isGentlyUsed = TRUE AND a.item_condition = 'gently_used') OR
                     (:isHeavilyUsed = TRUE AND a.item_condition = 'heavily_used') OR
                     (:isDamaged = TRUE AND a.item_condition = 'damaged'))
                ) AND
                (
                    ((:isPending IS NULL OR :isPending = FALSE) AND
                     (:isActive IS NULL OR :isActive = FALSE) AND
                     (:isCompleted IS NULL OR :isCompleted = FALSE))
                    OR
                    ((:isPending = TRUE AND a.status = 'pending') OR
                     (:isActive = TRUE AND a.status = 'active') OR
                     (:isCompleted = TRUE AND a.status = 'completed'))
                ) AND
                (:minPrice IS NULL OR bid_price.price >= :minPrice OR a.base_price >= :minPrice) AND
                (:maxPrice IS NULL OR bid_price.price <= :maxPrice OR a.base_price <= :maxPrice)
            GROUP BY a.id, a.title, a.start_time, a.end_time, a.status, a.base_price, a.created_at, bid_price.price, i.url
            """;

    private final RowMapper<AuctionSearchProjection> basicRowMapper = (rs, rowNum) ->
            AuctionSearchProjection.builder()
                    .id(rs.getLong("id"))
                    .title(rs.getString("title"))
                    .startTime(rs.getTimestamp("start_time").toLocalDateTime())
                    .endTime(rs.getTimestamp("end_time").toLocalDateTime())
                    .status(rs.getString("status"))
                    .basePrice(rs.getInt("base_price"))
                    .thumbnailUrl(rs.getString("thumbnail_url"))
                    .currentPrice(rs.getInt("current_price"))
                    .build();

    private final RowMapper<AuctionSearchProjection> aggregationRowMapper = (rs, rowNum) ->
            AuctionSearchProjection.builder()
                    .id(rs.getLong("id"))
                    .title(rs.getString("title"))
                    .startTime(rs.getTimestamp("start_time").toLocalDateTime())
                    .endTime(rs.getTimestamp("end_time").toLocalDateTime())
                    .status(rs.getString("status"))
                    .basePrice(rs.getInt("base_price"))
                    .thumbnailUrl(rs.getString("thumbnail_url"))
                    .currentPrice(rs.getInt("current_price"))
                    .bidderCount(rs.getLong("bidder_count"))
                    .scrapCount(rs.getLong("scrap_count"))
                    .build();

    public List<AuctionSearchProjection> search(AuctionSearchDto request) {
        String sortBy = request.getSortBy();

        if (isAggregationSort(sortBy)) {
            return searchWithAggregationSort(request);
        }

        return searchWithBasicSort(request);
    }

    private List<AuctionSearchProjection> searchWithAggregationSort(AuctionSearchDto request) {
        String orderBy = buildAggregationOrderByClause(request.getSortBy());
        String fullQuery = SEARCH_AUCTIONLIST_WITH_AGGREGATION_QUERY
                + orderBy + " LIMIT :limit OFFSET :offset";

        MapSqlParameterSource params = createParams(request)
                .addValue("limit", request.getLimit())
                .addValue("offset", request.getOffset());
        ;

        return jdbcTemplate.query(fullQuery, params, aggregationRowMapper);
    }

    private List<AuctionSearchProjection> searchWithBasicSort(AuctionSearchDto request) {
        String orderBy = buildBasicOrderByClause(request.getSortBy());
        String fullQuery = SEARCH_BASIC_QUERY + orderBy + " LIMIT :limit OFFSET :offset";

        MapSqlParameterSource params = createParams(request);

        return jdbcTemplate.query(fullQuery, params, basicRowMapper);
    }

    private boolean isAggregationSort(String sortBy) {
        return String.valueOf(SortType.scrap_count_desc).equals(sortBy) ||
                String.valueOf(SortType.bidder_count_desc).equals(sortBy);
    }

    private String buildBasicOrderByClause(String sortBy) {
        StringBuilder orderBy = new StringBuilder(" ORDER BY ");
        if (String.valueOf(SortType.price_asc).equals(sortBy)) {
            orderBy.append("current_price ASC");
        } else if (String.valueOf(SortType.price_desc).equals(sortBy)) {
            orderBy.append("current_price DESC");
        } else {
            orderBy.append("a.created_at DESC");
        }
        return orderBy.toString();
    }

    private MapSqlParameterSource createParams(AuctionSearchDto request) {
        return new MapSqlParameterSource()
                .addValue("keyword", request.getKeyword())
                .addValue("categoryId", request.getCategoryId())
                .addValue("maxPrice", request.getMaxPrice())
                .addValue("minPrice", request.getMinPrice())
                // 상품 상태 필터
                .addValue("isBrandNew", request.getIsBrandNew())
                .addValue("isLikeNew", request.getIsLikeNew())
                .addValue("isGentlyUsed", request.getIsGentlyUsed())
                .addValue("isHeavilyUsed", request.getIsHeavilyUsed())
                .addValue("isDamaged", request.getIsDamaged())
                // 경매 상태 필터
                .addValue("isPending", request.getIsPending())
                .addValue("isActive", request.getIsActive())
                .addValue("isCompleted", request.getIsCompleted())
                //페이지네이션
                .addValue("limit", request.getLimit())
                .addValue("offset", request.getOffset());
    }

    private String buildAggregationOrderByClause(String sortBy) {
        StringBuilder orderBy = new StringBuilder(" ORDER BY ");
        if (String.valueOf(SortType.scrap_count_desc).equals(sortBy)) {
            orderBy.append("scrap_count DESC");
        } else if (String.valueOf(SortType.bidder_count_desc).equals(sortBy)) {
            orderBy.append("bidder_count DESC");
        } else {
            orderBy.append("a.created_at DESC");
        }
        return orderBy.toString();
    }

    public Long countAuctionsInList(AuctionSearchDto request) {
        String countQuery = """
                SELECT
                    COUNT(a.id)
                FROM auction a
                LEFT JOIN bid b ON a.id = b.auction_id AND b.is_deleted = false AND b.is_winning = true
                JOIN category c ON a.category_id = c.id
                WHERE
                    MATCH(a.title, a.description) AGAINST (:keyword IN NATURAL LANGUAGE MODE) AND
                    (:categoryId IS NULL OR a.category_id IN (
                        WITH RECURSIVE CategoryHierarchy AS (
                            SELECT id
                            FROM category
                            WHERE id = :categoryId
                            UNION ALL
                            SELECT c.id
                            FROM category c
                            JOIN CategoryHierarchy ch ON c.parent_id = ch.id
                        )
                        SELECT id FROM CategoryHierarchy
                    )) AND
                    (
                        ((:isBrandNew IS NULL OR :isBrandNew = FALSE) AND
                         (:isLikeNew IS NULL OR :isLikeNew = FALSE) AND
                         (:isGentlyUsed IS NULL OR :isGentlyUsed = FALSE) AND
                         (:isHeavilyUsed IS NULL OR :isHeavilyUsed = FALSE) AND
                         (:isDamaged IS NULL OR :isDamaged = FALSE))
                        OR
                        ((:isBrandNew = TRUE AND a.item_condition = 'brand_new') OR
                         (:isLikeNew = TRUE AND a.item_condition = 'like_new') OR
                         (:isGentlyUsed = TRUE AND a.item_condition = 'gently_used') OR
                         (:isHeavilyUsed = TRUE AND a.item_condition = 'heavily_used') OR
                         (:isDamaged = TRUE AND a.item_condition = 'damaged'))
                    ) AND
                    (
                        ((:isPending IS NULL OR :isPending = FALSE) AND
                         (:isActive IS NULL OR :isActive = FALSE) AND
                         (:isCompleted IS NULL OR :isCompleted = FALSE))
                        OR
                        ((:isPending = TRUE AND a.status = 'pending') OR
                         (:isActive = TRUE AND a.status = 'active') OR
                         (:isCompleted = TRUE AND a.status = 'completed'))
                    ) AND
                    (:minPrice IS NULL OR a.base_price >= :minPrice OR b.price >= :minPrice) AND
                    (:maxPrice IS NULL OR a.base_price <= :maxPrice OR b.price <= :maxPrice)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", request.getKeyword())
                .addValue("categoryId", request.getCategoryId())
                .addValue("maxPrice", request.getMaxPrice())
                .addValue("minPrice", request.getMinPrice())
                .addValue("isBrandNew", request.getIsBrandNew())
                .addValue("isLikeNew", request.getIsLikeNew())
                .addValue("isGentlyUsed", request.getIsGentlyUsed())
                .addValue("isHeavilyUsed", request.getIsHeavilyUsed())
                .addValue("isDamaged", request.getIsDamaged())
                .addValue("isPending", request.getIsPending())
                .addValue("isActive", request.getIsActive())
                .addValue("isCompleted", request.getIsCompleted());

        return jdbcTemplate.queryForObject(countQuery, params, Long.class);
    }
}