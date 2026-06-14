package com.fleetops.asignaciones.domain.model;

import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "conductores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conductor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "tipo_vehiculo", nullable = false)
    private String tipoVehiculo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConductor estado;

    public boolean estaDisponible() {
        return EstadoConductor.DISPONIBLE.equals(this.estado);
    }

    public void reservar() {
        if (!estaDisponible()) {
            throw new IllegalStateException(
                "El conductor " + id + " no está disponible para reservar.");
        }
        this.estado = EstadoConductor.RESERVADO;
    }

    public void liberar() {
        this.estado = EstadoConductor.DISPONIBLE;
    }
}
