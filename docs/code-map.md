# Code Map

This file is the easiest map of the active codebase.

Use it when you want to know:

- which file starts the app
- which file builds a screen
- which file talks to the database
- which file sends email
- which file handles AI
- which file handles QR

## 1. Start here

### `src/ppb/qrattend/main`

- [`Main.java`](../src/ppb/qrattend/main/Main.java)
  - app starting point
  - login handoff
  - opens the workspace after sign in

## 2. Login files

### `src/ppb/qrattend/component/login`

- [`PanelCover.java`](../src/ppb/qrattend/component/login/PanelCover.java)
  - left side of sign in
  - role switch between admin and teacher

- [`PanelLogin.java`](../src/ppb/qrattend/component/login/PanelLogin.java)
  - email and password fields
  - sign in button

## 3. Main app shell

### `src/ppb/qrattend/app`

- [`AppShell.java`](../src/ppb/qrattend/app/AppShell.java)
  - left menu
  - page title
  - banner messages
  - right-side help panel
  - screen switching

- [`AppTheme.java`](../src/ppb/qrattend/app/AppTheme.java)
  - colors
  - fonts
  - button styles
  - field styles
  - table styles

- [`AppFlowPanels.java`](../src/ppb/qrattend/app/AppFlowPanels.java)
  - simple action tiles
  - simple helper panels

- [`AppDataStore.java`](../src/ppb/qrattend/app/AppDataStore.java)
  - one data facade for the UI
  - screens call this instead of calling services directly

## 4. Admin screen files

- [`AdminDashboardScreen.java`](../src/ppb/qrattend/app/AdminDashboardScreen.java)
  - admin home
  - task buttons for setup flow

- [`TeachersScreen.java`](../src/ppb/qrattend/app/TeachersScreen.java)
  - add teacher
  - send password again
  - reset password

- [`AdminStudentsScreen.java`](../src/ppb/qrattend/app/AdminStudentsScreen.java)
  - add student
  - assign teacher and section
  - send QR again

- [`AdminSchedulesScreen.java`](../src/ppb/qrattend/app/AdminSchedulesScreen.java)
  - set class schedules

- [`RequestsScreen.java`](../src/ppb/qrattend/app/RequestsScreen.java)
  - approve or reject schedule changes
  - approve or reject class list changes

- [`ReportsScreen.java`](../src/ppb/qrattend/app/ReportsScreen.java)
  - report filter
  - summary
  - attendance records

## 5. Teacher screen files

- [`TeacherDashboardScreen.java`](../src/ppb/qrattend/app/TeacherDashboardScreen.java)
  - teacher home
  - main task buttons

- [`AttendanceScreen.java`](../src/ppb/qrattend/app/AttendanceScreen.java)
  - start class
  - scan student QR
  - mark attendance without QR

- [`TeacherRosterScreen.java`](../src/ppb/qrattend/app/TeacherRosterScreen.java)
  - class list
  - ask admin to remove a student

- [`TeacherScheduleScreen.java`](../src/ppb/qrattend/app/TeacherScheduleScreen.java)
  - view schedule
  - ask for schedule change

- [`ReportsScreen.java`](../src/ppb/qrattend/app/ReportsScreen.java)
  - teacher reports
  - ask AI

## 6. Store helpers

### `src/ppb/qrattend/app/store`

- [`StoreMessages.java`](../src/ppb/qrattend/app/store/StoreMessages.java)
  - cleans messages before the UI shows them

- [`StoreTeacherAssistantSupport.java`](../src/ppb/qrattend/app/store/StoreTeacherAssistantSupport.java)
  - builds teacher AI context
  - stores local page conversation history

## 7. Shared models

### `src/ppb/qrattend/model`

- [`AppDomain.java`](../src/ppb/qrattend/model/AppDomain.java)
  - shared app data objects
  - enums
  - labels and date format helpers

- [`ModelUser.java`](../src/ppb/qrattend/model/ModelUser.java)
  - current logged-in user identity

## 8. Database files

### `src/ppb/qrattend/db`

- [`DatabaseConfig.java`](../src/ppb/qrattend/db/DatabaseConfig.java)
  - reads DB settings

- [`DatabaseManager.java`](../src/ppb/qrattend/db/DatabaseManager.java)
  - opens MariaDB connections

- [`DatabaseAuthenticationService.java`](../src/ppb/qrattend/db/DatabaseAuthenticationService.java)
  - sign in logic

- [`PasswordUtil.java`](../src/ppb/qrattend/db/PasswordUtil.java)
  - password hashing

- [`SecurityUtil.java`](../src/ppb/qrattend/db/SecurityUtil.java)
  - hashing and safe preview helpers

## 9. Business services

### `src/ppb/qrattend/service`

- [`TeacherService.java`](../src/ppb/qrattend/service/TeacherService.java)
  - teacher account actions

- [`StudentService.java`](../src/ppb/qrattend/service/StudentService.java)
  - student creation
  - QR sending
  - class list removal requests

- [`ScheduleService.java`](../src/ppb/qrattend/service/ScheduleService.java)
  - schedule setup
  - schedule change requests

- [`AttendanceService.java`](../src/ppb/qrattend/service/AttendanceService.java)
  - attendance sessions
  - QR attendance
  - manual attendance

- [`ReportService.java`](../src/ppb/qrattend/service/ReportService.java)
  - report data
  - dashboard counts

- [`EmailDispatchService.java`](../src/ppb/qrattend/service/EmailDispatchService.java)
  - email log queue and update logic

- [`AuditLogService.java`](../src/ppb/qrattend/service/AuditLogService.java)
  - audit log writes and reads

- [`AiInsightService.java`](../src/ppb/qrattend/service/AiInsightService.java)
  - AI request and saved insight logic

- [`AutomationService.java`](../src/ppb/qrattend/service/AutomationService.java)
  - backend automation logic

- [`IoTDeviceService.java`](../src/ppb/qrattend/service/IoTDeviceService.java)
  - backend device logic

- [`ServiceResult.java`](../src/ppb/qrattend/service/ServiceResult.java)
  - common service result wrapper

## 10. AI files

### `src/ppb/qrattend/ai`

- [`AiConfig.java`](../src/ppb/qrattend/ai/AiConfig.java)
  - AI config values

- [`AiInsightRequest.java`](../src/ppb/qrattend/ai/AiInsightRequest.java)
  - AI request data

- [`AiInsightResponse.java`](../src/ppb/qrattend/ai/AiInsightResponse.java)
  - AI response data

- [`AiClient.java`](../src/ppb/qrattend/ai/AiClient.java)
  - AI client interface

- [`GeminiAiClient.java`](../src/ppb/qrattend/ai/GeminiAiClient.java)
  - Gemini implementation

## 11. Email files

### `src/ppb/qrattend/email`

- [`ResendConfig.java`](../src/ppb/qrattend/email/ResendConfig.java)
  - Resend settings

- [`ResendEmailClient.java`](../src/ppb/qrattend/email/ResendEmailClient.java)
  - sends teacher and student emails

## 12. QR files

### `src/ppb/qrattend/qr`

- [`QrCodeService.java`](../src/ppb/qrattend/qr/QrCodeService.java)
  - create QR images
  - decode QR values

- [`QrScannerDialog.java`](../src/ppb/qrattend/qr/QrScannerDialog.java)
  - camera or image scan window
