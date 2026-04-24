# Code Map

This file is a beginner-friendly map of the active codebase.

## 1. App Entry

### `src/ppb/qrattend/main`

- [`Main.java`](../src/ppb/qrattend/main/Main.java)
  - Starts the Swing app
  - Shows login
  - Opens the workspace after a successful sign-in

## 2. Login UI

### `src/ppb/qrattend/component/login`

- [`PanelCover.java`](../src/ppb/qrattend/component/login/PanelCover.java)
  - Left side of the login screen
  - Lets the user switch between admin and teacher sign-in

- [`PanelLogin.java`](../src/ppb/qrattend/component/login/PanelLogin.java)
  - Right side of the login screen
  - Collects email and password

## 3. Main App UI

### `src/ppb/qrattend/app`

- [`AppShell.java`](../src/ppb/qrattend/app/AppShell.java)
  - Main shell layout
  - Navigation
  - Header
  - Banner
  - Right-side detail panel
  - Switches between pages

- [`AdminDashboardScreen.java`](../src/ppb/qrattend/app/AdminDashboardScreen.java)
  - Admin dashboard page

- [`TeacherDashboardScreen.java`](../src/ppb/qrattend/app/TeacherDashboardScreen.java)
  - Teacher dashboard page

- [`TeachersScreen.java`](../src/ppb/qrattend/app/TeachersScreen.java)
  - Teacher management page

- [`AdminStudentsScreen.java`](../src/ppb/qrattend/app/AdminStudentsScreen.java)
  - Admin student management page

- [`AdminSchedulesScreen.java`](../src/ppb/qrattend/app/AdminSchedulesScreen.java)
  - Admin schedule management page

- [`RequestsScreen.java`](../src/ppb/qrattend/app/RequestsScreen.java)
  - Admin approval page

- [`AttendanceScreen.java`](../src/ppb/qrattend/app/AttendanceScreen.java)
  - Teacher attendance page

- [`TeacherRosterScreen.java`](../src/ppb/qrattend/app/TeacherRosterScreen.java)
  - Teacher class list page

- [`TeacherScheduleScreen.java`](../src/ppb/qrattend/app/TeacherScheduleScreen.java)
  - Teacher schedule request page

- [`ReportsScreen.java`](../src/ppb/qrattend/app/ReportsScreen.java)
  - Reports page for admin and teacher

- [`AppTheme.java`](../src/ppb/qrattend/app/AppTheme.java)
  - Shared UI colors, fonts, cards, borders, and table styling

- [`AppDataStore.java`](../src/ppb/qrattend/app/AppDataStore.java)
  - UI-facing data facade
  - Calls service classes
  - Returns simple results for the UI

## 4. Store Helpers

### `src/ppb/qrattend/app/store`

- [`StoreMessages.java`](../src/ppb/qrattend/app/store/StoreMessages.java)
  - Cleans service messages into simple user-facing text

- [`StoreTeacherAssistantSupport.java`](../src/ppb/qrattend/app/store/StoreTeacherAssistantSupport.java)
  - Builds the teacher `Ask AI` chat context
  - Stores local conversation history

## 5. Shared Models

### `src/ppb/qrattend/model`

- [`AppDomain.java`](../src/ppb/qrattend/model/AppDomain.java)
  - Shared enums and data objects used by the UI and services

- [`ModelUser.java`](../src/ppb/qrattend/model/ModelUser.java)
  - Logged-in user identity used in the workspace

## 6. Database Layer

### `src/ppb/qrattend/db`

- [`DatabaseConfig.java`](../src/ppb/qrattend/db/DatabaseConfig.java)
  - Reads database settings

- [`DatabaseManager.java`](../src/ppb/qrattend/db/DatabaseManager.java)
  - Opens MariaDB connections

- [`DatabaseAuthenticationService.java`](../src/ppb/qrattend/db/DatabaseAuthenticationService.java)
  - Handles sign-in

- [`PasswordUtil.java`](../src/ppb/qrattend/db/PasswordUtil.java)
  - Hashes passwords

- [`SecurityUtil.java`](../src/ppb/qrattend/db/SecurityUtil.java)
  - Security helpers such as hashing and safe previews

## 7. Service Layer

### `src/ppb/qrattend/service`

- [`TeacherService.java`](../src/ppb/qrattend/service/TeacherService.java)
  - Teacher account actions

- [`StudentService.java`](../src/ppb/qrattend/service/StudentService.java)
  - Student creation, QR sending, and roster requests

- [`ScheduleService.java`](../src/ppb/qrattend/service/ScheduleService.java)
  - Schedule creation and schedule change requests

- [`AttendanceService.java`](../src/ppb/qrattend/service/AttendanceService.java)
  - Attendance sessions and attendance records

- [`ReportService.java`](../src/ppb/qrattend/service/ReportService.java)
  - Dashboard counts and report summaries

- [`EmailDispatchService.java`](../src/ppb/qrattend/service/EmailDispatchService.java)
  - Email log reads and updates

- [`AuditLogService.java`](../src/ppb/qrattend/service/AuditLogService.java)
  - Audit log access

- [`IoTDeviceService.java`](../src/ppb/qrattend/service/IoTDeviceService.java)
  - Backend device logic

- [`AutomationService.java`](../src/ppb/qrattend/service/AutomationService.java)
  - Backend automation logic

- [`AiInsightService.java`](../src/ppb/qrattend/service/AiInsightService.java)
  - AI request and insight storage logic

- [`ServiceResult.java`](../src/ppb/qrattend/service/ServiceResult.java)
  - Common result wrapper for services

## 8. AI Layer

### `src/ppb/qrattend/ai`

- [`AiConfig.java`](../src/ppb/qrattend/ai/AiConfig.java)
  - Reads AI settings

- [`AiInsightRequest.java`](../src/ppb/qrattend/ai/AiInsightRequest.java)
  - AI request object

- [`AiInsightResponse.java`](../src/ppb/qrattend/ai/AiInsightResponse.java)
  - AI response object

- [`AiClient.java`](../src/ppb/qrattend/ai/AiClient.java)
  - AI client interface

- [`GeminiAiClient.java`](../src/ppb/qrattend/ai/GeminiAiClient.java)
  - Gemini implementation

## 9. Email Layer

### `src/ppb/qrattend/email`

- [`ResendConfig.java`](../src/ppb/qrattend/email/ResendConfig.java)
  - Reads Resend settings

- [`ResendEmailClient.java`](../src/ppb/qrattend/email/ResendEmailClient.java)
  - Sends teacher and student emails

## 10. QR Layer

### `src/ppb/qrattend/qr`

- [`QrCodeService.java`](../src/ppb/qrattend/qr/QrCodeService.java)
  - Creates QR images
  - Decodes QR values

- [`QrScannerDialog.java`](../src/ppb/qrattend/qr/QrScannerDialog.java)
  - Camera/image scanner window

## 11. Custom Swing Components

### `src/ppb/qrattend/swing`

- Custom text fields, buttons, and UI widgets used by the login screen and app UI
