# Build stage: compile all 5 services in one go
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY . .
RUN ./gradlew :order-api:bootJar :order-validator:bootJar :inventory-service:bootJar :payment-service:bootJar :notification-service:bootJar --no-daemon

# Run stage: one image with all jars; each service overrides CMD in compose
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/order-api/build/libs/order-api-*.jar ./order-api.jar
COPY --from=build /build/order-validator/build/libs/order-validator-*.jar ./order-validator.jar
COPY --from=build /build/inventory-service/build/libs/inventory-service-*.jar ./inventory-service.jar
COPY --from=build /build/payment-service/build/libs/payment-service-*.jar ./payment-service.jar
COPY --from=build /build/notification-service/build/libs/notification-service-*.jar ./notification-service.jar
ENTRYPOINT ["java", "-jar"]
CMD ["order-api.jar"]
