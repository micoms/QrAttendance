# QR Attend — Full Codebase Reviewer

> **Stack:** Java 17 · Swing GUI · MySQL/MariaDB · ZXing (QR) · Resend (Email) · Gemini AI
> **Entry point:** `src/ppb/qrattend/main/Main.java`
> **Config:** `config/database.properties` (copy from `database.properties.example`)

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Architecture Map](#2-architecture-map)
3. [Package-by-Package File Reference](#3-package-by-package-file-reference)
4. [Database Schema](#4-database-schema)
5. [Configuration & Setup](#5-configuration--setup)
6. [User Roles & Login Flow](#6-user-roles--login-flow)
7. [Core Data Models](#7-core-data-models)
8. [Service Layer](#8-service-layer)
9. [UI Layer (app package)](#9-ui-layer-app-package)
10. [Supporting Systems](#10-supporting-systems)
11. [Known Issues & Review Notes](#11-known-issues--review-notes)
12. [Quick Review Checklist](#12-quick-review-checklist)

---

## 1. Project Overview

QR Attend is a **desktop Java Swing application** for school attendance tracking. Teachers scan student QR codes (or mark attendance manually) during class. Admins manage teachers, schedules, sections, students, and review requests.

**Key capabilities:**
- Dual-role login: **Admin** and **Teacher**
- QR code generation per student, emailed via Resend API
- Attendance sessions auto-detected from active schedules
- Temporary class override when no scheduled class exists
- Schedule change & student removal request/approval workflow
- Reports with CSV export and Gemini AI chat assistant
- Password-hashed authentication with forced first-login change

---

## 2. Architecture Map

```
Main.java  (JFrame, login/workspace routing)
│
├── component/login/     ← Login UI panels (PanelCover, PanelLogin)
│
└── AppShell.java        ← Main workspace (sidebar nav + content area)
    │
    ├── app/*Screen.java ← All screens (Admin/Teacher dashboards, Attendance, Reports…)
    │
    └── AppStore.java    ← Single facade; all UI actions go through here
        │
        ├── service/AttendanceService.java
        ├── service/ScheduleService.java
        ├── service/StudentService.java
        ├── service/TeacherService.java
        ├── service/SectionService.java
        ├── service/ReportService.java
        ├── service/EmailService.java
        └── service/AiChatService.java
            │
            └── db/DatabaseManager.java  ← JDBC connection factory
                └── config/database.properties
```

**Data flow:** UI Screen → `AppShell` helper → `AppStore` method → `ServiceResult<T>` → `ActionResult` → banner feedback.

---

## 3. Package-by-Package File Reference

### `ppb.qrattend.main`
| File | Purpose |
|------|---------|
| `Main.java` | Application entry point. Builds `JFrame`, hosts login (`CardLayout`) and workspace panels. Calls `DatabaseAuthenticationService` to verify credentials. On success, builds `AppShell`. |
| `Main.form` | NetBeans GUI builder form (legacy, not actively used for layout). |

---

### `ppb.qrattend.app`
| File | Purpose |
|------|---------|
| `AppStore.java` | **Central state + action façade.** Holds all service instances. Every screen calls `AppStore` — never services directly. Converts `ServiceResult` → `ActionResult` and fires `listeners` after every write. |
| `AppShell.java` | **Workspace shell.** Sidebar navigation, top header, banner notifications. Routes view keys (`"home"`, `"attendance"`, etc.) to screen builders. Stores report filter state (`reportTeacherFilter`, etc.). Auto-refreshes attendance every 30 s. |
| `AppTheme.java` | Full design system: colors, fonts (`AppTheme.headlineFont`, `bodyFont`), component factories (`styleTable`, `stylePrimaryButton`, `createSection`, `RoundedPanel`, etc.). All screens use this exclusively. |
| `AppFlowPanels.java` | Reusable panel helpers used across multiple screens. |
| `AdminDashboardScreen.java` | Admin home — summary cards linking to each admin section. |
| `TeacherDashboardScreen.java` | Teacher home — current/next class info, quick stats. |
| `TeachersScreen.java` | Admin: list teachers, add teacher, resend/reset password. |
| `AdminStudentsScreen.java` | Admin: view all students, add/import/edit/deactivate, resend QR. |
| `TeacherRosterScreen.java` | Teacher: read-only view of own students, submit removal request. |
| `AdminSchedulesScreen.java` | Admin: full schedule CRUD — add, edit, deactivate. |
| `TeacherScheduleScreen.java` | Teacher: own schedule, submit change request. |
| `SectionsScreen.java` | Admin: manage sections, subjects, and rooms (tabbed). |
| `AttendanceScreen.java` | Teacher: QR scan field, manual click attendance, start/end temporary class, mark-all-absent button. |
| `RequestsScreen.java` | Admin: review schedule change and student removal requests (approve/reject). |
| `ReportsScreen.java` | Both roles: filter records by teacher/section/subject, view summary metrics, export CSV, Ask AI panel. |
| `PasswordChangeScreen.java` | Forced on first login (when `must_change_password = 1`). Blocks all other navigation until changed. |

---

### `ppb.qrattend.component.login`
| File | Purpose |
|------|---------|
| `PanelCover.java` | Left half of login screen — branding panel, role-toggle button. |
| `PanelLogin.java` | Right half — email/password fields, role-aware submit, status display. Calls back `Main::attemptLogin`. |
| `PanelLoading.java` | Simple animated loading overlay. |
| `Message.java` | Reusable status message label component. |

---

### `ppb.qrattend.db`
| File | Purpose |
|------|---------|
| `DatabaseConfig.java` | Reads `config/database.properties`. Parses `db.enabled`, `db.url`, `db.username`, `db.password`, `db.driverClass`. Returns disabled config with a user-friendly message if file is missing or invalid. |
| `DatabaseManager.java` | Holds `DatabaseConfig`. Loads JDBC driver via reflection. `openConnection()` returns a fresh `Connection` per call (no pool). `isReady()` = config enabled AND driver loaded. |
| `DatabaseAuthenticationService.java` | Login query against `users` table filtered by `email`, `role`, and `password_hash` (SHA-256). Updates `last_login_at` on success. Returns `AuthenticationResult` (success/failure + `ModelUser`). |
| `PasswordUtil.java` | Single static method: `hashPassword(String)` → SHA-256 hex string. |
| `SecurityUtil.java` | `sha256Hex(String)` for QR token hashing. `generateOpaqueToken()` — 24-byte random Base64 prefixed with `"QRATTEND-"` used as the student QR payload. |

---

### `ppb.qrattend.model`
| File | Purpose |
|------|---------|
| `ModelUser.java` | Logged-in user session object. Holds `userId`, `fullName`, `email`, `role` (`ADMIN`/`TEACHER`), `mustChangePassword` flag. Mutable (setters present). |
| `AppDomain.java` | **Legacy model classes** — `TeacherProfile`, `StudentProfile`, `ScheduleSlot`, `ScheduleChangeRequest`, `AttendanceSession`, `AttendanceRecord`, `EmailDispatch`. Also defines enums: `UserRole`, `SessionState`, `ScheduleRequestStatus`, `AttendanceStatus`, `AttendanceSource`, `EmailStatus`. **Note:** Many of these are superseded by `CoreModels` but still referenced in some places. |
| `CoreModels.java` | **Active model records** used by all current services and screens. Immutable Java records/final classes. Contains: `Teacher`, `Section`, `Subject`, `Room`, `Student`, `Schedule`, `ScheduleRequest`, `StudentRemovalRequest`, `AttendanceSession`, `AttendanceRecord`, `EmailLog`, `ReportSummary`. Also enums: `EmailStatus`, `RequestStatus`, `SessionStatus`, `AttendanceMethod`, `AttendanceStatus`. Formatters: `TIME_FORMAT`, `DATE_FORMAT`, `DATE_TIME_FORMAT`. |

---

### `ppb.qrattend.service`
| File | Purpose |
|------|---------|
| `ServiceResult<T>.java` | Generic result wrapper. Three states: `success(message, data)`, `warning(message, data)`, `failure(message)`, plus `notImplemented`. Carries a typed data payload. |
| `TeacherService.java` | Teacher CRUD. Generates 10-char random temporary passwords, hashes them, queues email via `EmailService`, sends via `ResendEmailClient`. Handles resend/reset password rotation. |
| `StudentService.java` | Student CRUD and import. On add/resend: generates opaque QR token → SHA-256 hash stored in DB → sends email with raw token embedded in QR image. Import parses tab or comma-separated rows. |
| `SectionService.java` | Section CRUD. `addSection`, `renameSection`, `deleteSection` (blocked if students exist). |
| `ScheduleService.java` | Schedule and request management. Conflict detection (`FIND_CONFLICTING_SCHEDULE_SQL`) checks overlapping times on same day for same teacher. On approval: atomically updates the schedule row. |
| `AttendanceService.java` | Core attendance logic. `getCurrentSessionForTeacher`: checks for open temporary session first, then matches active schedule by day/time. Auto-creates session if none exists; closes stale ones. QR lookup matches by `qr_hash`, `student_code`, or `email`. Duplicate prevention via `uk_attendance_once` constraint. |
| `ReportService.java` | Filtered attendance record queries (by teacher/section/subject). Builds summary counts. CSV export with optional Teacher column, RFC-compliant escaping. |
| `EmailService.java` | Creates `email_logs` queue entries (`createQueuedEmail`), marks sent/failed (`markSent`, `markFailed`). `getRecentLogs` for admin view. |
| `AiChatService.java` | Manages per-teacher per-scope conversation history (DB-backed). `ask` appends to history and calls `GeminiAiClient`. `clear` wipes the conversation. |

---

### `ppb.qrattend.ai`
| File | Purpose |
|------|---------|
| `AiClient.java` | Interface: `generateInsight(AiInsightRequest)`, `isAvailable()`, `getStatusMessage()`. |
| `AiConfig.java` | Reads `ai.*` keys from `database.properties`. Parses `ai.enabled`, `ai.apiKey`, `ai.model`, `ai.timeoutSeconds`, `ai.maxPromptChars`. |
| `AiInsightRequest.java` | Request DTO: insight type, title, target type, max words, context lines. |
| `AiInsightResponse.java` | Response DTO: provider, model, generated text, timestamp, whether cached. |
| `GeminiAiClient.java` | HTTP POST to `generativelanguage.googleapis.com`. Builds system instruction + user prompt. Manually parses JSON response (no external JSON library). Two prompt modes: `CHAT_*` (teacher Q&A) and structured insight. |

---

### `ppb.qrattend.email`
| File | Purpose |
|------|---------|
| `ResendConfig.java` | Reads `mail.*` from `database.properties`. Validates `mail.enabled`, `mail.apiKey`, `mail.fromEmail`. Only `resend` provider is supported. |
| `ResendEmailClient.java` | HTTP POST to `https://api.resend.com/emails`. Builds HTML email bodies for `sendTeacherPasswordEmail` and `sendStudentQrEmail` (includes inline QR code as Base64 PNG). Returns `EmailSendResult`. |

---

### `ppb.qrattend.qr`
| File | Purpose |
|------|---------|
| `QrCodeService.java` | Static utility. Generates QR `BufferedImage`, PNG bytes, Base64 PNG, or saves to file path. Decodes QR from image or file. Uses ZXing with UTF-8, margin=1, error correction M. Default size 320px. |
| `QrScannerDialog.java` | Swing dialog with a text field + file-picker to load a QR image for decoding. Calls `QrCodeService.decodeQrFile`. Fires callback with decoded string. |

---

### `ppb.qrattend.swing`
| File | Purpose |
|------|---------|
| `Button.java` | Custom rounded `JButton` subclass with hover state. |
| `ButtonOutLine.java` | Outline-style rounded button variant. |
| `MyTextField.java` | Custom styled text field with icon support and placeholder text. |
| `MyPasswordField.java` | Same as `MyTextField` but for password input with show/hide toggle. |

---

### `ppb.qrattend.icon`
Static image resources: `user.png`, `pass.png`, `mail.png`, `success.png`, `error.png`, `loading.gif`.

---

### `ppb.qrattend.util`
| File | Purpose |
|------|---------|
| `AppClock.java` | Thin wrapper: `today()` → `LocalDate.now()`, `nowTime()` → `LocalTime.now()`, `nowDateTime()` → `LocalDateTime.now()`. Centralised so tests or future mocking can override. |

---

## 4. Database Schema

**Tables (creation order — FK dependencies respected):**

| Table | Key Columns | Notes |
|-------|------------|-------|
| `users` | `user_id`, `email` (unique), `password_hash` (SHA-256 hex), `role` ENUM, `must_change_password` | Admins start at `user_id=1`. Auto-increment starts at 100 for new rows. |
| `sections` | `section_id`, `section_name` (unique) | Referenced by students, schedules, sessions. Cannot delete if students exist (`RESTRICT`). |
| `subjects` | `subject_id`, `subject_name` (unique) | Cannot delete if active schedules reference it. |
| `rooms` | `room_id`, `room_name` (unique) | Cannot delete if active schedules reference it. |
| `students` | `student_id`, `student_code` (unique), `email` (unique), `qr_hash` (SHA-256 of token), `qr_status` ENUM | FK to `sections`. `is_active` soft-delete. |
| `schedules` | `schedule_id`, `teacher_user_id`, `day_of_week` (1–7), `start_time`, `end_time`, `is_active` | CHECK constraint: `start_time < end_time`. Index on `(teacher_user_id, day_of_week, start_time)`. |
| `schedule_requests` | `request_id`, `schedule_id`, `status` ENUM (PENDING/APPROVED/REJECTED), `reviewed_by_user_id` | FK to `schedules` CASCADE DELETE. |
| `student_removal_requests` | `request_id`, `teacher_user_id`, `student_id`, `status` ENUM | FK to `students` CASCADE DELETE. |
| `attendance_sessions` | `session_id`, `teacher_user_id`, `schedule_id` (nullable), `is_temporary`, `status` (OPEN/CLOSED), `opened_at`, `closed_at` | `schedule_id` NULL for temp classes. FK to `schedules` SET NULL on delete. |
| `attendance_records` | `record_id`, `session_id`, `student_id`, `attendance_method` ENUM (QR/MANUAL), `attendance_status` ENUM (PRESENT/LATE) | Unique: `(session_id, student_id)` — one record per student per session. **ABSENT is not stored** — absence = no record. |
| `email_logs` | `email_id`, `email_type` ENUM (TEACHER_PASSWORD/STUDENT_QR), `status` ENUM (QUEUED/SENT/FAILED), `provider_message_id` | Audit trail for all emails sent. |

> **Default admin credentials** (seeded in schema):
> - Email: `admin@qrattend.local`
> - Password hash maps to `admin123` — **change immediately in production**

---

## 5. SQL Table Aliases Quick Reference

Every SQL query in the service layer uses short aliases for table names. This table lists every alias used, the table it maps to, and where it appears.

| Alias | Full Table Name | Meaning | Used In |
|-------|----------------|---------|---------|
| `sc` | `schedules` | A scheduled class slot (teacher + section + subject + room + day/time) | `ScheduleService`, `AttendanceService`, `StudentService`, `ReportService` |
| `u` | `users` | A user account — either an Admin or a Teacher | `ScheduleService`, `TeacherService`, `StudentService`, `ReportService`, `DatabaseAuthenticationService` |
| `sec` | `sections` | A class section (e.g., "BSIT 2A") | `ScheduleService`, `AttendanceService`, `StudentService`, `SectionService`, `ReportService` |
| `sub` | `subjects` | A subject (e.g., "Web Development") | `ScheduleService`, `AttendanceService`, `ReportService` |
| `r` | `rooms` | A room or classroom (e.g., "Room 301") | `ScheduleService`, `AttendanceService` |
| `s` | `students` | A student record | `StudentService`, `AttendanceService` |
| `sr` | `schedule_requests` | A teacher's request to change a schedule | `ScheduleService` |
| `sess` | `attendance_sessions` | One open/closed class session (auto-created per schedule slot per day) | `AttendanceService`, `ReportService` |
| `ar` | `attendance_records` | One attendance mark (PRESENT/LATE) per student per session | `AttendanceService`, `ReportService` |
| `st` | `students` | Same as `s` — alternate alias used in `ReportService` to avoid collision with `sr` | `ReportService` |
| `t` | `users` (teacher role) | The teacher side of a join when `users` is joined twice (e.g., teacher + reviewer) | `StudentService` (removal requests) |
| `reviewer` | `users` (reviewer role) | The admin who reviewed a request — second join of `users` table | `ScheduleService`, `StudentService` |
| `el` | `email_logs` | An email log entry (correlated subquery for latest email status) | `TeacherService` |

### Example — `SELECT_SCHEDULES_SQL` in `ScheduleService.java`

```sql
SELECT
    sc.schedule_id,           -- schedules.schedule_id
    sc.teacher_user_id,       -- schedules → FK to users
    u.full_name AS teacher_name,  -- users.full_name (alias: u)
    sc.section_id,            -- schedules → FK to sections
    sec.section_name,         -- sections.section_name (alias: sec)
    sc.subject_id,            -- schedules → FK to subjects
    sub.subject_name,         -- subjects.subject_name (alias: sub)
    sc.room_id,               -- schedules → FK to rooms
    r.room_name,              -- rooms.room_name (alias: r)
    sc.day_of_week,           -- schedules: 1=Mon … 7=Sun
    sc.start_time,
    sc.end_time,
    sc.is_active
FROM schedules sc                          -- sc = schedules
INNER JOIN users u                         -- u  = users (teacher)
    ON u.user_id = sc.teacher_user_id
INNER JOIN sections sec                    -- sec = sections
    ON sec.section_id = sc.section_id
INNER JOIN subjects sub                    -- sub = subjects
    ON sub.subject_id = sc.subject_id
INNER JOIN rooms r                         -- r  = rooms
    ON r.room_id = sc.room_id
WHERE sc.is_active = 1
ORDER BY sc.day_of_week ASC, sc.start_time ASC, teacher_name ASC
```

### Example — `SELECT_ALL_REMOVAL_REQUESTS_SQL` in `StudentService.java`
Shows how `users` is joined **twice** under different aliases:

```sql
FROM student_removal_requests r        -- r  = the request row
INNER JOIN users t                     -- t  = teacher who submitted
    ON t.user_id = r.teacher_user_id
INNER JOIN students s                  -- s  = the student being removed
    ON s.student_id = r.student_id
INNER JOIN sections sec                -- sec = student's section
    ON sec.section_id = s.section_id
LEFT JOIN users reviewer               -- reviewer = admin who reviewed
    ON reviewer.user_id = r.reviewed_by_user_id
```

> **Tip:** When `users` appears twice in a query, look for two different aliases — one for the actor (teacher/requester) and one for the reviewer (admin).

---

## 6. Configuration & Setup

**File:** `config/database.properties` (copy from `config/database.properties.example`)

```properties
# Database (required)
db.enabled=true
db.driverClass=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/qrattend_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Manila
db.username=root
db.password=your_password_here

# Email via Resend (optional, set mail.enabled=false to disable)
mail.enabled=true
mail.apiKey=re_xxxxxxxxxxxx
mail.fromEmail=no-reply@yourdomain.com
mail.fromName=QR Attend

# Gemini AI (optional, set ai.enabled=false to disable)
ai.enabled=true
ai.apiKey=AIzaSy...
ai.model=gemini-2.5-flash
ai.maxPromptChars=12000
```

**Database setup:**
1. Run `database/qrattend_full_schema.sql` on your MySQL/MariaDB server.
2. Apply any migration scripts in `database/` if upgrading (check filenames for dates).

**Migration files:**
| File | Purpose |
|------|---------|
| `qrattend_full_schema.sql` | Full fresh schema — drops and recreates all tables |
| `qrattend_absent_status_migration.sql` | Adds ABSENT support changes |
| `qrattend_admin_student_sections_migration.sql` | Admin student/section view fixes |
| `qrattend_security_cleanup_migration.sql` | Security field cleanup |

---

## 6. User Roles & Login Flow

```
Login Screen
├── PanelCover (toggle button: Admin ↔ Teacher)
└── PanelLogin (email + password)
       │
       └── Main.attemptLogin(email, password, role)
              │
              └── DatabaseAuthenticationService.authenticate()
                     ├── checks db.enabled + driver loaded
                     ├── normalises email (lowercase), hashes password (SHA-256)
                     ├── SELECT WHERE email=? AND role=? AND password_hash=?
                     ├── checks is_active = 1
                     ├── UPDATE last_login_at
                     └── returns ModelUser with mustChangePassword flag
                            │
                            └── AppShell built
                                   ├── if mustChangePassword → PasswordChangeScreen only
                                   └── else → full nav (role-specific)
```

**Admin nav:** Home · Teachers · School Lists · Students · Schedule · Requests · Reports

**Teacher nav:** Home · Attendance · Class List · My Schedule · Reports

---

## 7. Core Data Models

All **active** models live in `CoreModels.java` as final inner classes (record-like with explicit constructors and accessors):

| Model | Key Fields |
|-------|-----------|
| `Teacher` | `id`, `fullName`, `email`, `active`, `emailStatus` |
| `Section` | `id`, `name` |
| `Subject` | `id`, `name` |
| `Room` | `id`, `name` |
| `Student` | `id`, `studentCode`, `fullName`, `email`, `sectionId`, `sectionName`, `active`, `qrStatus` |
| `Schedule` | `id`, `teacherId`, `teacherName`, `sectionId+Name`, `subjectId+Name`, `roomId+Name`, `day`, `startTime`, `endTime`, `active` |
| `ScheduleRequest` | All schedule fields + `scheduleId`, `reason`, `status`, `reviewedBy`, timestamps |
| `StudentRemovalRequest` | `id`, `teacherId`, `studentId+Code+Name`, `sectionName`, `reason`, `status`, timestamps |
| `AttendanceSession` | `id`, `teacherId`, `sectionId+Name`, `subjectId+Name`, `roomName`, `sessionDate`, `openedAt`, `temporary`, `reason`, `status` |
| `AttendanceRecord` | `id`, `studentCode+Name`, `sectionName`, `subjectName`, `teacherName`, `recordedAt`, `method`, `status`, `note` |
| `EmailLog` | `id`, `emailType`, `recipientEmail`, `subjectLine`, `infoText`, `status`, `createdAt` |
| `ReportSummary` | `totalStudents`, `totalPresent`, `totalLate`, `totalRecords` + `toPlainText()` |

> **Note:** `AppDomain.java` contains **older parallel models** (`TeacherProfile`, `ScheduleSlot`, etc.) still used in some screens. Both files should eventually be unified into `CoreModels`.

---

## 8. Service Layer

### ServiceResult Pattern
Every service method returns `ServiceResult<T>`:
- `success(message, data)` — operation completed, data available
- `warning(message, data)` — completed with caveats (e.g., email failed but student saved)
- `failure(message)` — operation failed, data is null

`AppStore.handleWrite()` converts to `ActionResult` and fires listeners. `AppStore.listOrEmpty()` / `dataOrNull()` safely unwrap for read operations.

### AttendanceService — Session Resolution Logic
```
getCurrentSessionForTeacher(teacherId):
  1. Check for open TEMPORARY session → return if found
  2. Query current schedule (matching today's day + time window)
  3. If no schedule → close stale open sessions → return NONE sentinel
  4. Close sessions for other schedules today
  5. Find or CREATE session for current schedule
  6. Return session
```
Sessions are **auto-created** on the first attendance query during a scheduled class window.

### QR Identification
Student lookup by QR accepts three formats (OR condition):
1. `qr_hash` — SHA-256 of the opaque token in the QR code
2. `UPPER(student_code)` — typed manually
3. `LOWER(email)` — typed manually

### Schedule Conflict Detection
On add/update/approve: queries `FIND_CONFLICTING_SCHEDULE_SQL` which checks:
```sql
WHERE teacher_user_id = ? AND day_of_week = ? AND is_active = 1
  AND schedule_id <> ?  -- excludes self on update
  AND (newStart < end_time AND newEnd > start_time)  -- overlap
```

---

## 9. UI Layer (app package)

### AppStore.ActionResult
```
success  → showMessage(msg, AppTheme.SUCCESS)   [green banner, 4s auto-clear]
warning  → showMessage(msg, AppTheme.WARNING)   [yellow banner]
failure  → showMessage(msg, AppTheme.DANGER)    [red banner]
```

### AppShell Key Methods
| Method | What it does |
|--------|-------------|
| `openView(key)` | Changes `selectedView`, calls `refreshSelectedView()`. Blocked if `mustChangePassword`. |
| `showResult(ActionResult)` | Maps result type to banner color and message. |
| `createTeacherAiPanel(scopeKey, ...)` | Builds reusable Ask AI section with conversation history and question input. |
| `newTextField()` / `newTextArea()` | Standard styled input factories (220px width). |
| `newTimeCombo()` | Dropdown with 30-min intervals from 7:00 AM to 9:00 PM. |
| `openQrScannerFor(field)` | Opens `QrScannerDialog`, sets decoded text into `field`. |

### Report Filter State
`AppShell` holds `reportTeacherFilter`, `reportSectionFilter`, `reportSubjectFilter` (`Integer`, nullable). `ReportsScreen` reads and sets these to persist filter selection across refreshes.

---

## 10. Supporting Systems

### Email (Resend API)
- Teacher creation/password reset → `sendTeacherPasswordEmail` (HTML with temp password)
- Student add/resend QR → `sendStudentQrEmail` (HTML with inline Base64 QR image)
- All emails are **queued first** (`email_logs` status = QUEUED), then sent synchronously, then status updated.
- If email fails, student/teacher is still saved — result is a `warning` not a `failure`.

### AI Chat (Gemini)
- `AiChatService` stores conversation history in a `ai_conversations` table (or equivalent).
- `AppStore.buildAiContext()` injects live data as context lines based on `scopeKey`:
  - `"reports"` scope → summary + recent records
  - `"attendance"` scope → session status + recent marks
  - Default scope → section names, student count, pending requests
- The system prompt explicitly forbids the AI from revealing passwords or DB credentials.

### QR Code Flow
```
Student added → SecurityUtil.generateOpaqueToken()  ("QRATTEND-<base64>")
             → sha256Hex(token) stored as qr_hash in DB
             → raw token embedded in QR image PNG
             → PNG sent as Base64 in HTML email
─────────────────────────────────────────────────
Teacher scans → QR decoded text = raw token
             → AttendanceService: sha256Hex(scannedValue) compared to qr_hash
```

---

## 11. Known Issues & Review Notes

### Dual Model Problem ⚠️
`AppDomain.java` and `CoreModels.java` define overlapping models (`AttendanceRecord`, `AttendanceSession`, `StudentRemovalRequest`, etc.). Some screens use `AppDomain.*`, others use `CoreModels.*`. This can cause confusion.

**Action:** Migrate all remaining `AppDomain` usage to `CoreModels` and delete `AppDomain`.

### No Connection Pool
`DatabaseManager.openConnection()` creates a new JDBC connection on every call. Each service method opens and closes its own connection.

**Risk:** High query frequency (like 30-second attendance timer) creates new connections repeatedly.
**Action:** Add HikariCP or at minimum cache a shared connection.

### Absent Status — No DB Record
Absent = student has no `attendance_records` row for that session. The `markAllAbsent()` method **does** write ABSENT records explicitly, but the schema `attendance_status` ENUM doesn't include ABSENT (only PRESENT, LATE).

> **Note:** Migration file `qrattend_absent_status_migration.sql` may address this — verify it's been applied.

### AppShell Rebuilds Full View on Every Refresh
`refreshSelectedView()` calls `contentHost.removeAll()` and rebuilds the entire view from scratch. For heavy screens (large tables), this causes flicker and is inefficient.

### Password in Email is Plaintext
Teacher temporary passwords are emailed as plain text. Once received, the DB stores the hash — acceptable security model for a school system but worth documenting.

### No Input Sanitisation for SQL `LIKE` patterns
Not an issue currently (no LIKE queries used in filters), but worth checking if search features are added.

### `AppDomain.TeacherProfile.getPassword()` Returns Empty String
`getPassword()` always returns `""` and `setPassword()` is a no-op. This is intentional (password never stored in domain object) but the method signatures are misleading.

---

## 12. Quick Review Checklist

Use this when reviewing a specific change or doing a full audit:

### Authentication & Security
- [ ] `password_hash` uses SHA-256 (not BCrypt) — acceptable for school context, but note the limitation
- [ ] `must_change_password` enforced in `AppShell` before any navigation
- [ ] QR token is opaque random bytes, stored only as hash — raw token never in DB ✓
- [ ] SQL uses `PreparedStatement` everywhere — no string concatenation in WHERE clauses ✓
- [ ] AI prompt cannot leak passwords or DB credentials (system instruction enforces this) ✓

### Database Integrity
- [ ] All FK constraints defined with correct ON DELETE actions
- [ ] `uk_attendance_once (session_id, student_id)` prevents duplicate attendance ✓
- [ ] `uk_students_code` and `uk_students_email` prevent duplicates ✓
- [ ] `chk_schedules_time (start_time < end_time)` enforced at DB level ✓
- [ ] `ABSENT` status — check if migration has been applied

### Service Layer
- [ ] All write operations use transactions (`setAutoCommit(false)` + `commit`/`rollback`) ✓
- [ ] `ServiceResult` states (success/warning/failure) correctly handled in `AppStore` ✓
- [ ] Schedule conflict detection runs before insert AND before approving a request ✓
- [ ] Email send failure returns `warning` (not `failure`) — student/teacher still saved ✓

### UI & UX
- [ ] `mustChangePassword` blocks all nav until password is changed ✓
- [ ] Banner messages auto-clear after 4 seconds ✓
- [ ] Attendance auto-refresh timer (30s) stops on logout ✓
- [ ] Report filters persist across refreshes (stored in `AppShell`) ✓
- [ ] CSV export includes Teacher column only for Admin role ✓

### Configuration
- [ ] `config/database.properties` is in `.gitignore` (contains credentials)
- [ ] `database.properties.example` committed as reference template ✓
- [ ] Default admin password is changed from `admin123` before deployment

---

*Last reviewed: May 7, 2026 | Reviewer: Antigravity AI*
