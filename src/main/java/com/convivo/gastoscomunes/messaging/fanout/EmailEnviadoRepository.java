package com.convivo.gastoscomunes.messaging.fanout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEnviadoRepository extends JpaRepository<EmailEnviado, String> {}
