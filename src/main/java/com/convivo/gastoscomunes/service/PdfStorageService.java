package com.convivo.gastoscomunes.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PdfStorageService {

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    public void guardarPdf(String ticketId, byte[] data) {
        if (ticketId != null && data != null) {
            storage.put(ticketId, data);
        }
    }

    public Optional<byte[]> obtenerPdf(String ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(ticketId));
    }

    public void eliminarPdf(String ticketId) {
        if (ticketId != null) {
            storage.remove(ticketId);
        }
    }
}
