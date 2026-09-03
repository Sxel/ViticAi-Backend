package com.vitialert.backend.repository;

import com.vitialert.backend.domain.IrrigationEvent;
import com.vitialert.backend.domain.IrrigationEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface IrrigationEventRepository extends JpaRepository<IrrigationEvent, Long> {

    @Query("""
            select e from IrrigationEvent e
            where e.node.id = :nodeId and e.estado = :estado
            order by e.startedAt desc
            """)
    List<IrrigationEvent> findByStatus(@Param("nodeId") Long nodeId,
                                       @Param("estado") IrrigationEventStatus estado,
                                       Pageable pageable);

    @Query(value = """
            select e from IrrigationEvent e
            where e.node.id = :nodeId
              and e.startedAt >= :from
              and e.startedAt <= :to
            order by e.startedAt desc
            """,
            countQuery = """
                    select count(e) from IrrigationEvent e
                    where e.node.id = :nodeId
                      and e.startedAt >= :from
                      and e.startedAt <= :to
                    """)
    Page<IrrigationEvent> findRange(@Param("nodeId") Long nodeId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);

    /**
     * Volumen aplicado por los riegos CERRADOS dentro de (from, to].
     * Devuelve null si no hubo ningun evento cerrado en la ventana.
     */
    @Query("""
            select sum(e.volumenAplicadoL) from IrrigationEvent e
            where e.node.id = :nodeId
              and e.endedAt is not null
              and e.volumenAplicadoL is not null
              and e.endedAt > :from
              and e.endedAt <= :to
            """)
    BigDecimal sumAppliedVolume(@Param("nodeId") Long nodeId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);
}
