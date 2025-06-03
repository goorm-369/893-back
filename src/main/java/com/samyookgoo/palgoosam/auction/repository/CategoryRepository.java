package com.samyookgoo.palgoosam.auction.repository;

import com.samyookgoo.palgoosam.auction.domain.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children LEFT JOIN FETCH c.parent")
    List<Category> findAllWithChildrenAndParent();

    List<Category> findByParentIsNotNullAndChildrenIsNotEmpty();
}
