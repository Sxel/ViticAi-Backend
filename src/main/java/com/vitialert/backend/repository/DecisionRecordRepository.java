package com.vitialert.backend.repository;

import com.vitialert.backend.domain.DecisionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, Long> {

    @Query(value = """
            select d from DecisionRecord d
            where d.node.id = :nodeId
            order by d.timestamp desc
            """,
            countQuery = "select count(d) from DecisionRecord d where d.node.id = :nodeId")
    Page<DecisionRecord> findByNode(@Param("nodeId") Long nodeId, Pageable pageable);
}
