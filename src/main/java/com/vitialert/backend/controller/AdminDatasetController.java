package com.vitialert.backend.controller;

import com.vitialert.backend.domain.Granularity;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.service.DatasetExportService;
import com.vitialert.backend.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Exportacion del dataset unificado para el trabajo de Ciencia de Datos en Python. */
@RestController
@RequestMapping("/api/admin/dataset")
@Tag(name = "Administracion - Dataset", description = "Exportacion del dataset para Ciencia de Datos")
public class AdminDatasetController {

    private final DatasetExportService datasetExportService;
    private final NodeService nodeService;

    public AdminDatasetController(DatasetExportService datasetExportService, NodeService nodeService) {
        this.datasetExportService = datasetExportService;
        this.nodeService = nodeService;
    }

    @Operation(summary = "Exporta el dataset agregado en CSV",
            description = "Lags, medias moviles y target t+24h se resuelven por timestamp real sobre la "
                    + "serie agregada. Un hueco de datos produce una celda vacia, no un cero ni un valor corrido.")
    @GetMapping(path = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam("nodeId") String nodeId,
                                         @RequestParam("from") String from,
                                         @RequestParam("to") String to,
                                         @RequestParam(value = "granularity", defaultValue = "HOURLY")
                                         Granularity granularity) {
        Node node = nodeService.requireByExternalId(nodeId);
        Instant fromInstant = RequestTimes.parse(from, "from");
        Instant toInstant = RequestTimes.parse(to, "to");

        String csv = datasetExportService.exportCsv(node, fromInstant, toInstant, granularity);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        String filename = "vitialert_dataset_node" + node.getExternalId() + "_"
                + granularity.name().toLowerCase() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
