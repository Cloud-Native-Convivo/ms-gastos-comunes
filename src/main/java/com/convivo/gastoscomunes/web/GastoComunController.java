package com.convivo.gastoscomunes.web;

import com.convivo.gastoscomunes.domain.Pago;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.dto.GastoComunResponse;
import com.convivo.gastoscomunes.dto.PagoRequest;
import com.convivo.gastoscomunes.dto.PagoResponse;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import com.convivo.gastoscomunes.service.GastoComunService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de Gastos Comunes, consumida por el BFF (nunca directamente por
 * los frontends). {@code conserje} no tiene acceso a este dominio: solo
 * administrador/comité (gestión total) y propietario/residente (su propia
 * unidad, filtrado en {@link GastoComunService}).
 */
@RestController
@RequestMapping("/api/v1/gastos-comunes")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COMITE','PROPIETARIO','RESIDENTE')")
public class GastoComunController {

    private final GastoComunService service;
    private final UsuarioContexto usuarioContexto;

    public GastoComunController(GastoComunService service, UsuarioContexto usuarioContexto) {
        this.service = service;
        this.usuarioContexto = usuarioContexto;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COMITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public GastoComunResponse crear(@Valid @RequestBody GastoComunRequest request) {
        return GastoComunResponse.desde(service.crear(request));
    }

    @GetMapping
    public ResponseEntity<Page<GastoComunResponse>> listar(
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GastoComunResponse> pagina = service.listar(usuarioContexto, pageable).map(GastoComunResponse::desde);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/unidad/{unidadId}")
    public ResponseEntity<Page<GastoComunResponse>> listarPorUnidad(
            @PathVariable String unidadId,
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GastoComunResponse> pagina =
                service.listarPorUnidad(unidadId, usuarioContexto, pageable).map(GastoComunResponse::desde);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    public GastoComunResponse obtener(@PathVariable Long id) {
        return GastoComunResponse.desde(service.obtener(id, usuarioContexto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COMITE')")
    public GastoComunResponse actualizar(@PathVariable Long id, @Valid @RequestBody GastoComunRequest request) {
        return GastoComunResponse.desde(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COMITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }

    @PostMapping("/{id}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponse registrarPago(@PathVariable Long id, @Valid @RequestBody PagoRequest request) {
        Pago pago = service.registrarPago(id, request, usuarioContexto);
        return PagoResponse.desde(pago);
    }

    @GetMapping("/{id}/pagos")
    public ResponseEntity<Page<PagoResponse>> listarPagos(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "fechaPago", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PagoResponse> pagina = service.listarPagos(id, usuarioContexto, pageable).map(PagoResponse::desde);
        return ResponseEntity.ok(pagina);
    }
}
