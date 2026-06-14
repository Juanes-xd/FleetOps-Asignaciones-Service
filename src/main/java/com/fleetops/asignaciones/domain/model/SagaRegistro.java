package com.fleetops.asignaciones.domain.model;

import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de trazabilidad del proceso SAGA coreografiado.
 * En coreografía no hay orquestador: este registro solo guarda
 * el estado actual y el historial para consulta y auditoría.
 * No contiene "siguienteAccion" ni lógica de polling — el flujo
 * avanza por reacción a eventos Kafka.
 */
@Entity
@Table(name = "saga_registros")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id")
    private Asignacion asignacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSaga estado;

    @Column(name = "vehiculo_id")
    private UUID vehiculoId;

    @Column(name = "motivo_fallo")
    private String motivoFallo;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "actualizada_en")
    private LocalDateTime actualizadaEn;

    @PrePersist
    protected void onCreate() {
        this.creadaEn = LocalDateTime.now();
        this.actualizadaEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadaEn = LocalDateTime.now();
    }

    public void marcarCompletada(UUID vehiculoId) {
        this.estado = EstadoSaga.COMPLETADO;
        this.vehiculoId = vehiculoId;
    }

    public void marcarFallida(String motivo) {
        this.estado = EstadoSaga.FALLIDO;
        this.motivoFallo = motivo;
    }

    public void marcarPendienteLiberacion() {
        this.estado = EstadoSaga.PENDIENTE_LIBERACION;
    }
}
