# FoodFlow — Food Delivery Microservices

A locally runnable learning project using Java 17, Spring Boot, Spring Cloud, Angular 17, PostgreSQL, Eureka, API Gateway, OpenFeign, JWT, Swagger/OpenAPI, Lombok, JUnit 5 and Mockito.

## Components
- `eureka-server` (8761): service registry
- `api-gateway` (8080): routes `/api/**` requests and validates JWTs
- `auth-service` (8081): registration/login/JWT
- `catalog-service` (8082): restaurants/menu/inventory
- `order-service` (8083): order lifecycle and Feign catalog/coupon/delivery calls
- `delivery-service` (8084): assignments/status, Feign order calls
- `coupon-service` (8085): coupon validation, expiry, discount calculation and usage limits
- `review-service` (8086): reviews and ratings; only the owner of a delivered order can review it
- `frontend` (4200): Angular 17 UI; JWT stored in sessionStorage as requested

## Requirements
- JDK 17
- Maven 3.9+
- Node.js 18+ and npm
- Docker Desktop (recommended for PostgreSQL)
- Razorpay sandbox API credentials for online checkout

## Configure secrets
Set `JWT_SECRET` to a random value with at least 32 bytes before starting Auth Service and API Gateway. There is no built-in JWT secret fallback. For local Spring Tool Suite launches, Auth Service and API Gateway load JWT credentials from `%USERPROFILE%\.foodflow\auth-service.properties` and `%USERPROFILE%\.foodflow\api-gateway.properties`; Order Service loads Razorpay credentials from `%USERPROFILE%\.foodflow\order-service.properties`. These private files are outside the project and must not be committed or shared. Set Razorpay test-mode credentials before starting Order Service. Never commit live or test secrets. Razorpay orders require server-side signature and captured-status verification before kitchen fulfillment; COD orders skip Razorpay and require the delivery partner to confirm cash collection before completion.

## Start infrastructure
Copy `.env.example` to `.env` in the project root and set a local `POSTGRES_PASSWORD` before starting Docker Compose. Keep `.env` private; it is ignored by Git. Set `POSTGRES_PASSWORD` in the environment for backend services launched outside Docker.
```bash
cp .env.example .env
docker compose up -d postgres
```
The Compose file creates separate logical databases for the services. All services read the PostgreSQL password from `POSTGRES_PASSWORD`. If you previously started Compose with an existing PostgreSQL volume, Compose will not change credentials in that volume automatically; back up data and recreate the volume only if you are comfortable deleting its contents.

## Start backend
Open separate terminals from the project root and run these commands in order. The PowerShell commands work on Windows:
```bash
Set-Location .\eureka-server; mvn spring-boot:run
Set-Location .\auth-service; mvn spring-boot:run
Set-Location .\catalog-service; mvn spring-boot:run
Set-Location .\coupon-service; mvn spring-boot:run
Set-Location .\order-service; mvn spring-boot:run
Set-Location .\delivery-service; mvn spring-boot:run
Set-Location .\review-service; mvn spring-boot:run
Set-Location .\api-gateway; mvn spring-boot:run
```
Centralized Swagger UI is available through the API Gateway at `http://localhost:8080/swagger-ui.html` (it redirects to the UI asset path). Select a service from the Swagger dropdown to view its API, click **Authorize**, and paste the JWT access token returned by Auth Service login (without adding `Bearer `). The token is sent as an HTTP Bearer token for Try it out requests. OpenAPI documents are routed through the gateway at `/auth/v3/api-docs`, `/catalog/v3/api-docs`, `/orders/v3/api-docs`, `/delivery/v3/api-docs`, `/coupon/v3/api-docs`, and `/review/v3/api-docs`. Individual service Swagger UIs remain available at `http://localhost:<service-port>/swagger-ui/index.html`. Eureka dashboard: `http://localhost:8761`.

## Start frontend
```bash
cd frontend
npm install
npm start
```
Open `http://localhost:4200`.

## Accounts and roles
Fresh databases have no preloaded accounts, menu items, coupons, or simulated sessions. Removing seeders does not delete rows already stored in an existing database; back up and inspect existing data before cleaning it up. Registration always creates a `CUSTOMER`; applicants can request restaurant-owner or delivery-partner access during registration or, after signing in, submit an application from the home page using their existing account. An administrator must approve each request before role access is granted. After approval, sign out and back in to refresh the account role; restaurant owners can then use the Restaurant Portal to add menu items, edit prices/descriptions/stock, delete items, pause order intake, and manage kitchen orders. Menu management is limited to that owner's approved restaurant. During an administrator-approved owner application, matching unassigned legacy menu rows are attached to that restaurant; unrelated rows are left untouched.

For first-time local setup, Auth Service can create one administrator from the optional private `%USERPROFILE%\.foodflow\auth-admin-bootstrap.properties` file. Set `foodflow.bootstrap-admin.enabled=true`, `foodflow.bootstrap-admin.email`, `foodflow.bootstrap-admin.full-name`, and `foodflow.bootstrap-admin.password` there; the password must be at least 8 characters. Auth Service also loads JWT settings from `%USERPROFILE%\.foodflow\auth-service.properties`. After the first admin is created, you can set bootstrap `enabled=false` and remove its password; the admin remains in the database. Bootstrap never changes an existing account or resets an existing admin password. Public registration and application approval cannot grant the `ADMIN` role. Keep both private files outside the project and never commit or share them.

## Important implementation notes
- JWT access tokens expire after 12 hours by default (configurable with `foodflow.jwt.access-token-expiration-seconds`) and are stored in browser `sessionStorage` by the Angular interceptor/service. This is exposed to JavaScript; HttpOnly cookies or a backend-for-frontend are safer for production.
- The API Gateway validates JWTs once, removes client-supplied identity headers, then forwards verified user ID and role headers. Downstream services trust these gateway headers and retain endpoint role authorization. Keep service ports private and expose only the gateway; direct public access to downstream ports would allow identity-header spoofing.
- Catalog stock reservations use a PostgreSQL pessimistic write lock. Order creation uses compensating stock release if persistence fails.
- Restaurant owners can pause order intake. Only ACTIVE restaurants accepting orders are listed for ordering or can reserve menu inventory; administrators can activate, deactivate, or suspend restaurants.
- Razorpay sandbox payments are created and verified by Order Service. Only captured payments are marked paid. COD is tracked separately as cash due and becomes collected only when the delivery partner explicitly confirms collection.
- Administrators can inspect registered account summaries and the latest orders' payment states. They can refund captured Razorpay orders before kitchen preparation and refresh provider-pending refund status; cash-on-delivery payments are not refundable through Razorpay.
- Administrators can create coupons from the Admin Console; customers validate/apply coupon codes in the cart before checkout. Coupon codes are normalized case-insensitively and must be active, unexpired, and within their usage limit.
- After an order reaches `DELIVERED`, its customer can submit one 1–5 star restaurant review with an optional comment from Order History. Reviews are tied to the delivered order and restaurant.
- When a restaurant marks an order `READY_FOR_PICKUP`, Delivery Service discovers it as a partner loads the queue and creates an available task. A delivery partner can claim it from the delivery dashboard.
- Customers can track order status and its history in Order History; while that page is open it refreshes automatically every 10 seconds. Restaurant owners can reject pending or accepted orders; reserved stock is restored and captured Razorpay payments are refunded (refunds may remain pending with the provider).
- Coupon and review services are intentionally small, business-focused services. Coupon usage persistence is in PostgreSQL; review duplicate protection uses a unique constraint on `(order_id, customer_id)`.
- For a multi-service distributed workflow, compensation is best-effort and requires reconciliation if the downstream release call is unavailable. This is documented rather than pretending a distributed transaction exists.

## Tests and coverage
Run `mvn verify` from the project root to build and test all backend services, or run it from an individual service directory. JaCoCo is configured to generate `target/site/jacoco/jacoco.xml`; this enables measurement but does not itself guarantee 80% coverage. SonarQube analysis requires a reachable server and project token; run `mvn verify sonar:sonar -Dsonar.host.url=... -Dsonar.token=...` from each service directory. Build the frontend with `npm --prefix frontend run build`. The frontend currently has no configured unit-test runner, so frontend coverage and an overall 80% target are not yet established.

## Project structure
Each service uses `controller`, `dto`, `entity`, `repository`, `service`, `exception`, `config` and (where applicable) `security` packages. Keep service-owned data isolated; never query another service's tables directly.
