# FleetOps — Microservicio de Asignaciones

Microservicio responsable de coordinar la asignación de vehículos y conductores dentro de la plataforma FleetOps.

Implementado con **Arquitectura Hexagonal (Ports & Adapters)**, **SAGA Coreografiado** y comunicación asíncrona mediante **Apache Kafka**. No existe un orquestador central: cada microservicio reacciona de forma autónoma a los eventos que recibe.

---

## Decisiones arquitectónicas clave

| Decisión | Elección | Justificación |
|----------|----------|---------------|
| Estilo arquitectónico | Hexagonal | Desacoplamiento total entre dominio e infraestructura |
| Coordinación distribuida | SAGA Coreografiado | Sin orquestador central; cada servicio reacciona a eventos |
| Mensajería | Apache Kafka | Comunicación asíncrona desacoplada entre microservicios |
| Consistencia DB + Kafka | Kafka Transactions | Reemplaza el Outbox Pattern; atomicidad garantizada sin polling |
| Persistencia | PostgreSQL + Flyway | ACID local; migraciones versionadas |
| Seguridad | JWT + Spring Security | Stateless; compatible con arquitectura de microservicios |
| Build | Maven 3.9 + Java 21 | Stack LTS estable |

---

## Prerrequisitos

| Herramienta | Versión mínima | Verificar con |
|-------------|----------------|---------------|
| Docker Desktop | 26.x | `docker --version` |
| Docker Compose | v2.x | `docker compose version` |
| Java JDK | 21 LTS | `java --version` |
| Maven | 3.9.x | `mvn --version` |

---

## Quick Start

```bash
# 1. Clonar el repositorio
git clone https://github.com/fleetops/asignaciones.git
cd asignaciones

# 2. Configurar variables de entorno
cp .env.example .env
# Editar .env con valores reales antes de continuar

# 3. Levantar todos los servicios
docker compose up --build
```

La API queda disponible en `http://localhost:8080`
Swagger UI en `http://localhost:8080/swagger-ui.html`
Health check en `http://localhost:8080/actuator/health`

---

## Endpoints disponibles

### Crear una asignación
```bash
POST /asignaciones
Authorization: Bearer <token>
Content-Type: application/json

{
  "tipoVehiculo": "CAMION",
  "fechaInicio": "2026-07-01",
  "fechaFin": "2026-07-10",
  "emailContacto": "coordinador@fleetops.com"
}
```

Respuesta `202 Accepted`:
```json
{
  "idSaga": "550e8400-e29b-41d4-a716-446655440000",
  "idAsignacion": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "mensaje": "Asignación en proceso. Consulte el estado con el id de saga."
}
```

El `202` indica que el proceso continúa de forma asíncrona vía Kafka. Guarda el `idSaga`.

### Consultar estado de la SAGA
```bash
GET /asignaciones/saga/{idSaga}
Authorization: Bearer <token>
```

Respuesta posible:
```json
{ "idSaga": "...", "estado": "PENDIENTE_VEHICULO", "vehiculoId": null, "motivoFallo": null }
{ "idSaga": "...", "estado": "COMPLETADO",          "vehiculoId": "uuid-vehiculo", "motivoFallo": null }
{ "idSaga": "...", "estado": "FALLIDO",             "vehiculoId": null, "motivoFallo": "Sin vehículos disponibles" }
```

---

## Topics Kafka

| Topic | Quién publica | Quién consume | Cuándo |
|-------|--------------|---------------|--------|
| `fleetops.vehiculos.solicitar` | Asignaciones | Vehículos | Al crear una asignación |
| `fleetops.vehiculos.liberar` | Asignaciones | Vehículos | Al procesar una falla mecánica |
| `fleetops.asignaciones.vehiculo.confirmado` | Vehículos | Asignaciones | Cuando Vehículos asigna el vehículo |
| `fleetops.asignaciones.vehiculo.fallido` | Vehículos | Asignaciones | Cuando Vehículos no puede asignar |
| `fleetops.incidentes.falla.mecanica` | Incidentes | Asignaciones | Cuando hay una falla mecánica |
| `fleetops.asignaciones.completada` | Asignaciones | Otros servicios | Al completar exitosamente |
| `fleetops.asignaciones.fallida` | Asignaciones | Otros servicios | Al fallar la asignación |

---

## Flujo coreografiado — visión general

```
Flujo 1 — Asignación exitosa:
  POST /asignaciones
    → [Asignaciones] reserva conductor + publica VehiculoSolicitadoEvent
    → [Vehículos]    asigna vehículo  + publica VehiculoConfirmadoEvent
    → [Asignaciones] confirma asignación + publica AsignacionCompletadaEvent
    → GET /asignaciones/saga/{id} → estado: COMPLETADO

Flujo 2 — Vehículo no disponible (compensación):
  POST /asignaciones
    → [Asignaciones] reserva conductor + publica VehiculoSolicitadoEvent
    → [Vehículos]    sin stock         + publica VehiculoRechazadoEvent
    → [Asignaciones] libera conductor  + publica AsignacionFallidaEvent
    → GET /asignaciones/saga/{id} → estado: FALLIDO

Flujo 3 — Falla mecánica desde Incidentes:
  [Incidentes] detecta falla + publica FallaMecanicaEvent
    → [Asignaciones] libera conductor + publica VehiculoLiberadoEvent
    → [Vehículos]    libera vehículo  (autónomamente)
```

---

## Correr los tests

```bash
# Todos los tests unitarios
./mvnw test

# Un test específico
./mvnw test -Dtest=AsignacionServiceTest

# Generar reporte de cobertura JaCoCo
./mvnw clean verify

# Ver reporte en el navegador
open target/site/jacoco/index.html        # Mac
xdg-open target/site/jacoco/index.html    # Linux
start target/site/jacoco/index.html       # Windows
```

El reporte HTML de JaCoCo queda en `target/site/jacoco/index.html`.

La cobertura minima exigida actualmente es **80% de lineas** sobre la logica relevante de `domain` y `application`. El build falla durante `./mvnw clean verify` si no se cumple.

JaCoCo excluye de la metrica clases sin logica de negocio directa:

- clase principal de Spring Boot
- interfaces de puertos (`application/port`)
- enums y domain events simples
- DTOs y mensajes REST/Kafka
- configuraciones Spring, Swagger, Security y Kafka
- controladores REST
- repositorios JPA generados por Spring Data

El CI de GitHub Actions ejecuta `./mvnw clean verify` en cada push o pull request hacia `develop` o `main`. Esa validacion compila el proyecto, ejecuta los tests, genera el reporte JaCoCo y aplica el umbral de cobertura configurado.

---

## Estructura del proyecto

```
src/main/java/com/fleetops/asignaciones/
├── domain/           Núcleo puro: modelos, enums y domain events (sin dependencias externas)
├── application/      Puertos (contratos) + servicios que implementan la lógica de negocio
└── infrastructure/   Adaptadores: Kafka, JPA/PostgreSQL, REST controllers, configuración

src/main/resources/
├── application.yml           Configuración externalizada (usa variables de entorno)
└── db/migration/             Scripts SQL ejecutados por Flyway al arrancar

src/test/                     Tests unitarios con Mockito (sin BD real ni Kafka real)
```

Consulta `GUIA_PROYECTO.md` para la descripción detallada de cada archivo.

---

## Comandos útiles

```bash
# Ver logs de un servicio
docker compose logs -f asignaciones
docker compose logs -f kafka

# Detener todo
docker compose down

# Detener y borrar datos de la base de datos
docker compose down -v

# Correr solo la infraestructura (para desarrollo local)
docker compose up postgres zookeeper kafka
mvn spring-boot:run

# Publicar un evento Kafka manualmente (para pruebas)
docker exec -it fleetops-kafka bash
kafka-console-producer --bootstrap-server localhost:29092 \
  --topic fleetops.asignaciones.vehiculo.confirmado

# Ver mensajes en un topic
docker exec -it fleetops-kafka bash
kafka-console-consumer --bootstrap-server localhost:29092 \
  --topic fleetops.vehiculos.solicitar --from-beginning
```

---

## Limitaciones conocidas y próximos pasos

- **Contrato con Incidentes:** el `FallaMecanicaMessage` asume los campos `idIncidente`, `idVehiculo`, `idAsignacion` y `descripcion`. Debe coordinarse con el equipo de Incidentes.
- **Dead Letter Queue (DLQ):** mensajes que fallen todos los reintentos del consumer quedan sin procesar. Se recomienda configurar un topic DLQ en producción.
- **Vista de despliegue AWS EC2:** el SAD tiene esta sección pendiente; el `docker-compose.yml` cubre entornos locales y de CI.
- **Schema Registry:** para producción se recomienda Confluent Schema Registry con Avro para detectar roturas de contrato en tiempo de compilación.
