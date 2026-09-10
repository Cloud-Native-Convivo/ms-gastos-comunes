package com.convivo.gastoscomunes.repository;

import com.convivo.gastoscomunes.domain.Pago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Page<Pago> findByGastoComunId(Long gastoComunId, Pageable pageable);
}
