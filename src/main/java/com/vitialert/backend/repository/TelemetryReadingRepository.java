package com.vitialert.backend.repository;

import com.vitialert.backend.domain.TelemetryReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Todas las consultas son explicitas y acotadas por nodo y ventana temporal: la ingesta
 * de un POST nunca debe leer la tabla completa ni recalcular el historico.
 */
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {

    @EntityGraph(attributePaths = "node")
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
            order by t.timestampReceived desc
            """)
    List<TelemetryReading> findLatest(@Param("nodeId") Long nodeId, Pageable pageable);

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
    @EntityGraph(attributePaths = "node")
    Page<TelemetryReading> findRange(@Param("nodeId") Long nodeId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    /** Serie completa ascendente para la capa de agregacion temporal. */
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

    /** Observacion con humedad de suelo mas cercana ANTES (o en) el instante buscado. */
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
              and t.humedadSueloPct is not null
              and t.timestampReceived <= :target
              and t.timestampReceived >= :lowerBound
            order by t.timestampReceived desc
            """)
    List<TelemetryReading> findSoilNearestBefore(@Param("nodeId") Long nodeId,
                                                 @Param("target") Instant target,
                                                 @Param("lowerBound") Instant lowerBound,
                                                 Pageable pageable);

    /** Observacion con humedad de suelo mas cercana DESPUES del instante buscado. */
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
              and t.humedadSueloPct is not null
              and t.timestampReceived > :target
              and t.timestampReceived <= :upperBound
            order by t.timestampReceived asc
            """)
    List<TelemetryReading> findSoilNearestAfter(@Param("nodeId") Long nodeId,
                                                @Param("target") Instant target,
                                                @Param("upperBound") Instant upperBound,
                                                Pageable pageable);

    /** Media de humedad de suelo en (from, to]. Devuelve null si no hay observaciones. */
    @Query("""
            select avg(t.humedadSueloPct) from TelemetryReading t
            where t.node.id = :nodeId
              and t.humedadSueloPct is not null
              and t.timestampReceived > :from
              and t.timestampReceived <= :to
            """)
    Double averageSoilMoisture(@Param("nodeId") Long nodeId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    @Query("""
            select count(t) from TelemetryReading t
            where t.node.id = :nodeId
              and t.timestampReceived > :from
              and t.timestampReceived <= :to
            """)
    long countInRange(@Param("nodeId") Long nodeId,
                      @Param("from") Instant from,
                      @Param("to") Instant to);

    /** Ultima lectura cuyo valor RAW difiere del actual: base de la deteccion de sensor congelado. */
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
              and t.humedadSueloRaw is not null
              and t.humedadSueloRaw <> :raw
            order by t.timestampReceived desc
            """)
    List<TelemetryReading> findLastWithDifferentRaw(@Param("nodeId") Long nodeId,
                                                    @Param("raw") Integer raw,
                                                    Pageable pageable);

    /** Lecturas mas antiguas del nodo (usar con PageRequest.of(0, 1) para obtener la primera). */
    @Query("""
            select t from TelemetryReading t
            where t.node.id = :nodeId
            order by t.timestampReceived asc
            """)
    List<TelemetryReading> findOldest(@Param("nodeId") Long nodeId, Pageable pageable);
}
