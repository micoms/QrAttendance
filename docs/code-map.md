# Code Map

This is the beginner map of the active code.

## `src/ppb/qrattend/main`

### `Main.java`

Start here.

It:

- creates the login screen
- handles sign-in
- opens the workspace after login

## `src/ppb/qrattend/app`

### `AppShell.java`

The main workspace shell.

It handles:

- left menu
- page title
- message banner
- screen switching
- QR scanner dialog opening
- teacher AI panel

### `AppStore.java`

The UI-facing bridge.

Screens use this instead of calling services directly.

### `AppTheme.java`

Shared colors, fonts, field styles, table styles, and button styles.

### `AppFlowPanels.java`

Small reusable UI blocks like:

- action tiles
- simple info list panels

### Screen files

Admin:

- `AdminDashboardScreen.java`
- `TeachersScreen.java`
- `SectionsScreen.java`
- `AdminStudentsScreen.java`
- `AdminSchedulesScreen.java`
- `RequestsScreen.java`
- `ReportsScreen.java`

Teacher:

- `TeacherDashboardScreen.java`
- `AttendanceScreen.java`
- `TeacherRosterScreen.java`
- `TeacherScheduleScreen.java`
- `ReportsScreen.java`

## `src/ppb/qrattend/model`

### `CoreModels.java`

The main small records and enums used by the active app:

- teacher
- section
- subject
- room
- student
- schedule
- requests
- attendance session
- attendance record
- email log
- report summary

### `ModelUser.java`

Logged-in user data for the active session.

### `AppDomain.java`

Still used for shared role/login support.

## `src/ppb/qrattend/service`

### `TeacherService.java`

Teacher account create, resend password, reset password, list teachers.

### `SectionService.java`

Section list and section create.

### `StudentService.java`

Student save/import, QR resend, teacher class list, and student removal requests.

### `ScheduleService.java`

Subject and room save, schedule save, schedule list, schedule requests.

### `AttendanceService.java`

Current class check, automatic class session, temporary class, QR attendance, manual attendance.

### `ReportService.java`

Report summary and report records.

### `EmailService.java`

Simple email log create/update/read.

### `AiChatService.java`

Teacher page-based AI chat.

### `ServiceResult.java`

Shared result object for service success, warning, or failure.

## `src/ppb/qrattend/db`

### `DatabaseConfig.java`

Reads database config from `config/database.properties`.

### `DatabaseManager.java`

Opens MariaDB connections.

### `DatabaseAuthenticationService.java`

Checks login against `users`.

### `PasswordUtil.java`

Password hash helper.

### `SecurityUtil.java`

Used for secure token/hash work like QR hash creation.

## `src/ppb/qrattend/email`

### `ResendConfig.java`

Reads Resend settings from config.

### `ResendEmailClient.java`

Sends teacher password emails and student QR emails.

## `src/ppb/qrattend/ai`

### `GeminiAiClient.java`

Low-level Gemini request/response helper.

### `AiConfig.java`

Reads AI settings from config.

### `AiInsightRequest.java` and `AiInsightResponse.java`

Small request/response helpers reused by AI chat.

## `src/ppb/qrattend/qr`

### `QrCodeService.java`

Creates and reads QR codes.

### `QrScannerDialog.java`

The camera/photo QR scanner dialog used by the attendance page.
