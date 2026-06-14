package com.fleetops.asignaciones.domain.enums;

/**
 * Estados posibles de una SAGA en el modelo coreografiado.
 * No existe un orquestador central: cada estado refleja
 * la última reacción del microservicio ante un evento recibido.
 */
public enum EstadoSaga {
    PENDIENTE_VEHICULO,    // Evento VehiculoSolicitado publicado, esperando respuesta
    PENDIENTE_LIBERACION,  // Compensación iniciada, esperando confirmación de liberación
    COMPLETADO,            // VehiculoAsignado recibido, asignación finalizada
    FALLIDO                // No se pudo completar tras recibir rechazo de Vehículos
}
