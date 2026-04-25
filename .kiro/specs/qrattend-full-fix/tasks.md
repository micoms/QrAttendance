# Tasks

## Implementation Plan

### Phase 1: Code Simplification (Records, Streams, Switch, Comments)

- [x] 1.1 Replace all Java records in CoreModels.java with plain classes
  - Replace `Teacher`, `Section`, `Subject`, `Room`, `Student`, `Schedule`, `ScheduleRequest`, `StudentRemovalRequest`, `AttendanceSession`, `AttendanceRecord`, `EmailLog`, `ReportSummary` records with `public static final class` having `private final` fields, a constructor, and accessor methods named identically to the old record accessors (e.g., `id()`, `name()`, `fullName()`)
  - Keep `getTimeLabel()` on `Schedule` and `ScheduleRequest`
  - Keep `toPlainText()` on `ReportSummary`
  - Add `ABSENT("Absent")` to the `AttendanceStatus` enum

- [x] 1.2 Replace all stream/lambda chains in app/ and service/ packages with plain for-loops
  - Replace every `.stream().map().toArray()`, `.stream().map().toList()`, `.stream().map().toArray(String[]::new)`, `.stream().limit().toList()`, and similar chains
  - Files to update: `AttendanceScreen.java`, `AdminSchedulesScreen.java`, `AdminStudentsScreen.java`, `AppShell.java`, `AppStore.java`, `ReportsScreen.java`, `SectionsScreen.java`, `TeacherScheduleScreen.java`, `TeachersScreen.java`, `ReportService.java`, `ScheduleService.java`, `StudentService.java`, `TeacherService.java`

- [x] 1.3 Replace all switch expressions in app/ and service/ packages with if/else chains
  - Replace `switch` expressions in `AppShell.buildView()`, `AppShell.updateHeader()`, `TeacherService.mapEmailStatus()`, `StudentService.mapEmailStatus()`, `StudentService.mapRequestStatus()`, `ScheduleService.mapStatus()`
  - Use `if`/`else if`/`else` chains with `.equals()` comparisons

- [x] 1.4 Remove all "Mini-code guide" comment blocks from all source files
  - Remove from `ServiceResult.java` and any other files containing these comment blocks
  - Leave surrounding code unchanged

---

### Phase 2: Bug Fixes

- [x] 2.1 Fix Integer identity comparison in ReportsScreen.indexForId()
  - Change `ids.get(i) == targetId` to `ids.get(i).equals(targetId)`

- [x] 2.2 Fix warning detail extraction in AppStore.handleWrite()
  - Replace the pattern-matching instanceof expression with a plain `instanceof` check and explicit cast
  - Use `if (result.getData() instanceof String) { detail = (String) result.getData(); }` instead

- [x] 2.3 Add auto-clear banner timer to AppShell
  - Add `private Timer bannerClearTimer` field
  - Update `showMessage()` to stop any existing timer, then start a new one-shot 4000ms timer that clears the banner
  - When message is blank or null, clear immediately without starting a timer
  - Stop `bannerClearTimer` in the logout action listener alongside `attendanceTimer.stop()`

---

### Phase 3: UX Improvements

- [x] 3.1 Add Enter key action listener to PanelLogin
  - Add a `KeyAdapter` to both `emailField` and `passwordField` that invokes `loginHandler.loginRequested(...)` when `VK_ENTER` is pressed
  - Guard with `if (loginHandler != null)` before invoking

- [x] 3.2 Add Reason column to RequestsScreen schedule requests table
  - Change table model columns from `"Teacher", "Section", "Subject", "Room", "Day", "Time", "Status"` to `"Teacher", "Section", "Subject", "Room", "Day", "Time", "Reason", "Status"`
  - Add `request.reason()` at the correct column index in each row

- [x] 3.3 Add Export CSV button to ReportsScreen
  - Add an "Export CSV" button to `buildRecordsSection`
  - On click, show a `JFileChooser` with a `.csv` file filter
  - On confirm, call `shell.getStore().exportCsv(records, file)` and pass result to `shell.showResult()`
  - On cancel, take no action

- [x] 3.4 Add Mark All Absent button to AttendanceScreen
  - Add a "Mark All Absent" button to `buildManualSection`
  - Enable the button only when `session.status() == SessionStatus.OPEN`
  - On click, call `shell.getStore().markAllAbsent(shell.getCurrentUser().getUserId())` and refresh

---

### Phase 4: New Service Methods

- [x] 4.1 Add markAllAbsent to AttendanceService
  - Load current session; return failure if no open session
  - Load all students for the session's section
  - For each student without an existing attendance record for this session, insert an `ABSENT` record with method `MANUAL`
  - Return `ServiceResult<Integer>` with count of newly inserted records
  - Return success with message "All students are already marked." when count is 0

- [x] 4.2 Add exportCsv to ReportService
  - Accept `List<AttendanceRecord> records` and `java.io.File file`
  - Write UTF-8 CSV with header: `Student ID,Student Name,Section,Subject,Date/Time,Method,Status,Note`
  - Write one row per record; escape values containing commas or quotes
  - Return `ServiceResult<Integer>` with row count on success, failure on IOException

- [x] 4.3 Add updateTeacher and deactivateTeacher to TeacherService
  - `updateTeacher(int teacherId, String fullName, String email)`: validate non-blank, run UPDATE, return failure on duplicate email
  - `deactivateTeacher(int teacherId)`: set `is_active = 0`

- [x] 4.4 Add changePassword to TeacherService
  - `changePassword(int teacherId, String newPassword)`: validate length >= 8, hash password, run UPDATE setting `password_hash` and `must_change_password = 0`

- [x] 4.5 Add updateStudent and deactivateStudent to StudentService
  - `updateStudent(int studentId, int sectionId, String studentCode, String fullName, String email)`: validate all fields, run UPDATE, return failure on duplicate code or email
  - `deactivateStudent(int studentId)`: set `is_active = 0`

- [x] 4.6 Add updateSchedule and deactivateSchedule to ScheduleService
  - `updateSchedule(int scheduleId, int teacherId, int sectionId, int subjectId, int roomId, DayOfWeek day, LocalTime startTime, LocalTime endTime)`: validate, check conflict excluding self, run UPDATE
  - `deactivateSchedule(int scheduleId)`: set `is_active = 0`

- [x] 4.7 Improve schedule conflict error message in ScheduleService
  - Add private helper `findConflictingSchedule(...)` that returns the conflicting `Schedule` object (joining with subjects to get subject name)
  - Update `addSchedule()` and `reviewScheduleRequest()` to use this helper and include the conflicting class name and time range in the failure message

- [x] 4.8 Add renameSection and deleteSection to SectionService
  - `renameSection(int sectionId, String newName)`: validate non-blank, run UPDATE, return failure on duplicate name
  - `deleteSection(int sectionId)`: check for references in students and active schedules; return failure if referenced; otherwise DELETE

- [x] 4.9 Add renameSubject, deleteSubject, renameRoom, deleteRoom to ScheduleService
  - `renameSubject` / `renameRoom`: validate non-blank, run UPDATE, return failure on duplicate name
  - `deleteSubject` / `deleteRoom`: check for references in active schedules; return failure if referenced; otherwise DELETE

---

### Phase 5: AppStore Wiring

- [x] 5.1 Add new AppStore methods for all new service operations
  - `updateTeacher(int teacherId, String fullName, String email)`
  - `deactivateTeacher(int teacherId)`
  - `changePassword(int userId, String newPassword)`
  - `updateStudent(int studentId, int sectionId, String code, String name, String email)`
  - `deactivateStudent(int studentId)`
  - `updateSchedule(int scheduleId, int teacherId, int sectionId, int subjectId, int roomId, DayOfWeek day, LocalTime start, LocalTime end)`
  - `deactivateSchedule(int scheduleId)`
  - `markAllAbsent(int teacherId)`
  - `exportCsv(List<AttendanceRecord> records, java.io.File file)`
  - `renameSection(int sectionId, String newName)`
  - `deleteSection(int sectionId)`
  - `renameSubject(int subjectId, String newName)`
  - `deleteSubject(int subjectId)`
  - `renameRoom(int roomId, String newName)`
  - `deleteRoom(int roomId)`
  - All methods delegate to the appropriate service and wrap the result with `handleWrite()`

---

### Phase 6: Password Change Feature

- [x] 6.1 Add must_change_password support to ModelUser and DatabaseAuthenticationService
  - Add `private boolean mustChangePassword` field and `isMustChangePassword()` / `setMustChangePassword(boolean)` to `ModelUser`
  - Update `DatabaseAuthenticationService.authenticate()` to load `must_change_password` from the `users` table and set it on the returned `ModelUser`

- [x] 6.2 Create PasswordChangeScreen
  - New file `src/ppb/qrattend/app/PasswordChangeScreen.java`
  - Form with two password fields (new password, confirm password) and an inline error label
  - Validate: length >= 8, fields match
  - On success, call `shell.onPasswordChanged()`
  - On validation failure, show inline error label (do not dismiss form)

- [x] 6.3 Add password change gate to AppShell
  - Add `private boolean mustChangePassword` field, set from `user.isMustChangePassword()` in constructor
  - If `mustChangePassword` is true, set `selectedView = "password_change"` in constructor
  - Update `openView()` to return immediately if `mustChangePassword` is true
  - Update `buildView()` to return `PasswordChangeScreen.build(this)` when `selectedView.equals("password_change")`
  - Add `onPasswordChanged()` method that sets `mustChangePassword = false`, sets `selectedView = "home"`, and calls `refreshSelectedView()`

---

### Phase 7: CRUD UI Screens

- [x] 7.1 Add Edit and Deactivate to TeachersScreen
  - Add "Edit" and "Deactivate" buttons to the teacher list section
  - "Edit" populates an inline form (name field, email field, "Save Changes" button) with the selected teacher's current values
  - "Save Changes" calls `shell.getStore().updateTeacher(teacher.id(), name, email)` and refreshes
  - "Deactivate" calls `shell.getStore().deactivateTeacher(teacher.id())` and refreshes
  - Show teacher status as "Hidden" when `teacher.active()` is false

- [x] 7.2 Add Edit and Deactivate to AdminStudentsScreen
  - Add "Edit" and "Deactivate" buttons to the student list section
  - "Edit" populates an inline form (code, name, email, section combo) with the selected student's current values
  - "Save Changes" calls `shell.getStore().updateStudent(...)` and refreshes
  - "Deactivate" calls `shell.getStore().deactivateStudent(student.id())` and refreshes

- [x] 7.3 Add Edit and Delete to AdminSchedulesScreen
  - Add "Edit" and "Delete" buttons to the saved schedules table
  - "Edit" populates the existing schedule form fields with the selected schedule's values and shows a "Save Changes" button
  - "Save Changes" calls `shell.getStore().updateSchedule(...)` and refreshes
  - "Delete" calls `shell.getStore().deactivateSchedule(schedule.id())` and refreshes

- [x] 7.4 Add Edit and Delete to SectionsScreen for sections, subjects, and rooms
  - For each item in the Sections list, add "Edit" and "Delete" buttons
  - "Edit" shows an inline text field pre-filled with the current name and a "Save" button
  - "Save" calls `shell.getStore().renameSection(section.id(), newName)` and refreshes
  - "Delete" calls `shell.getStore().deleteSection(section.id())` and refreshes
  - Apply the same pattern to Subjects (using `renameSubject`/`deleteSubject`) and Rooms (using `renameRoom`/`deleteRoom`)

---

### Phase 8: Database Migration

- [x] 8.1 Add ABSENT to attendance_status enum in database
  - Create migration file `database/qrattend_absent_status_migration.sql`
  - Content: `ALTER TABLE attendance_records MODIFY attendance_status ENUM('PRESENT', 'LATE', 'ABSENT') NOT NULL DEFAULT 'PRESENT';`

---

### Phase 9: Tests

- [ ] 9.1 Set up test infrastructure
  - Add JUnit 4 jar to `lib/`
  - Add jqwik jar to `lib/` for property-based tests
  - Update `build.xml` to compile and run tests from the `test/` directory

- [ ] 9.2 Write property test for Property 1: indexForId correctness
  - Generate random `List<Integer>` with values 1–500 (crossing the 127 boundary)
  - Verify `indexForId` returns the correct index for any target in the list
  - Tag: `// Feature: qrattend-full-fix, Property 1: indexForId returns correct index for any Integer ID`

- [ ] 9.3 Write property test for Property 2: handleWrite detail extraction
  - Generate warning `ServiceResult` with data drawn from String, Integer, null, and Object instances
  - Verify `ActionResult.getDetail()` equals the String value when data is String, empty string otherwise
  - Tag: `// Feature: qrattend-full-fix, Property 2: warning detail is String data or empty string`

- [ ] 9.4 Write property test for Property 3: conflict message names conflicting class
  - Generate overlapping schedule pairs for the same teacher/day
  - Verify failure message contains the first schedule's subject name and time range
  - Tag: `// Feature: qrattend-full-fix, Property 3: conflict message names the conflicting class`

- [ ] 9.5 Write property test for Property 4: reason column never null
  - Generate random `ScheduleRequest` lists with varying reason strings including blank
  - Verify table model Reason column is never null
  - Tag: `// Feature: qrattend-full-fix, Property 4: reason column contains request.reason() for every row`

- [ ] 9.6 Write property test for Property 5: CSV row count
  - Generate random non-empty `AttendanceRecord` lists
  - Verify written file has exactly `records.size() + 1` lines
  - Tag: `// Feature: qrattend-full-fix, Property 5: CSV line count equals record count plus one header`

- [ ] 9.7 Write property test for Property 6: markAllAbsent inserts exactly unmarked students
  - Generate sessions with N students and M pre-marked (0 ≤ M ≤ N)
  - Verify exactly N - M ABSENT records are inserted
  - Tag: `// Feature: qrattend-full-fix, Property 6: markAllAbsent inserts exactly N-M absent records`

- [ ] 9.8 Write property test for Property 7: changePassword clears must_change_password
  - Generate valid passwords (length 8–30)
  - Verify must_change_password = 0 and hash matches after success
  - Tag: `// Feature: qrattend-full-fix, Property 7: changePassword sets must_change_password to 0`

- [ ] 9.9 Write property test for Property 8: short passwords rejected
  - Generate strings of length 0–7
  - Verify changePassword returns failure and DB is unchanged
  - Tag: `// Feature: qrattend-full-fix, Property 8: passwords shorter than 8 characters are rejected`

- [ ] 9.10 Write property test for Property 9: navigation blocked while must_change_password
  - Generate any view key from the navigation map
  - Verify openView leaves selectedView unchanged when mustChangePassword is true
  - Tag: `// Feature: qrattend-full-fix, Property 9: navigation is blocked while must_change_password is true`

- [ ] 9.11 Write property test for Property 10: teacher update round-trip
  - Generate valid name and email pairs
  - Verify loaded teacher matches submitted values after updateTeacher
  - Tag: `// Feature: qrattend-full-fix, Property 10: updateTeacher persists new name and email`

- [ ] 9.12 Write property test for Property 11: student update round-trip
  - Generate valid student fields
  - Verify loaded student matches submitted values after updateStudent
  - Tag: `// Feature: qrattend-full-fix, Property 11: updateStudent persists all changed fields`

- [ ] 9.13 Write property test for Property 12: section rename round-trip
  - Generate valid section names
  - Verify loaded section name matches after renameSection
  - Tag: `// Feature: qrattend-full-fix, Property 12: renameSection persists the new name`

- [ ] 9.14 Write property test for Property 13: in-use section delete blocked
  - Generate sections with at least one referencing student or schedule
  - Verify deleteSection returns failure and section still exists
  - Tag: `// Feature: qrattend-full-fix, Property 13: sections referenced by students or schedules cannot be deleted`
