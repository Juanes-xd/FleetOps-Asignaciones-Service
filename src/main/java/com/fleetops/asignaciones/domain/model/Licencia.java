package com.fleetops.asignaciones.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "licencias")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Licencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conductor_id", nullable = false)
    private Conductor conductor;

    @Column(nullable = false)
    private String categoria;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    public boolean estaVigente() {
        return LocalDate.now().isBefore(fechaVencimiento);
    }
}
