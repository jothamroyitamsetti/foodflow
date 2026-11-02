# FoodFlow architecture

```mermaid
flowchart TD
 UI[Angular 17] --> GW[Spring Cloud Gateway]
 GW --> E[Eureka Discovery]
 GW --> AUTH[Auth Service]
 GW --> CAT[Catalog Service]
 GW --> ORD[Order Service]
 GW --> DEL[Delivery Service]
 GW --> COUP[Coupon Service]
 GW --> REV[Review Service]
 ORD -->|OpenFeign: reserve/release| CAT
 DEL -->|OpenFeign: order eligibility/status| ORD
 ORD -->|REST: READY_FOR_PICKUP creates available delivery| DEL
 AUTH --> DA[(foodflow_auth)]
 CAT --> DC[(foodflow_catalog)]
 ORD --> DO[(foodflow_orders)]
 DEL --> DD[(foodflow_delivery)]
 COUP --> DCO[(foodflow_coupon)]
 REV --> DR[(foodflow_review)]
```

## Business invariants
- Catalog Service owns inventory and serializes reservations using a PostgreSQL pessimistic write lock.
- Order Service owns order status and immutable item price snapshots.
- Delivery Service owns assignment records; only the assigned partner may update them.
- Coupon Service owns coupon definitions and usage counts.
- Review Service prevents more than one review per customer/order using a database unique constraint.
- Delivery tasks become available to delivery partners when an order reaches `READY_FOR_PICKUP`.
- A stock reservation plus order write spans services; compensation is best-effort. A production-grade version should persist outbox/reconciliation records for failed compensation.
