package com.convivo.gastoscomunes.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SolicitudJobRepository extends JpaRepository<SolicitudJob, String> {
}
