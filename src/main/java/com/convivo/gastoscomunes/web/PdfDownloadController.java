package com.convivo.gastoscomunes.web;

import com.convivo.gastoscomunes.service.PdfStorageService;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gastos/pdf")
public class PdfDownloadController {

    private final PdfStorageService pdfStorageService;

    public PdfDownloadController(PdfStorageService pdfStorageService) {
        this.pdfStorageService = pdfStorageService;
    }

    @GetMapping("/{ticketId}/descargar")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String ticketId) {
        Optional<byte[]> pdfOpt = pdfStorageService.obtenerPdf(ticketId);
        if (pdfOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] pdfBytes = pdfOpt.get();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename("gastos-comunes-" + ticketId + ".pdf")
                .build());
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
