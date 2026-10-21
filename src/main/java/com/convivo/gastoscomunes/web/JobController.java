package com.convivo.gastoscomunes.web;

import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.exception.RecursoNoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gastos/jobs")
public class JobController {

    private final SolicitudJobRepository solicitudJobRepository;
    private final ObjectMapper objectMapper;

    public JobController(SolicitudJobRepository solicitudJobRepository, ObjectMapper objectMapper) {
        this.solicitudJobRepository = solicitudJobRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<Map<String, Object>> obtenerJob(@PathVariable String ticketId) {
        SolicitudJob job = solicitudJobRepository.findById(ticketId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Job no encontrado con ticket " + ticketId));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ticket_id", job.getTicketId());
        resp.put("modulo", job.getModulo());
        resp.put("accion", job.getAccion());
        resp.put("estado", job.getEstado());
        resp.put("resultado", parseJsonOrRaw(job.getResultado()));
        resp.put("error", parseJsonOrRaw(job.getError()));
        resp.put("creado_en", job.getCreadoEn());
        resp.put("actualizado_en", job.getActualizadoEn());

        return ResponseEntity.ok(resp);
    }

    private Object parseJsonOrRaw(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(val);
        } catch (Exception e) {
            return val;
        }
    }
}
