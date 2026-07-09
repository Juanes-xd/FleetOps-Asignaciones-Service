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
| Seguridad | JWT RS256 (Spring Security) | Stateless; el API Gateway firma con su llave privada, este microservicio solo valida con la llave pública — nunca conoce el secreto de firma |
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

El `<token>` debe estar firmado en RS256 por el API Gateway (o, en local, por la llave privada de prueba correspondiente a `secrets/jwt_public.pem` — ver `GUIA_PROYECTO.md`).

```bash
POST /asignaciones
Authorization: Bearer <token>
Content-Type: application/json

{
  "tipoVehiculo": "CAMION",
  "fechaInicio": "2026-07-01",
  "fechaFin": "2026-07-10",
  "kilometros": "100"
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
| `fleetops.vehiculos.solicitar` | Asignaciones | Vehículos | Al crear una asignación o reintentar una reasignación mecánica |
| `fleetops.vehiculos.liberar` | Asignaciones | Vehículos | Al procesar un incidente mecánico grave |
| `fleetops.asignaciones.vehiculo.confirmado` | Vehículos | Asignaciones | Cuando Vehículos asigna el vehículo |
| `fleetops.asignaciones.vehiculo.fallido` | Vehículos | Asignaciones | Cuando Vehículos no puede asignar |
| `fleetops.asignaciones.completada` | Asignaciones | Otros servicios | Al completar exitosamente |
| `fleetops.asignaciones.fallida` | Asignaciones | Otros servicios | Al fallar la asignación |

## Cola SQS (Incidentes → Asignaciones)

Incidentes y Asignaciones corren en instancias AWS separadas, por lo que esta integración
ya no usa Kafka: Incidentes publica en un tópico SNS que hace fan-out hacia una cola SQS
que este servicio consume.

| Recurso | Quién publica | Quién consume | Cuándo |
|---------|--------------|---------------|--------|
| SNS `fleetops-incidentes-falla-mecanica` → SQS `fleetops-asignaciones-incidentes-falla-mecanica` | Incidentes | Asignaciones | Cuando llega un incidente grave mecánico o humano |

Como la cola está suscrita al tópico SNS (y no recibe mensajes directos), el Body de cada
mensaje SQS es el **sobre de notificación de SNS**, no el payload real. `SqsIncidentesConsumer`
deserializa el sobre, toma el campo `Message` (un JSON serializado como string) y lo vuelve a
deserializar para obtener el `FallaMecanicaMessage` real — dos pasadas de JSON, no una.

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

Flujo 3 — Incidente mecánico grave:
  [Incidentes] publica incidente MECANICO/GRAVE
    → [Asignaciones] localiza la asignación por vehicle_id
    → [Asignaciones] libera el vehículo actual y marca la SAGA como PENDIENTE_LIBERACION
    → [Asignaciones] publica VehiculoLiberadoEvent + VehiculoSolicitadoEvent
    → [Vehículos]    libera el vehículo y luego asigna uno nuevo

Flujo 4 — Incidente humano grave:
  [Incidentes] publica incidente HUMANO/GRAVE
    → [Asignaciones] localiza la asignación por driver_id
    → [Asignaciones] libera el conductor afectado
    → [Asignaciones] busca otro conductor disponible del mismo tipo de vehículo
    → [Asignaciones] actualiza la asignación con el nuevo conductor
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

## Monitoreo con Prometheus y Grafana

Al levantar el entorno con Docker Compose quedan disponibles:

- Microservicio: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Métricas Prometheus: `http://localhost:8080/actuator/prometheus`
- Prometheus: `http://localhost:9090`
- Prometheus targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000`

Credenciales locales de Grafana:

- Usuario: `admin`
- Contraseña: `admin`

Comandos de prueba en PowerShell:

```powershell
docker compose config
docker compose up -d --build
docker compose ps
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/prometheus
```

Evidencias:

- `docker compose ps`
- `/actuator/health` en `UP`
- `/actuator/prometheus` mostrando métricas
- Prometheus target `fleetops-asignaciones` en estado `UP`
- Grafana con datasource Prometheus conectado
- Dashboard básico con métricas de CPU, memoria, peticiones y errores

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

## Cómo probar el nuevo flujo con Incidentes

Primero levanta la infraestructura y el microservicio:

```powershell
docker compose up -d --build
```

Luego publica un incidente grave mecánico en el tópico SNS local (LocalStack simula el
mismo fan-out SNS -> SQS que usa Incidentes en AWS real; `aws sns publish` envuelve el
mensaje automáticamente en el sobre de notificación que `SqsIncidentesConsumer` espera):

```powershell
docker exec -it fleetops-localstack awslocal sns publish `
  --topic-arn arn:aws:sns:us-east-1:000000000000:queue_assignations `
  --message '{"incident_id":"INC-MEC-GRV-20260621-a3f9","vehicle_id":"11111111-1111-1111-1111-111211111111","description":"Falla grave de motor","driver_id":"11111111-1111-1111-1111-111111111111","incident_type":"MECANICO","severity":"GRAVE","event_date":"2026-07-02T10:15:00Z"}'
```

En ese caso debes ver en los logs que Asignaciones:

1. encuentra la asignación por `vehicle_id`
2. libera el vehículo actual
3. publica `VehiculoLiberadoEvent`
4. publica `VehiculoSolicitadoEvent` para pedir otro vehículo

Para un incidente grave humano, publica un payload similar cambiando `incident_type` a `HUMANO` y usando el `driver_id` real del conductor afectado:

```powershell
docker exec -it fleetops-localstack awslocal sns publish `
  --topic-arn arn:aws:sns:us-east-1:000000000000:fleetops-incidentes-falla-mecanica `
  --message '{"incident_id":"44444444-4444-4444-4444-444444444444","vehicle_id":"11111111-1111-1111-1111-111211111111","description":"Conductor incapacitado","driver_id":"11111111-1111-1111-1111-111111111111","incident_type":"HUMANO","severity":"GRAVE","event_date":"2026-07-02T10:20:00Z"}'
```

Ese flujo debe:

1. localizar la asignación por `driver_id`
2. liberar el conductor afectado
3. reservar otro conductor disponible del mismo tipo de vehículo
4. actualizar la asignación existente con el nuevo conductor

Para validar rápido, revisa los logs del servicio `asignaciones`:

```powershell
docker compose logs -f asignaciones
```

Si quieres ejecutar solo la suite impactada:

```powershell
./mvnw test -Dtest=SqsIncidentesConsumerTest,ReasignacionServiceTest
```

## Limitaciones conocidas y próximos pasos

- **Contrato con Incidentes:** el `FallaMecanicaMessage` ahora espera `incident_id`, `vehicle_id`, `description`, `driver_id`, `incident_type`, `Severity` y `event_date`. Si el productor usa `event_date`, el consumidor también lo acepta por alias.
- **Reasignación humana:** el microservicio reasigna el conductor buscando otro disponible del mismo tipo de vehículo; si no existe, el mensaje se reintenta por Kafka.
- **Dead Letter Queue (DLQ):** mensajes que fallen todos los reintentos del consumer quedan sin procesar. Se recomienda configurar un topic DLQ en producción.
- **Vista de despliegue AWS EC2:** el SAD tiene esta sección pendiente; el `docker-compose.yml` cubre entornos locales y de CI.
- **Schema Registry:** para producción se recomienda Confluent Schema Registry con Avro para detectar roturas de contrato en tiempo de compilación.
