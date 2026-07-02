package com.fleetops.asignaciones.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "asignaciones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conductor_id", nullable = false)
    private Conductor conductor;

    @Column(name = "vehiculo_id")
    private UUID vehiculoId;

    @Column(name = "tipo_vehiculo", nullable = false)
    private String tipoVehiculo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "kilometros", nullable = false)
    private Integer kilometros;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @PrePersist
    protected void onCreate() {
        this.creadaEn = LocalDateTime.now();
    }

    public void asignarVehiculo(UUID vehiculoId) {
        if (vehiculoId == null) {
            throw new IllegalArgumentException("El vehiculoId no puede ser nulo.");
        }
        this.vehiculoId = vehiculoId;
    }
}
