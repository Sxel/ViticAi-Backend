package com.vitialert.backend.repository;

import com.vitialert.backend.domain.TelemetryReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Tres consultas, todas acotadas por nodo y ventana temporal y todas resueltas por el indice
 * {@code (node_id, timestamp_received)}. La ingesta de un POST nunca lee el historico.
 */
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    /** Ultimas lecturas del nodo. Usar con {@code PageRequest.of(0, 1)} para la mas reciente. */
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
            order by t.timestampReceived desc
            """)
    List<TelemetryReading> findLatest(@Param("nodeId") Long nodeId, Pageable pageable);

    /** Historico paginado, del mas reciente al mas antiguo. */
    @Query(value = """
            select t from TelemetryReading t
            where t.node.id = :nodeId
              and t.timestampReceived >= :from
              and t.timestampReceived <= :to
            order by t.timestampReceived desc
            """,
            countQuery = """
                    select count(t) from TelemetryReading t
                    where t.node.id = :nodeId
                      and t.timestampReceived >= :from
                      and t.timestampReceived <= :to
                    """)
    Page<TelemetryReading> findRange(@Param("nodeId") Long nodeId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    /**
     * Serie ascendente para construir los buckets horarios. {@code to} es exclusivo para que
     * dos rangos contiguos no compartan lecturas.
     */
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
              and t.timestampReceived >= :from
              and t.timestampReceived < :to
            order by t.timestampReceived asc
            """)
    List<TelemetryReading> findRangeAsc(@Param("nodeId") Long nodeId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);
}
