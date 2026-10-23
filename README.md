# FoodFlow

FoodFlow is a local-first food delivery microservices example built with Java 17, Spring Boot 3.3.5, Spring Cloud 2023.0.3, PostgreSQL, and Angular 17.

## Services

| Application | Port | Responsibility |
|---|---:|---|
| Eureka Server | 8761 | Service registry |
| API Gateway | 8080 | Public API routes |
| Auth Service | 8081 | Registration, login, and JWT |
| Catalog Service | 8082 | Restaurants, menu items, and locked stock reservations |
| Order Service | 8083 | Orders, idempotency, payment simulation, and order status |
| Delivery Service | 8084 | Delivery partner assignment and status |
| Angular frontend | 4200 | Customer and staff screens |

Each backend service has a standalone Maven build. PostgreSQL runs locally with Docker Compose and creates a separate database for each business service.

## Prerequisites

- JDK 17
- Maven 3.9+
- Node.js 20+ and npm
- Docker Compose (for PostgreSQL)

## Start locally

1. (Optional) Copy `.env.example` to `.env` and replace the local-only secrets. Compose reads `.env`; for backend processes, set matching `JWT_SECRET` and `INTERNAL_TOKEN` values in each PowerShell terminal if you changed the defaults. If you change the PostgreSQL user/password, update the corresponding service datasource properties too.

2. Start the service databases:

   ```powershell
   docker compose up -d postgres
   ```

3. In separate terminals, start the backend applications in this order:

   ```powershell
   cd eureka-server; mvn spring-boot:run
   cd api-gateway; mvn spring-boot:run
   cd auth-service; mvn spring-boot:run
   cd catalog-service; mvn spring-boot:run
   cd order-service; mvn spring-boot:run
   cd delivery-service; mvn spring-boot:run
   ```

4. Start the frontend:

   ```powershell
   cd frontend
   npm install
   npm start
   ```

   The app is available at `http://localhost:4200`; APIs are exposed at `http://localhost:8080/api`.

## Local API documentation

- Eureka dashboard: http://localhost:8761
- Gateway health: http://localhost:8080/actuator/health
- Auth Swagger UI: http://localhost:8081/swagger-ui.html
- Catalog Swagger UI: http://localhost:8082/swagger-ui.html
- Order Swagger UI: http://localhost:8083/swagger-ui.html
- Delivery Swagger UI: http://localhost:8084/swagger-ui.html
- Auth Swagger UI via Gateway: http://localhost:8080/auth-docs/swagger-ui.html
- Catalog Swagger UI via Gateway: http://localhost:8080/catalog-docs/swagger-ui.html
- Order Swagger UI via Gateway: http://localhost:8080/order-docs/swagger-ui.html
- Delivery Swagger UI via Gateway: http://localhost:8080/delivery-docs/swagger-ui.html

## Default local configuration

Each service has a development-only JWT fallback and the Catalog/Order/Delivery services have a development-only internal service token. Replace these values before using this project outside local development. No default administrator account is included.

Catalog Service seeds a sample menu owned by a placeholder account. Register an owner and use its ID from `/api/auth/me`, then assign ownership and promote roles with local-only SQL:

```powershell
docker exec -it foodflow-postgres psql -U foodflow -d auth_db -c "UPDATE user_accounts SET role='RESTAURANT_OWNER' WHERE email='owner@example.com';"
docker exec -it foodflow-postgres psql -U foodflow -d catalog_db -c "UPDATE restaurants SET owner_id='<user-uuid>' WHERE name='Sample Kitchen';"
```

To test administrator features, set the account's role to `ADMIN` in `auth_db` instead. Two demo delivery partner slots are seeded in `delivery_db` with IDs `10000000-0000-4000-8000-000000000001` and `10000000-0000-4000-8000-000000000002`. Register partner accounts, then set their roles and IDs to those seeded values in local-only SQL before signing in again:

```powershell
docker exec -it foodflow-postgres psql -U foodflow -d auth_db -c "UPDATE user_accounts SET id='10000000-0000-4000-8000-000000000001'::uuid, role='DELIVERY_PARTNER' WHERE email='driver1@example.com';"
docker exec -it foodflow-postgres psql -U foodflow -d auth_db -c "UPDATE user_accounts SET id='10000000-0000-4000-8000-000000000002'::uuid, role='DELIVERY_PARTNER' WHERE email='driver2@example.com';"
```

The catalog's stock reservation endpoint uses a PostgreSQL pessimistic write lock inside a transaction. Order creation uses a unique idempotency key and compensates a failed persistence attempt by releasing reserved stock. Payments are simulated as immediately confirmed when an order is created.

## Tests

Run a service's tests from its directory:

```powershell
mvn test
```

The Catalog Service includes a Testcontainers PostgreSQL integration test for concurrent last-item reservation. Docker must be available for that test.

## Architecture

```text
Angular (4200)
     |
 API Gateway (8080) ------ Eureka (8761)
     |       |       |
   Auth   Catalog   Order <----> Delivery
    8081    8082    8083         8084
     |       |       |             |
 auth_db catalog_db order_db   delivery_db
```

Service-owned databases are isolated; services communicate over HTTP rather than accessing one another's tables.
