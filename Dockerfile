# ── Stage 1: Build (Aquí necesitamos Maven para compilar) ──────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cachear dependencias antes de copiar el código fuente
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

# Copiar código y compilar
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B && \
    mv target/asignaciones-*.jar target/app.jar

# ── Stage 2: Runtime (Aquí dejamos una imagen limpia y ligera solo con Java) ──
FROM eclipse-temurin:21-jdk-alpine

# Usuario no-root por seguridad
RUN addgroup -S fleetops && adduser -S fleetops -G fleetops

WORKDIR /app

# Copiamos el JAR final generado en la etapa anterior
COPY --from=builder /build/target/app.jar app.jar

RUN chown fleetops:fleetops app.jar

USER fleetops

EXPOSE 8020

ENTRYPOINT ["java", "-jar", "app.jar"]