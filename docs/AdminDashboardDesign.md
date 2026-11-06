# PayCore — Admin Dashboard & Operational Analytics Design

---

## 1. Overview & Core Philosophy

The **PayCore Admin Dashboard** serves as the primary operational command center for system administrators, compliance officers, and support engineers.

### Core Philosophy:
- **No Mock or Hardcoded Data:** Every metric, trend point, and KPI card is aggregated directly from live database tables (`payments`, `refunds`, `webhook_delivery_attempts`, `reconciliation_runs`, `reconciliation_records`).
- **Real-Time Insight:** Reflects real-time state machine transitions and async queue health without stale caches.
- **Lightweight & High-Performance:** Uses native database aggregation queries (`COUNT`, `SUM`, grouped date buckets) and responsive inline SVG visualizations with zero bulky charting libraries.

---

## 2. Metric Specifications & Sources

| KPI Metric | Source Table | Query / Aggregation Logic | Purpose |
|---|---|---|---|
| **Total Payment Volume** | `payments` | `SUM(amount) WHERE status = 'SUCCESS' AND created_at BETWEEN :start AND :end` | Gross financial throughput |
| **Payment Success Rate** | `payments` | `(COUNT(status='SUCCESS') / COUNT(*)) * 100` | Operational conversion efficiency |
| **Settled Refund Volume** | `refunds` | `SUM(amount) WHERE status = 'SUCCESS' AND created_at BETWEEN :start AND :end` | Chargeback & refund impact |
| **Pending Refunds** | `refunds` | `COUNT(*) WHERE status IN ('REQUESTED', 'PROCESSING')` | Active customer dispute workload |
| **Webhook Delivery Failures** | `webhook_delivery_attempts` | `COUNT(*) WHERE status = 'FAILED' AND attempt_number >= max_retries` | Downstream merchant connectivity health |
| **Pending Webhook Retries** | `webhook_delivery_attempts` | `COUNT(*) WHERE status = 'RETRYING'` | Outbox dispatch buffer backlog |
| **Latest Reconciliation Run** | `reconciliation_runs` | `ORDER BY created_at DESC LIMIT 1` | Ledger clearing status |
| **Unresolved Discrepancies** | `reconciliation_records` | `COUNT(*) WHERE status IN ('UNMATCHED', 'REVIEW_REQUIRED')` | Critical financial investigation queue |

---

## 3. Visual Trend Analytics & SVG Visualizations

The frontend dashboard provides lightweight, responsive SVG visualizations tailored to Tailwind CSS design tokens:

1. **Payment Daily Volume Trend:**
   Dynamic bar chart plotting daily volume throughput and success/failure counts with interactive hover tooltips.
2. **Payment Lifecycle Status Distribution:**
   Visual status progress bars displaying percentage breakdown across `SUCCESS`, `FAILED`, `PROCESSING`, and `UNKNOWN`.
3. **Operational Shortcuts:**
   Direct modal trigger for instant reconciliation execution, quick-link inspection of unresolved discrepancies, and live audit feed.

---

## 4. API Specification

All dashboard endpoints require `ROLE_ADMIN` authentication:

- `GET /api/v1/admin/dashboard/summary?startDate=&endDate=`: High-level operational metrics.
- `GET /api/v1/admin/dashboard/trends/payments?startDate=&endDate=`: Daily time-series payment volume points.
- `GET /api/v1/admin/dashboard/trends/refunds?startDate=&endDate=`: Daily time-series refund volume points.
- `GET /api/v1/admin/dashboard/status-distribution?startDate=&endDate=`: Distribution percentages across payment statuses.
- `GET /api/v1/admin/dashboard/reconciliation-summary`: Global reconciliation run counts and discrepancy breakdown.
