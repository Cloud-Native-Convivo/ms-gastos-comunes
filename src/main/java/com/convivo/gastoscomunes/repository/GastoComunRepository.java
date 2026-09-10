package com.convivo.gastoscomunes.repository;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoComunRepository extends JpaRepository<GastoComun, Long> {

    Page<GastoComun> findByUnidadId(String unidadId, Pageable pageable);

    Optional<GastoComun> findByOrigenAndReferenciaExterna(OrigenGasto origen, String referenciaExterna);
}
