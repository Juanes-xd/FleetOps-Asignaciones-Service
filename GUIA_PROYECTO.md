# Guía completa del proyecto — FleetOps Asignaciones

Esta guía explica desde cero cómo correr el proyecto, cómo probarlo y qué hace cada archivo. No asume conocimiento previo del código.

---

## Tabla de contenido

1. [¿Qué hace este microservicio?](#1-qué-hace-este-microservicio)
2. [¿Qué necesitas instalar?](#2-qué-necesitas-instalar)
3. [Configuración inicial](#3-configuración-inicial)
4. [Cómo correr el proyecto](#4-cómo-correr-el-proyecto)
5. [Cómo probar que funciona](#5-cómo-probar-que-funciona)
6. [Cómo correr los tests](#6-cómo-correr-los-tests)
7. [Flujo completo de la aplicación](#7-flujo-completo-de-la-aplicación)
8. [Qué hace cada carpeta](#8-qué-hace-cada-carpeta)
9. [Qué hace cada archivo](#9-qué-hace-cada-archivo)
10. [Preguntas frecuentes](#10-preguntas-frecuentes)

---

## 1. ¿Qué hace este microservicio?

Este microservicio gestiona la **asignación de vehículos y conductores** dentro de la plataforma FleetOps.

Cuando alguien solicita una asignación, el microservicio reserva un conductor y le comunica a Vehículos que necesita un vehículo enviando un **evento Kafka**. Vehículos procesa eso de forma autónoma y responde con otro evento — ya sea confirmando el vehículo o rechazando la solicitud. Asignaciones escucha esa respuesta y actúa: si es confirmación, completa la asignación; si es rechazo, libera al conductor y registra el fallo.

También escucha al microservicio de Incidentes. Si reporta una falla mecánica, Asignaciones libera al conductor y le pide a Vehículos que libere el vehículo afectado.

Lo importante: **nadie coordina a nadie**. Cada microservicio reacciona a los eventos que recibe de forma independiente. A esto se le llama **SAGA Coreografiado**.

---

## 2. ¿Qué necesitas instalar?

| Herramienta | Versión mínima | Para qué |
|-------------|----------------|----------|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | 26.x | Contenedores de PostgreSQL y Kafka |
| [Docker Compose](https://docs.docker.com/compose/) | v2.x | Orquestar todos los servicios |
| [Java JDK](https://adoptium.net/) | 21 LTS | Compilar y correr el código localmente |
| [Maven](https://maven.apache.org/download.cgi) | 3.9.x | Gestionar dependencias y compilar |

Verificar instalación:

```bash
docker --version
docker compose version
java --version
mvn --version
```

---

## 3. Configuración inicial

### Paso 1 — Clonar el repositorio

```bash
git clone https://github.com/fleetops/asignaciones.git
cd asignaciones
```

### Paso 2 — Crear el archivo de variables de entorno

El proyecto no tiene contraseñas ni configuración sensible escrita en el código. Todo se lee desde un archivo `.env`. Hay una plantilla lista:

```bash
cp .env.example .env
```

### Paso 3 — Editar el `.env`

Abre `.env` y completa los valores marcados:

```env
# Base de datos
DB_NAME=fleetops_asignaciones
DB_USERNAME=fleetops_user
DB_PASSWORD=una_contrasena_segura_aqui

# Servidor
SERVER_PORT=8080

# JWT — mínimo 32 caracteres
JWT_SECRET=mi_secreto_super_seguro_de_32_caracteres_minimo

# Kafka
KAFKA_EXTERNAL_PORT=9092
KAFKA_CONSUMER_GROUP_ID=asignaciones-group

# Transacciones Kafka — garantiza atomicidad entre DB y Kafka
# Debe ser único por instancia del microservicio
KAFKA_PRODUCER_TRANSACTION_ID_PREFIX=asignaciones-tx

# Topics (puedes dejar los valores por defecto)
KAFKA_TOPIC_VEHICULOS_SOLICITAR=fleetops.vehiculos.solicitar
KAFKA_TOPIC_VEHICULOS_LIBERAR=fleetops.vehiculos.liberar
KAFKA_TOPIC_VEHICULOS_CONFIRMADO=fleetops.asignaciones.vehiculo.confirmado
KAFKA_TOPIC_VEHICULOS_FALLIDO=fleetops.asignaciones.vehiculo.fallido
KAFKA_TOPIC_INCIDENTES_FALLA=fleetops.incidentes.falla.mecanica
KAFKA_TOPIC_ASIGNACION_COMPLETADA=fleetops.asignaciones.completada
KAFKA_TOPIC_ASIGNACION_FALLIDA=fleetops.asignaciones.fallida
```

> El archivo `.env` está en `.gitignore` y nunca se sube al repositorio. Solo `.env.example` (sin valores reales) se versiona.

---

## 4. Cómo correr el proyecto

### Opción A — Todo con Docker (recomendado)

Levanta PostgreSQL, Zookeeper, Kafka y el microservicio de una vez:

```bash
docker compose up --build
```

La primera vez descarga imágenes y compila el JAR — tarda unos minutos. Las siguientes veces es mucho más rápido.

```bash
# Detener todo
docker compose down

# Detener y borrar los datos de PostgreSQL
docker compose down -v

# Ver logs de un servicio específico
docker compose logs -f asignaciones
docker compose logs -f kafka
docker compose logs -f postgres
```

### Opción B — Infraestructura en Docker, código en local

Útil cuando estás desarrollando y quieres ver cambios rápido sin reconstruir el contenedor:

```bash
# Terminal 1 — levantar solo la infraestructura
docker compose up postgres zookeeper kafka

# Terminal 2 — correr el microservicio con Maven
mvn spring-boot:run
```

### Verificar que está corriendo

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health check:** http://localhost:8080/actuator/health
- **Métricas:** http://localhost:8080/actuator/metrics

---

## 5. Cómo probar que funciona

### Obtener un token JWT

Los endpoints están protegidos. Para pruebas, genera un token en [jwt.io](https://jwt.io) usando el mismo `JWT_SECRET` que pusiste en el `.env`.

### Crear una asignación

```bash
curl -X POST http://localhost:8080/asignaciones \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TU_TOKEN_JWT_AQUI" \
  -d '{
    "tipoVehiculo": "CAMION",
    "fechaInicio": "2026-07-01",
    "fechaFin": "2026-07-10",
    "emailContacto": "coordinador@fleetops.com"
  }'
```

**Respuesta esperada (202 Accepted):**

```json
{
  "idSaga": "550e8400-e29b-41d4-a716-446655440000",
  "idAsignacion": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "mensaje": "Asignación en proceso. Consulte el estado con el id de saga."
}
```

Guarda el `idSaga` — lo necesitas para consultar el estado.

### Consultar el estado de la SAGA

```bash
curl http://localhost:8080/asignaciones/saga/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer TU_TOKEN_JWT_AQUI"
```

**Posibles respuestas:**

```json
// Esperando respuesta de Vehículos
{ "idSaga": "...", "estado": "PENDIENTE_VEHICULO", "vehiculoId": null, "motivoFallo": null }

// Asignación completada exitosamente
{ "idSaga": "...", "estado": "COMPLETADO", "vehiculoId": "uuid-del-vehiculo", "motivoFallo": null }

// Asignación fallida con compensación ejecutada
{ "idSaga": "...", "estado": "FALLIDO", "vehiculoId": null, "motivoFallo": "Sin vehículos disponibles" }

// Compensación por falla mecánica en progreso
{ "idSaga": "...", "estado": "PENDIENTE_LIBERACION", "vehiculoId": null, "motivoFallo": null }
```

### Simular eventos Kafka manualmente

Para probar el flujo coreografiado sin tener los otros microservicios levantados, puedes publicar eventos directamente:

```bash
# Entrar al contenedor de Kafka
docker exec -it fleetops-kafka bash

# Simular que Vehículos confirmó la asignación
kafka-console-producer \
  --bootstrap-server localhost:29092 \
  --topic fleetops.asignaciones.vehiculo.confirmado

# Pegar este JSON y presionar Enter:
{"idAsignacion":"UUID_DE_TU_ASIGNACION","idVehiculo":"UUID_DEL_VEHICULO"}
#Ejemplo para las longitudes de los UUIDs:
{"idAsignacion":"dc912c19-25d1-4d29-be08-0f03f2459f45","idVehiculo":"11111111-1111-1111-1111-111211111111"}
# Simular que Vehículos rechazó la solicitud
kafka-console-producer \
  --bootstrap-server localhost:29092 \
  --topic fleetops.asignaciones.vehiculo.fallido

# Pegar este JSON:
{"idAsignacion":"UUID_DE_TU_ASIGNACION","motivo":"Sin vehículos disponibles del tipo CAMION"}

# Simular una falla mecánica desde Incidentes
kafka-console-producer \
  --bootstrap-server localhost:29092 \
  --topic fleetops.incidentes.falla.mecanica

# Pegar este JSON:
{"idIncidente":"UUID","idVehiculo":"UUID","idAsignacion":"UUID","descripcion":"Motor averiado"}

# Ver todos los mensajes que Asignaciones está publicando
kafka-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic fleetops.vehiculos.solicitar \
  --from-beginning
  
#Ver todas las solictudes completadas
kafka-console-consumer \  
  --bootstrap-server localhost:29092 \ 
  --topic fleetops.asignaciones.completada \
  --from-beginning 
```

---

## 6. Cómo correr los tests

```bash
# Todos los tests unitarios
./mvnw test

# Un test específico
./mvnw test -Dtest=AsignacionServiceTest
./mvnw test -Dtest=VehiculoAsignadoServiceTest
./mvnw test -Dtest=KafkaVehiculosConsumerTest

# Generar reporte de cobertura JaCoCo
./mvnw clean verify

# Abrir el reporte en el navegador
open target/site/jacoco/index.html        # Mac
xdg-open target/site/jacoco/index.html    # Linux
start target/site/jacoco/index.html       # Windows
```

El reporte HTML de JaCoCo queda en `target/site/jacoco/index.html`.

El proyecto esta configurado para **fallar si la cobertura de la logica relevante de `domain` y `application` es menor al 80%**. Esto esta definido en el `pom.xml` con el plugin de JaCoCo.

JaCoCo excluye de la metrica clases sin logica de negocio directa: clase principal de Spring Boot, interfaces de puertos, enums y domain events simples, DTOs y mensajes REST/Kafka, configuraciones Spring/Swagger/Security/Kafka, controladores REST y repositorios JPA generados por Spring Data.

El CI de GitHub Actions ejecuta `./mvnw clean verify` en cada push o pull request hacia `develop` o `main`. Esa validacion compila el proyecto, ejecuta los tests, genera el reporte JaCoCo y aplica el umbral de cobertura configurado.

Ningún test conecta a bases de datos reales ni a Kafka real — todo se simula con Mockito.

---

## 7. Flujo completo de la aplicación

### Flujo 1 — Asignación exitosa (camino feliz)

```
[Admin] POST /asignaciones
    │
    ▼
[AsignacionController]              driving adapter REST
    │ llama puerto de entrada
    ▼
[CrearAsignacionUseCase]            interface (puerto in)
    │ implementado por
    ▼
[AsignacionService]
    │ 1. busca conductor disponible por tipo de vehículo
    │ 2. conductor.reservar()       → estado RESERVADO
    │ 3. guarda Asignacion en DB
    │ 4. guarda SagaRegistro        → estado PENDIENTE_VEHICULO
    │ 5. publica VehiculoSolicitadoEvent en Kafka
    │    (misma transacción Kafka — si falla, hace rollback en DB también)
    ▼
[Cliente recibe 202 Accepted + idSaga]

    ↓ Vehículos consume VehiculoSolicitadoEvent y procesa
    ↓ Vehículos publica VehiculoConfirmadoEvent

[KafkaVehiculosConsumer]            driving adapter Kafka
    │ recibe VehiculoConfirmadoMessage
    │ llama puerto de entrada
    ▼
[ProcesarVehiculoAsignadoUseCase]   interface (puerto in)
    │ implementado por
    ▼
[VehiculoAsignadoService]
    │ 1. actualiza asignacion.vehiculoId
    │ 2. saga.marcarCompletada(vehiculoId)
    │ 3. publica AsignacionCompletadaEvent

[Admin consulta] GET /asignaciones/saga/{id} → estado: COMPLETADO
```

### Flujo 2 — Vehículo no disponible (compensación coreografiada)

```
[Vehículos] no tiene stock → publica VehiculoRechazadoEvent

[KafkaVehiculosConsumer]            driving adapter Kafka
    │ recibe VehiculoRechazadoMessage
    ▼
[ProcesarVehiculoRechazadoUseCase]  interface (puerto in)
    │ implementado por
    ▼
[VehiculoRechazadoService]          — compensación —
    │ 1. conductor.liberar()         → estado DISPONIBLE
    │ 2. saga.marcarFallida(motivo)  → estado FALLIDO
    │ 3. publica AsignacionFallidaEvent

[Admin consulta] GET /asignaciones/saga/{id} → estado: FALLIDO
```

### Flujo 3 — Falla mecánica reportada por Incidentes

```
[Incidentes] detecta falla → publica FallaMecanicaEvent

[KafkaIncidentesConsumer]           driving adapter Kafka
    │ convierte FallaMecanicaMessage → FallaMecanicaRecibidaEvent (domain event)
    ▼
[ProcesarFallaMecanicaUseCase]      interface (puerto in)
    │ implementado por
    ▼
[ReasignacionService]
    │ 1. conductor.liberar()          → estado DISPONIBLE
    │ 2. saga.marcarPendienteLiberacion() → estado PENDIENTE_LIBERACION
    │ 3. publica VehiculoLiberadoEvent → Vehículos lo consume y libera el vehículo
```

---

## 8. Qué hace cada carpeta

```
fleetops-asignaciones/
│
├── src/main/java/com/fleetops/asignaciones/
│   │
│   ├── domain/           El corazón del sistema. No importa nada de Spring,
│   │                     Kafka ni JPA. Solo Java puro con lógica de negocio.
│   │                     ├── model/    Entidades con comportamiento
│   │                     ├── enums/    Estados posibles
│   │                     └── event/    Hechos del dominio (POJOs puros)
│   │
│   ├── application/      Coordina el flujo. Define qué operaciones
│   │                     existen (puertos) y cómo se ejecutan (servicios).
│   │                     ├── port/in/   Contratos de entrada (lo que ofrecemos)
│   │                     ├── port/out/  Contratos de salida (lo que necesitamos)
│   │                     └── service/   Implementaciones de los use cases
│   │
│   └── infrastructure/   Todo lo concreto y reemplazable:
│                         cómo se guarda, cómo se envía, cómo se expone.
│                         ├── persistence/  Adaptadores JPA/PostgreSQL
│                         ├── messaging/    Adaptadores Kafka (consumers + publisher)
│                         ├── web/          Controllers REST + DTOs HTTP
│                         └── config/       Spring Security, Swagger
│
├── src/main/resources/
│   ├── application.yml        Configuración (todo usa ${VARIABLE})
│   └── db/migration/          Scripts SQL versionados con Flyway
│
├── src/test/                  Tests unitarios con Mockito
│
├── docker-compose.yml         PostgreSQL + Zookeeper + Kafka + Microservicio
├── Dockerfile                 Imagen del microservicio (multi-stage, non-root)
├── pom.xml                    Dependencias Maven
├── .env.example               Plantilla de variables de entorno
└── .gitignore                 Excluye .env, target/, logs/
```

---

## 9. Qué hace cada archivo

### Raíz del proyecto

| Archivo | Qué hace |
|---------|----------|
| `pom.xml` | Define todas las dependencias (Spring Boot 3.3, Kafka, JPA, Flyway, JWT, Swagger, JaCoCo) y la configuracion de Maven. Incluye el plugin de JaCoCo configurado para exigir 80% de cobertura en la logica relevante de `domain` y `application`, excluyendo clases sin logica de negocio directa. |
| `docker-compose.yml` | Describe los cuatro servicios: Zookeeper (requerido por Kafka), Kafka con soporte para transacciones, PostgreSQL con volumen persistente, y el microservicio de Asignaciones. Todos con healthchecks y red interna `fleetops-net`. |
| `Dockerfile` | Construcción en dos etapas: primera etapa compila con Maven y produce el JAR; segunda etapa copia solo el JAR a una imagen JRE liviana y lo corre como usuario `fleetops` (no-root). |
| `.env.example` | Plantilla con las 14 variables que necesita el proyecto, cada una con un comentario explicativo. Copiar a `.env` y completar con valores reales. |
| `.gitignore` | Excluye `.env`, `target/`, `*.log` y archivos de IDE del control de versiones. |

---

### `AsignacionesApplication.java`

Punto de entrada de la aplicación. Contiene el método `main()` que arranca Spring Boot. No tiene `@EnableScheduling` porque en el modelo coreografiado no hay tareas programadas — el flujo avanza por reacción a eventos Kafka.

---

### `domain/model/`

Entidades con lógica de negocio. No dependen de Spring ni de JPA (aunque usan anotaciones JPA para el mapeo, la lógica es independiente).

| Archivo | Qué hace |
|---------|----------|
| `Conductor.java` | Representa un conductor. Método `reservar()` cambia el estado a `RESERVADO` y valida que esté disponible antes; lanza `IllegalStateException` si no. Método `liberar()` lo devuelve a `DISPONIBLE`. La validación vive aquí, no en el servicio. |
| `Asignacion.java` | Representa la asignación entre conductor y vehículo. Tiene las fechas, tipo de vehículo y email de contacto. Método `asignarVehiculo(UUID)` valida que el ID no sea nulo antes de asignarlo. |
| `SagaRegistro.java` | Registra el estado del proceso SAGA para trazabilidad y auditoría. En el modelo coreografiado no tiene columnas de polling (`siguiente_accion`, `intentos`) — solo guarda en qué estado quedó el proceso. Métodos: `marcarCompletada(vehiculoId)`, `marcarFallida(motivo)`, `marcarPendienteLiberacion()`. |
| `Licencia.java` | Licencia de conducción de un conductor. Método `estaVigente()` compara la fecha de vencimiento con hoy. |

---

### `domain/enums/`

| Archivo | Qué hace |
|---------|----------|
| `EstadoConductor.java` | `DISPONIBLE`, `RESERVADO`, `INACTIVO`. Refleja en qué situación está el conductor. |
| `EstadoSaga.java` | `PENDIENTE_VEHICULO` (esperando confirmación), `PENDIENTE_LIBERACION` (compensación en progreso), `COMPLETADO`, `FALLIDO`. No existe `AccionSaga` — en coreografía no hay polling de acciones pendientes. |

---

### `domain/event/`

POJOs puros (Java records) que representan hechos del dominio. No saben nada de Kafka — son solo datos con nombre semántico.

| Archivo | Qué hace |
|---------|----------|
| `VehiculoSolicitadoEvent.java` | Se publica al crear una asignación. Le dice a Vehículos que necesita un vehículo de cierto tipo para ciertas fechas. Contiene `idSaga`, `idAsignacion`, `tipoVehiculo`, `fechaInicio`, `fechaFin`. |
| `VehiculoLiberadoEvent.java` | Se publica al procesar una falla mecánica. Le dice a Vehículos que libere un vehículo específico. Contiene `idSaga` e `idVehiculo`. |
| `AsignacionCompletadaEvent.java` | Se publica cuando la asignación termina exitosamente. Útil para auditoría y otros servicios que quieran reaccionar. Contiene los IDs de la saga, asignación, vehículo y conductor. |
| `AsignacionFallidaEvent.java` | Se publica cuando la asignación falla. Contiene `idSaga`, `idAsignacion` y el motivo. |
| `FallaMecanicaRecibidaEvent.java` | Representa una falla mecánica que llegó desde Incidentes. Es la traducción del mensaje Kafka al lenguaje del dominio (anti-corruption layer). Contiene `idIncidente`, `idVehiculo`, `idAsignacion`, `descripcion`. |

---

### `application/port/in/` — Contratos de entrada

Interfaces que definen qué puede hacer el microservicio. Los driving adapters (controllers REST y consumers Kafka) llaman a estas interfaces — nunca a las implementaciones directamente.

| Archivo | Qué hace |
|---------|----------|
| `CrearAsignacionUseCase.java` | Define la operación de crear una asignación. Incluye el `Command` (tipo vehículo, fechas, email) y el `Result` (idSaga, idAsignacion). |
| `ConsultarSagaUseCase.java` | Define la consulta del estado de una SAGA. Recibe un UUID y devuelve `Result` con estado, vehiculoId y motivoFallo. |
| `ProcesarVehiculoAsignadoUseCase.java` | Se activa cuando Vehículos confirma que asignó el vehículo. Recibe `idAsignacion` e `idVehiculo`. |
| `ProcesarVehiculoRechazadoUseCase.java` | Se activa cuando Vehículos no puede asignar el vehículo. Recibe `idAsignacion` y `motivo`. Desencadena la compensación. |
| `ProcesarFallaMecanicaUseCase.java` | Se activa cuando llega un evento de falla mecánica desde Incidentes. Recibe un `FallaMecanicaRecibidaEvent`. |

---

### `application/port/out/` — Contratos de salida

Interfaces que definen lo que el dominio necesita del exterior. Las implementaciones concretas (JPA, Kafka) viven en `infrastructure/` y se inyectan por Spring.

| Archivo | Qué hace |
|---------|----------|
| `AsignacionRepositoryPort.java` | `guardar(Asignacion)` y `buscarPorId(UUID)`. |
| `ConductorRepositoryPort.java` | `buscarDisponiblePorTipoVehiculo(String)` — la consulta clave del negocio —, `guardar(Conductor)` y `buscarPorId(UUID)`. |
| `SagaRepositoryPort.java` | `guardar(SagaRegistro)`, `buscarPorId(UUID)` y `buscarPorAsignacionId(UUID)`. Sin `buscarPendientesPorAccion` — ese método existía para el OutboxWorker que ya no existe. |
| `EventPublisherPort.java` | `publicar(topic, evento)`. El servicio no sabe si es Kafka, RabbitMQ u otra cosa. Solo conoce este contrato. |

---

### `application/service/` — Implementaciones de use cases

Cada servicio implementa **un solo use case**. Esto evita ambigüedades de tipos cuando varios use cases definen tipos internos con el mismo nombre (como `Result`).

| Archivo | Qué hace |
|---------|----------|
| `AsignacionService.java` | Implementa `CrearAsignacionUseCase`. Busca conductor, lo reserva, crea la `Asignacion`, crea el `SagaRegistro` con estado `PENDIENTE_VEHICULO` y publica `VehiculoSolicitadoEvent` directamente en Kafka — todo dentro de una transacción. |
| `ConsultarSagaService.java` | Implementa `ConsultarSagaUseCase`. Solo consulta el estado actual del `SagaRegistro`. Transacción de solo lectura. |
| `VehiculoAsignadoService.java` | Implementa `ProcesarVehiculoAsignadoUseCase`. Reacción coreografiada a la confirmación de Vehículos: actualiza la asignación con el `vehiculoId`, marca la SAGA como `COMPLETADO` y publica `AsignacionCompletadaEvent`. |
| `VehiculoRechazadoService.java` | Implementa `ProcesarVehiculoRechazadoUseCase`. Compensación coreografiada: libera el conductor (`DISPONIBLE`), marca la SAGA como `FALLIDO` con el motivo y publica `AsignacionFallidaEvent`. |
| `ReasignacionService.java` | Implementa `ProcesarFallaMecanicaUseCase`. Cuando Incidentes reporta una falla: libera el conductor, marca la SAGA como `PENDIENTE_LIBERACION` y publica `VehiculoLiberadoEvent` para que Vehículos libere el vehículo de forma autónoma. |

---

### `infrastructure/persistence/`

Adaptadores driven que implementan los puertos de repositorio usando Spring Data JPA.

| Archivo | Qué hace |
|---------|----------|
| `AsignacionPersistenceAdapter.java` | Implementa `AsignacionRepositoryPort`. Delega en `AsignacionJpaRepository`. |
| `ConductorPersistenceAdapter.java` | Implementa `ConductorRepositoryPort`. La consulta `buscarDisponiblePorTipoVehiculo()` llama a `findFirstByTipoVehiculoAndEstado()`, que Spring traduce automáticamente a un `SELECT ... WHERE tipo_vehiculo = ? AND estado = 'DISPONIBLE' LIMIT 1`. |
| `SagaPersistenceAdapter.java` | Implementa `SagaRepositoryPort`. Incluye `buscarPorAsignacionId()` que los servicios de reacción coreografiada usan para encontrar la SAGA correspondiente a una asignación. |
| `jpa/AsignacionJpaRepository.java` | Extiende `JpaRepository`. Spring genera el SQL automáticamente. |
| `jpa/ConductorJpaRepository.java` | Extiende `JpaRepository` y añade `findFirstByTipoVehiculoAndEstado()`. |
| `jpa/SagaJpaRepository.java` | Extiende `JpaRepository` y añade `findByAsignacionId()`. Sin query de polling — eso desapareció con el OutboxWorker. |

---

### `infrastructure/messaging/`

Todo el código relacionado con Kafka.

#### `publisher/`

| Archivo | Qué hace |
|---------|----------|
| `KafkaEventPublisherAdapter.java` | Implementa `EventPublisherPort`. Usa `KafkaTemplate` configurado con transacciones. Si la transacción de DB hace rollback por cualquier error, el mensaje Kafka también se descarta automáticamente — sin necesidad de OutboxWorker. |

#### `consumer/`

| Archivo | Qué hace |
|---------|----------|
| `KafkaVehiculosConsumer.java` | Driving adapter que escucha dos topics separados: `vehiculos-confirmado` llama a `ProcesarVehiculoAsignadoUseCase`; `vehiculos-fallido` llama a `ProcesarVehiculoRechazadoUseCase`. ACK manual: el offset solo avanza si el procesamiento fue exitoso. Si falla, Kafka reintenta automáticamente. |
| `KafkaIncidentesConsumer.java` | Driving adapter que escucha `incidentes.falla.mecanica`. Convierte el `FallaMecanicaMessage` (DTO de Kafka) al `FallaMecanicaRecibidaEvent` (domain event) y llama a `ProcesarFallaMecanicaUseCase`. ACK manual por la misma razón. |

#### `dto/`

Mensajes del broker — distintos a los DTOs HTTP para que un cambio en el contrato REST no rompa el contrato Kafka ni viceversa.

| Archivo | Qué hace |
|---------|----------|
| `FallaMecanicaMessage.java` | Mensaje que llega desde Incidentes. Campos: `idIncidente`, `idVehiculo`, `idAsignacion`, `descripcion`. |
| `VehiculoConfirmadoMessage.java` | Mensaje que llega desde Vehículos confirmando la asignación. Campos: `idAsignacion`, `idVehiculo`. |
| `VehiculoRechazadoMessage.java` | Mensaje que llega desde Vehículos rechazando la solicitud. Campos: `idAsignacion`, `motivo`. |

#### `config/`

| Archivo | Qué hace |
|---------|----------|
| `KafkaConfig.java` | Configura el producer con `TRANSACTIONAL_ID_CONFIG` (garantiza atomicidad DB + Kafka) e idempotencia activada. Configura el consumer con `ENABLE_AUTO_COMMIT=false` y ACK manual. El modo `EXACTLY_ONCE` del producer es lo que reemplaza el OutboxWorker. |

---

### `infrastructure/web/`

| Archivo | Qué hace |
|---------|----------|
| `controller/AsignacionController.java` | Driving adapter REST. Expone `POST /asignaciones`. Valida el request con Bean Validation (`@Valid`), mapea el DTO al `Command` del use case y retorna `202 Accepted`. |
| `controller/SagaController.java` | Driving adapter REST. Expone `GET /asignaciones/saga/{idSaga}`. Llama a `ConsultarSagaUseCase` y retorna el estado actual. |
| `dto/CrearAsignacionRequest.java` | DTO de entrada HTTP con validaciones: `@NotBlank`, `@Future`, `@Email`. Distintos al `Command` del use case. |
| `dto/AsignacionResponse.java` | DTO de salida: `idSaga`, `idAsignacion`, `mensaje`. |
| `dto/SagaEstadoResponse.java` | DTO de salida con el estado de la SAGA: `idSaga`, `estado`, `vehiculoId`, `motivoFallo`. |

---

### `infrastructure/config/`

| Archivo | Qué hace |
|---------|----------|
| `SecurityConfig.java` | Configura Spring Security 6. Protege `/asignaciones` y `/asignaciones/saga/**` con JWT. Deja públicos `/swagger-ui/**`, `/v3/api-docs/**` y `/actuator/**`. Sin sesiones (stateless). |
| `SwaggerConfig.java` | Configura el título y descripción que aparece en Swagger UI. |

---

### `src/main/resources/`

| Archivo | Qué hace |
|---------|----------|
| `application.yml` | Toda la configuración de la app. Cada valor sensible usa `${NOMBRE_VARIABLE}` para leerlo del `.env`. Configura DB, Kafka (incluyendo `transaction-id-prefix`), JWT, Actuator, Swagger y los 7 topics. |
| `V1__create_conductores.sql` | Crea la tabla `conductores` con `id`, `nombre`, `email`, `tipo_vehiculo` y `estado`. |
| `V2__create_asignaciones.sql` | Crea la tabla `asignaciones` con clave foránea a `conductores`. El campo `vehiculo_id` es nullable — se llena cuando Vehículos confirma. |
| `V3__create_licencias.sql` | Crea la tabla `licencias` con clave foránea a `conductores` y fecha de vencimiento. |
| `V4__create_saga_registros.sql` | Crea la tabla `saga_registros`. Sin columnas de outbox (`siguiente_accion`, `intentos`, `maximo_intentos`) porque el flujo ya no se maneja por polling sino por reacción a eventos. Incluye índice sobre `asignacion_id`. |

---

### `src/test/`

Todos los tests usan Mockito. Ninguno conecta a base de datos real ni a Kafka real.

| Archivo | Qué prueba |
|---------|------------|
| `AsignacionServiceTest.java` | Verifica que `ejecutar()` reserva el conductor, crea la asignación, la SAGA y **publica el evento Kafka directamente** (sin OutboxWorker). También verifica que no publica nada si no hay conductor disponible. |
| `ConsultarSagaServiceTest.java` | Verifica los cuatro estados posibles: `COMPLETADO`, `FALLIDO`, `PENDIENTE_VEHICULO` e inexistente. |
| `VehiculoAsignadoServiceTest.java` | Verifica la reacción coreografiada a la confirmación: asignación actualizada con `vehiculoId`, SAGA en `COMPLETADO`, evento `AsignacionCompletadaEvent` publicado. |
| `VehiculoRechazadoServiceTest.java` | Verifica la compensación coreografiada: conductor liberado (`DISPONIBLE`), SAGA en `FALLIDO` con motivo, evento `AsignacionFallidaEvent` publicado. |
| `ReasignacionServiceTest.java` | Verifica la reacción a falla mecánica: conductor liberado, SAGA en `PENDIENTE_LIBERACION`, `VehiculoLiberadoEvent` publicado para que Vehículos reaccione. |
| `KafkaVehiculosConsumerTest.java` | Verifica que el consumer delega correctamente según el topic: confirmación va a `ProcesarVehiculoAsignadoUseCase`, rechazo va a `ProcesarVehiculoRechazadoUseCase`. Verifica que el ACK solo se hace si el procesamiento fue exitoso. |
| `AsignacionControllerTest.java` | Verifica los endpoints HTTP con MockMvc: request válido retorna 202, email inválido retorna 400. |

---

## 10. Preguntas frecuentes

**¿Por qué el endpoint retorna 202 y no 201?**

Porque el proceso no termina cuando el controller responde. La asignación real del vehículo ocurre de forma asíncrona: Asignaciones publica un evento, Vehículos lo procesa y responde con otro evento, y Asignaciones reacciona. El 202 Accepted significa "recibí tu pedido, está en proceso". Para saber el resultado hay que consultar `/asignaciones/saga/{id}`.

**¿Qué es exactamente la coreografía y en qué se diferencia del orquestador?**

Con orquestador hay un director central (el `SagaOrchestrator`) que llama a cada servicio en orden y gestiona el flujo completo. Con coreografía no hay director: cada microservicio escucha los eventos que le interesan y reacciona de forma autónoma. Vehículos no recibe una orden de "ahora asigna el vehículo" — simplemente escucha el topic `fleetops.vehiculos.solicitar` y hace su trabajo. Asignaciones tampoco le dice a Vehículos "ya terminaste, ahora te comunico el resultado" — simplemente escucha la respuesta que Vehículos publica por su cuenta.

**¿Por qué no hay OutboxWorker si antes era necesario para la consistencia?**

El OutboxWorker resolvía el problema de "qué pasa si el servicio se cae después del commit de DB pero antes de publicar en Kafka". Ahora ese problema lo resuelven las **Kafka Transactions**: el producer está configurado como transaccional, lo que significa que la publicación en Kafka y el commit de DB son atómicos — si uno falla, el otro también se deshace. Esto elimina la necesidad del worker de polling.

**¿Por qué hay cinco servicios en `application/service/` en vez de uno?**

Cada servicio implementa exactamente un use case. Hay dos razones: primero, evita la ambigüedad de tipos — si un solo servicio implementa `CrearAsignacionUseCase` y `ConsultarSagaUseCase` al mismo tiempo, Java no sabe a cuál `Result` te refieres (ambas interfaces definen un tipo interno con ese nombre). Segundo, cada servicio tiene una sola responsabilidad, lo que lo hace más fácil de testear, entender y modificar.

**¿Cómo pruebo la coreografía si no tengo los microservicios de Vehículos e Incidentes?**

Usa el producer de consola de Kafka para publicar manualmente los eventos de respuesta. El microservicio de Asignaciones no sabe si el evento vino de un microservicio real o de una consola — solo lee el mensaje del topic. Los comandos exactos están en la sección 5 de esta guía.

**¿Qué pasa si Kafka no está disponible cuando se crea una asignación?**

Como la publicación del evento ocurre dentro de la misma transacción Kafka, si Kafka no está disponible la transacción completa hace rollback — incluyendo los cambios en PostgreSQL. El conductor vuelve a estar disponible y la asignación no se guarda. El cliente recibe un error 500. Esto es diferente al modelo anterior con OutboxWorker, donde el commit de DB sí ocurría y el worker reintentaba la publicación más tarde.
