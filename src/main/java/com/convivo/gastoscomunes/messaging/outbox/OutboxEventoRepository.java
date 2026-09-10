package com.convivo.gastoscomunes.messaging.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, Long> {

    List<OutboxEvento> findByEstadoOrderByFechaCreacionAsc(EstadoOutbox estado, Pageable pageable);
}
