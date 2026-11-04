# PAYCORE — GIT & GITHUB WORKFLOW SPECIFICATION

## 1. Repository Topology

PayCore maintains two distinct, independent Git repositories:

1. **`PayCore-Backend`** (`origin https://github.com/Prutwi17/PayCore-Backend.git`)
2. **`PayCore-Frontend`** (`origin https://github.com/Prutwi17/PayCore-Frontend.git`)

Both repositories share the authoritative base branch: **`main`**.

---

## 2. Strict Engineering Rules

1. **No Direct Commits to `main`:** Committing directly to `main` during feature development is strictly prohibited. All modifications enter `main` exclusively through Pull Requests.
2. **Atomic Feature Isolation:** Every feature must be developed in a dedicated feature branch branched off the latest `main`.
3. **No Unrelated Multi-Repository Sprawl:** Feature branches live inside the appropriate primary repository; separate GitHub repositories must never be spawned for individual features.
4. **Synchronized Multi-Repo Coordination:** When a capability spans frontend and backend (e.g. creating payment, displaying payment timeline):
   - A feature branch `feature/payment-processing` is cut in `PayCore-Backend`.
   - A corresponding branch `feature/payment-processing` is cut in `PayCore-Frontend`.
   - Both PRs cross-reference each other in their descriptions.

---

## 3. Branch Naming Conventions

Feature branches must adhere to the standard prefix pattern:
- `feature/<milestone-or-capability>` (e.g., `feature/project-foundation`, `feature/authentication`, `feature/payment-processing`)
- `fix/<bug-identifier>`
- `refactor/<target-component>`
- `docs/<documentation-update>`

---

## 4. Conventional Commit Standard

Commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

- `feat:` A new feature or endpoint
- `fix:` A bug fix or error mitigation
- `refactor:` Code refactoring with no functional change
- `test:` Adding or updating unit/integration tests
- `docs:` Documentation additions or updates
- `chore:` Build scripts, dependency updates, CI configurations

*Example:*
```
feat(payment): implement simulated provider adapter and timeout state transition

- Add PaymentProvider SPI and SimulatedPaymentProvider
- Handle SocketTimeoutException with UNKNOWN state transition
- Add unit tests for provider timeout recovery
```

---

## 5. End-to-End Pull Request Lifecycle

For each milestone or feature increment:

1. **Sync Base:**
   ```bash
   git checkout main
   git pull origin main
   ```
2. **Branch:**
   ```bash
   git checkout -b feature/<feature-name>
   ```
3. **Implement & Test:**
   - Write code adhering to modular monolith boundaries.
   - Run tests locally:
     - Backend: `mvn clean test`
     - Frontend: `npm run test` & `npm run build`
4. **Commit:**
   ```bash
   git add <modified-files>
   git commit -m "feat(<scope>): meaningful description"
   ```
5. **Push:**
   ```bash
   git push -u origin feature/<feature-name>
   ```
6. **Open PR:**
   - Title: `feat(<scope>): <short description>`
   - Body includes: Summary of changes, tests executed, CI check status, related frontend/backend PR link.
7. **CI & Review:** Ensure GitHub Actions automated checks turn green.
8. **Merge:** Merge into `main` (Squash & Merge or Rebase Merge), delete the feature branch.
9. **Update Local:**
   ```bash
   git checkout main
   git pull origin main
   ```
