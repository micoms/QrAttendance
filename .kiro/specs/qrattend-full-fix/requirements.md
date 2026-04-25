# Requirements Document

## Introduction

This document covers a comprehensive fix and improvement pass for QRAttend, a Java Swing desktop application used by schools to manage attendance via QR codes. The work falls into four categories:

1. **Bug fixes** — correct logic errors that cause silent failures or wrong behavior.
2. **Missing CRUD operations** — add edit and delete actions for teachers, students, schedules, sections, subjects, and rooms.
3. **UX improvements** — small changes that make the application faster and clearer to use.
4. **Code simplification** — rewrite stream/lambda chains, switch expressions, and Java records into plain beginner-style Java so the codebase is easier to read and maintain.

---

## Glossary

- **Application**: The QRAttend Java Swing desktop application.
- **AppShell**: The main window class that hosts the sidebar, header, banner, and content area.
- **AppStore**: The facade class that routes UI actions to service classes and returns `ActionResult` objects.
- **AttendanceService**: The service class that manages attendance sessions and records.
- **AttendanceScreen**: The teacher-facing screen for scanning QR codes and marking attendance.
- **Banner**: The colored notification strip shown at the top of the content area inside AppShell.
- **PanelLogin**: The login form panel used by both admin and teacher roles.
- **ReportsScreen**: The screen that shows attendance summaries and filtered record tables.
- **ReportService**: The service class that queries and aggregates attendance records.
- **RequestsScreen**: The admin screen for reviewing schedule change and student removal requests.
- **ScheduleService**: The service class that manages schedules and schedule-change requests.
- **SectionsScreen**: The admin screen for managing sections, subjects, and rooms.
- **SectionService**: The service class that manages sections.
- **TeachersScreen**: The admin screen for managing teacher accounts.
- **TeacherService**: The service class that manages teacher accounts.
- **StudentService**: The service class that manages student records.
- **AdminStudentsScreen**: The admin screen for importing and managing students.
- **TeacherRosterScreen**: The teacher-facing screen showing the class list.
- **AdminSchedulesScreen**: The admin screen for creating and managing schedules.
- **TeacherScheduleScreen**: The teacher-facing screen showing personal schedules.
- **ServiceResult**: A generic wrapper returned by all service methods, carrying success/warning/failure state, a message, and optional data.
- **ActionResult**: The AppStore-level wrapper returned to the UI, carrying success/warning/failure state, a message, and an optional detail string.
- **Integer object comparison**: Using `==` to compare two `Integer` objects, which only works reliably for values −128 to 127 due to JVM integer caching.
- **Auto-clear timer**: A `javax.swing.Timer` that fires once after a fixed delay to clear the banner message.
- **CSV**: Comma-separated values file format used for data export.
- **must_change_password**: A flag column in the `users` table (value `1`) that signals a teacher must set a new password before using the application.
- **Mark all absent**: An action that inserts an attendance record with status `ABSENT` for every student in the current session who has not yet been marked.

---

## Requirements

---

### Requirement 1: Fix Integer Identity Comparison in ReportsScreen

**User Story:** As an admin or teacher, I want the report filter to correctly restore the previously selected teacher, section, and subject, so that the filter does not silently reset to "All" for IDs greater than 127.

#### Acceptance Criteria

1. WHEN `ReportsScreen.indexForId()` compares a list element to a target ID, THE `ReportsScreen` SHALL use `.equals()` instead of `==` to compare the two `Integer` objects.
2. WHEN a teacher, section, or subject with an ID greater than 127 is selected and the report is refreshed, THE `ReportsScreen` SHALL restore the correct combo-box selection.
3. WHEN a teacher, section, or subject with an ID of 127 or less is selected and the report is refreshed, THE `ReportsScreen` SHALL restore the correct combo-box selection.

---

### Requirement 2: Fix Blank Warning Detail in AppStore.handleWrite()

**User Story:** As a user, I want warning banners to show the full detail message, so that I can understand what went wrong when an action partially succeeds.

#### Acceptance Criteria

1. WHEN `AppStore.handleWrite()` receives a `ServiceResult` with warning state, THE `AppStore` SHALL extract the detail string from `result.getMessage()` and `result.getData()` only when `getData()` returns a `String`.
2. WHEN `result.getData()` is not a `String` instance, THE `AppStore` SHALL use an empty string as the detail so the banner shows only the main message without a null or object reference.
3. WHEN a warning `ActionResult` is passed to `AppShell.showResult()`, THE `AppShell` SHALL display the combined message and detail in the banner.

---

### Requirement 3: Auto-Clear Banner After 4 Seconds

**User Story:** As a user, I want status banners to disappear on their own after a short time, so that old messages do not stay on screen and confuse me.

#### Acceptance Criteria

1. WHEN `AppShell.showMessage()` is called with a non-blank message, THE `AppShell` SHALL start a one-shot `javax.swing.Timer` that fires after 4 000 milliseconds.
2. WHEN the auto-clear timer fires, THE `AppShell` SHALL set the banner message to blank and repaint the banner panel.
3. WHEN `AppShell.showMessage()` is called again before the previous timer has fired, THE `AppShell` SHALL stop the previous timer before starting a new one, so only one timer is active at a time.
4. WHEN `AppShell.showMessage()` is called with a blank or null message, THE `AppShell` SHALL clear the banner immediately without starting a timer.

---

### Requirement 4: Enter Key Submits the Login Form

**User Story:** As a teacher or admin, I want to press Enter to log in, so that I do not have to move my hand to the mouse after typing my password.

#### Acceptance Criteria

1. WHEN the Enter key is pressed while the email field in `PanelLogin` has focus, THE `PanelLogin` SHALL invoke the same login action as clicking the Sign In button.
2. WHEN the Enter key is pressed while the password field in `PanelLogin` has focus, THE `PanelLogin` SHALL invoke the same login action as clicking the Sign In button.
3. WHEN no `LoginHandler` has been set, THE `PanelLogin` SHALL take no action when Enter is pressed.

---

### Requirement 5: Show Conflicting Class Name in Schedule Conflict Error

**User Story:** As an admin, I want the schedule conflict error to name the class that is already booked, so that I know which existing schedule is blocking the new one.

#### Acceptance Criteria

1. WHEN `ScheduleService.addSchedule()` detects a time overlap for a teacher, THE `ScheduleService` SHALL return a failure message that includes the subject name and time range of the conflicting schedule.
2. WHEN `ScheduleService.reviewScheduleRequest()` detects a time overlap during approval, THE `ScheduleService` SHALL return a failure message that includes the subject name and time range of the conflicting schedule.
3. WHEN no conflict exists, THE `ScheduleService` SHALL proceed normally without including conflict details in the result message.

---

### Requirement 6: Show Reason Column in Schedule Requests Table

**User Story:** As an admin, I want to see the teacher's reason in the schedule requests table, so that I can decide whether to approve or reject without opening a separate detail view.

#### Acceptance Criteria

1. WHEN `RequestsScreen` builds the schedule requests table, THE `RequestsScreen` SHALL include a "Reason" column that displays `ScheduleRequest.reason()` for each row.
2. WHEN a schedule request has a blank reason, THE `RequestsScreen` SHALL display an empty cell in the Reason column rather than null or an error.

---

### Requirement 7: Export Attendance Records to CSV

**User Story:** As an admin or teacher, I want to export the current attendance report to a CSV file, so that I can open it in a spreadsheet for further analysis.

#### Acceptance Criteria

1. WHEN the user clicks the Export CSV button on `ReportsScreen`, THE `ReportsScreen` SHALL open a file-save dialog so the user can choose the destination file name and folder.
2. WHEN the user confirms the file path, THE `ReportService` SHALL write one header row followed by one data row per `AttendanceRecord` in the current filtered result set.
3. THE CSV file SHALL contain the columns: Student ID, Student Name, Section, Subject, Date/Time, Method, Status, Note — in that order.
4. WHEN the export completes successfully, THE `AppShell` SHALL display a success banner with the number of rows written.
5. IF the file cannot be written, THEN THE `AppShell` SHALL display a failure banner with a plain-language error message.
6. WHEN the user cancels the file-save dialog, THE `ReportsScreen` SHALL take no action and show no banner.

---

### Requirement 8: Mark All Absent Button on Attendance Screen

**User Story:** As a teacher, I want to mark all students who have not yet been scanned as absent with one click, so that I do not have to mark each missing student individually at the end of class.

#### Acceptance Criteria

1. WHEN a class session is open, THE `AttendanceScreen` SHALL display a "Mark All Absent" button.
2. WHEN the teacher clicks "Mark All Absent", THE `AttendanceService` SHALL insert an attendance record with status `ABSENT` for every student in the current session's section who does not already have a record for that session.
3. WHEN all students already have a record for the current session, THE `AppShell` SHALL display a message stating that all students are already marked.
4. WHEN no class session is open, THE `AttendanceScreen` SHALL disable the "Mark All Absent" button.
5. WHEN the mark-all-absent action completes, THE `AttendanceScreen` SHALL refresh to show the updated attendance list.

---

### Requirement 9: Password Change Screen for Teachers

**User Story:** As a teacher whose account was just created or whose password was reset, I want to be prompted to set a new password immediately after logging in, so that I replace the temporary password before using the application.

#### Acceptance Criteria

1. WHEN a teacher logs in and `must_change_password = 1` in the database, THE `AppShell` SHALL display a password change screen instead of the normal teacher dashboard.
2. WHEN the teacher submits a new password, THE `TeacherService` SHALL update the password hash and set `must_change_password = 0` for that teacher's user record.
3. WHEN the new password is fewer than 8 characters, THE password change screen SHALL display an inline error and SHALL NOT submit the change.
4. WHEN the new password and the confirmation field do not match, THE password change screen SHALL display an inline error and SHALL NOT submit the change.
5. WHEN the password change succeeds, THE `AppShell` SHALL navigate to the normal teacher dashboard and display a success banner.
6. WHILE `must_change_password = 1`, THE `AppShell` SHALL prevent the teacher from navigating to any other screen.

---

### Requirement 10: Edit and Delete Teachers

**User Story:** As an admin, I want to edit a teacher's name and email and to deactivate a teacher account, so that I can keep the teacher list accurate without deleting records that are referenced by attendance history.

#### Acceptance Criteria

1. WHEN the admin selects a teacher in `TeachersScreen` and clicks "Edit", THE `TeachersScreen` SHALL populate an edit form with the teacher's current name and email.
2. WHEN the admin submits the edit form, THE `TeacherService` SHALL update the teacher's `full_name` and `email` in the `users` table.
3. WHEN the admin selects a teacher and clicks "Deactivate", THE `TeacherService` SHALL set `is_active = 0` for that teacher's user record.
4. WHEN a teacher is deactivated, THE `TeachersScreen` SHALL show the teacher's status as "Hidden" in the list.
5. IF the updated email is already used by another user, THEN THE `TeacherService` SHALL return a failure result with a plain-language message.

---

### Requirement 11: Edit and Delete Students

**User Story:** As an admin, I want to edit a student's details and to deactivate a student record, so that I can correct mistakes without losing attendance history.

#### Acceptance Criteria

1. WHEN the admin selects a student in `AdminStudentsScreen` and clicks "Edit", THE `AdminStudentsScreen` SHALL populate an edit form with the student's current student code, full name, email, and section.
2. WHEN the admin submits the edit form, THE `StudentService` SHALL update the student's `student_code`, `full_name`, `email`, and `section_id` in the `students` table.
3. WHEN the admin selects a student and clicks "Deactivate", THE `StudentService` SHALL set `is_active = 0` for that student record.
4. IF the updated student code or email is already used by another student, THEN THE `StudentService` SHALL return a failure result with a plain-language message.
5. WHEN a teacher views `TeacherRosterScreen`, THE `TeacherRosterScreen` SHALL not show deactivated students.

---

### Requirement 12: Edit and Delete Schedules

**User Story:** As an admin, I want to edit or delete a schedule directly without going through the request workflow, so that I can fix mistakes immediately.

#### Acceptance Criteria

1. WHEN the admin selects a schedule in `AdminSchedulesScreen` and clicks "Edit", THE `AdminSchedulesScreen` SHALL populate an edit form with the schedule's current teacher, section, subject, room, day, start time, and end time.
2. WHEN the admin submits the edited schedule, THE `ScheduleService` SHALL update the schedule row and check for time conflicts, excluding the schedule being edited from the conflict check.
3. WHEN the admin selects a schedule and clicks "Delete", THE `ScheduleService` SHALL set `is_active = 0` for that schedule row.
4. IF the edited schedule overlaps with another active schedule for the same teacher, THEN THE `ScheduleService` SHALL return a failure result naming the conflicting class.
5. WHEN a teacher views `TeacherScheduleScreen`, THE `TeacherScheduleScreen` SHALL not show schedules where `is_active = 0`.

---

### Requirement 13: Edit and Delete Sections, Subjects, and Rooms

**User Story:** As an admin, I want to rename or remove sections, subjects, and rooms, so that I can correct typos and remove entries that are no longer used.

#### Acceptance Criteria

1. WHEN the admin selects a section in `SectionsScreen` and clicks "Edit", THE `SectionsScreen` SHALL show an inline text field pre-filled with the current section name.
2. WHEN the admin submits the renamed section, THE `SectionService` SHALL update the `section_name` in the `sections` table.
3. WHEN the admin selects a section and clicks "Delete", THE `SectionService` SHALL attempt to delete the section row.
4. IF the section is referenced by at least one student or schedule, THEN THE `SectionService` SHALL return a failure result explaining that the section is still in use.
5. THE same edit and delete behavior described in criteria 1–4 SHALL apply to subjects (via `ScheduleService`) and rooms (via `ScheduleService`).
6. IF a subject or room is referenced by at least one active schedule, THEN THE `ScheduleService` SHALL return a failure result explaining that the item is still in use.

---

### Requirement 14: Replace Streams and Lambdas with Plain For-Loops

**User Story:** As a student developer maintaining this codebase, I want all stream/lambda chains replaced with plain for-loops, so that the code is easier to read and debug without knowledge of the Streams API.

#### Acceptance Criteria

1. THE `Application` SHALL contain no calls to `.stream()`, `.map()`, `.filter()`, `.collect()`, `.toList()`, `.forEach()`, or other `java.util.stream` methods in any class under `src/ppb/qrattend/app/` or `src/ppb/qrattend/service/`.
2. WHEN a stream chain was used to build a list, THE replacement code SHALL use a plain `for` loop that adds items to an `ArrayList`.
3. WHEN a stream chain was used to convert a list to an array for a combo box, THE replacement code SHALL use a plain `for` loop or a pre-built `String[]` array.

---

### Requirement 15: Replace Switch Expressions with If/Else Blocks

**User Story:** As a student developer, I want switch expressions replaced with if/else blocks, so that the control flow is immediately recognizable without knowing Java 14+ syntax.

#### Acceptance Criteria

1. THE `Application` SHALL contain no switch expressions (the `switch (...) { case X -> ... }` form) in any class under `src/ppb/qrattend/app/` or `src/ppb/qrattend/service/`.
2. WHEN a switch expression was used to map a string to an enum value, THE replacement code SHALL use a series of `if`/`else if` comparisons.
3. WHEN a switch expression was used to select a screen panel, THE replacement code SHALL use a series of `if`/`else if` comparisons.

---

### Requirement 16: Replace Java Records with Plain Classes

**User Story:** As a student developer, I want Java records replaced with plain classes that have fields and getters, so that the model classes are understandable without knowledge of the Java 16 records feature.

#### Acceptance Criteria

1. THE `CoreModels` class SHALL contain no `record` declarations.
2. WHEN a record is replaced, THE replacement class SHALL have `private final` fields, a constructor that sets all fields, and a public getter method for each field.
3. WHEN a record accessor such as `teacher.id()` was used in calling code, THE replacement getter SHALL be named identically (e.g., `getId()` is NOT acceptable; the getter SHALL be named `id()`) so that no call sites need to change.
4. THE `ServiceResult` class SHALL remain generic and SHALL NOT be converted to a record.

---

### Requirement 17: Remove Mini-Code Guide Comments

**User Story:** As a student developer, I want the inline "Mini-code guide" comment blocks removed, so that the source files are shorter and the real logic is easier to find.

#### Acceptance Criteria

1. THE `Application` SHALL contain no comment blocks that begin with the text "Mini-code guide" in any source file.
2. WHEN a mini-code guide comment is removed, THE surrounding code SHALL remain unchanged and fully functional.
