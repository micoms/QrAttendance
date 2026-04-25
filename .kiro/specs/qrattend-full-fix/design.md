# Design Document: QRAttend Full Fix

## Overview

This document describes the technical design for the comprehensive fix and improvement pass on the QRAttend Java Swing desktop application. The work covers four categories: bug fixes, missing CRUD operations, UX improvements, and code simplification. All changes must produce beginner-style Java — plain for-loops, if/else chains, no streams, no records, no switch expressions — so the codebase remains accessible to student developers.

The application uses Java Swing for the UI, MariaDB/MySQL via JDBC for persistence, and Apache Ant for builds. There are no external JSON or testing libraries beyond what is already in `lib/`.

---

## Architecture

The application follows a layered architecture that is already established:

```
UI Layer (app/)
    ↓ calls
AppStore (facade)
    ↓ delegates to
Service Layer (service/)
    ↓ uses
DatabaseManager / JDBC
    ↓ talks to
MariaDB
```

All changes stay within this existing structure. No new layers are introduced.

### Key Architectural Constraints

- **No streams or lambdas** in `app/` or `service/` packages (simple `ActionListener` lambdas in UI wiring are acceptable).
- **No switch expressions** anywhere in `app/` or `service/`.
- **No Java records** in `CoreModels.java` — replace with plain classes.
- **No mini-code guide comments** in any source file.
- All new service methods return `ServiceResult<T>`.
- All new AppStore methods return `ActionResult`.

---

## Components and Interfaces

### CoreModels.java — Record Replacement

Every `record` in `CoreModels.java` is replaced with a plain class following this pattern:

```java
public static final class Teacher {
    private final int id;
    private final String fullName;
    private final String email;
    private final boolean active;
    private final EmailStatus emailStatus;

    public Teacher(int id, String fullName, String email,
                   boolean active, EmailStatus emailStatus) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.active = active;
        this.emailStatus = emailStatus;
    }

    public int id()               { return id; }
    public String fullName()      { return fullName; }
    public String email()         { return email; }
    public boolean active()       { return active; }
    public EmailStatus emailStatus() { return emailStatus; }
}
```

**Critical rule:** accessor names must match the old record accessor names exactly (e.g., `id()` not `getId()`). This means zero call-site changes are needed anywhere in the codebase.

Records to replace: `Teacher`, `Section`, `Subject`, `Room`, `Student`, `Schedule`, `ScheduleRequest`, `StudentRemovalRequest`, `AttendanceSession`, `AttendanceRecord`, `EmailLog`, `ReportSummary`.

`Schedule` and `ScheduleRequest` also have a `getTimeLabel()` method — keep it as-is.
`ReportSummary` has a `toPlainText()` method — keep it as-is.

### Stream/Lambda Replacement Pattern

Every stream chain is replaced with a plain for-loop. Example:

```java
// Before (stream)
String[] names = teachers.stream().map(Teacher::fullName).toArray(String[]::new);

// After (plain loop)
String[] names = new String[teachers.size()];
for (int i = 0; i < teachers.size(); i++) {
    names[i] = teachers.get(i).fullName();
}
```

For `.toList()` calls:
```java
// Before
List<Integer> ids = teachers.stream().map(Teacher::id).toList();

// After
List<Integer> ids = new ArrayList<>();
for (Teacher teacher : teachers) {
    ids.add(teacher.id());
}
```

### Switch Expression Replacement Pattern

```java
// Before (switch expression)
return switch (raw.trim().toUpperCase()) {
    case "SENT"   -> EmailStatus.SENT;
    case "FAILED" -> EmailStatus.FAILED;
    default       -> EmailStatus.NOT_SENT;
};

// After (if/else)
String upper = raw.trim().toUpperCase();
if ("SENT".equals(upper)) {
    return EmailStatus.SENT;
} else if ("FAILED".equals(upper)) {
    return EmailStatus.FAILED;
} else {
    return EmailStatus.NOT_SENT;
}
```

The same pattern applies to `buildView()` in `AppShell` and `updateHeader()` in `AppShell`.

---

### Bug Fix: ReportsScreen.indexForId()

**Current code (broken):**
```java
if (ids.get(i) == targetId) {   // identity comparison — fails for IDs > 127
```

**Fixed code:**
```java
if (ids.get(i).equals(targetId)) {   // value comparison — always correct
```

This is the only change needed in `indexForId`. The method signature and all call sites remain unchanged.

---

### Bug Fix: AppStore.handleWrite()

**Current code (broken):**
```java
String detail = result.getData() instanceof String text ? text : "";
```
This uses a pattern-matching instanceof expression (Java 16+) which is fine syntactically, but the intent is to extract a String detail. The fix also removes the pattern-matching form to keep beginner-style Java:

**Fixed code:**
```java
String detail = "";
if (result.getData() instanceof String) {
    detail = (String) result.getData();
}
```

---

### AppShell — Auto-Clear Banner Timer

A `javax.swing.Timer` field is added to `AppShell` for the auto-clear behavior:

```java
private Timer bannerClearTimer;
```

`showMessage()` is updated:

```java
public void showMessage(String message, Color color) {
    bannerMessage = message == null ? "" : message.trim();
    bannerColor = color == null ? AppTheme.INFO : color;

    if (bannerClearTimer != null) {
        bannerClearTimer.stop();
        bannerClearTimer = null;
    }

    if (!bannerMessage.isBlank()) {
        bannerClearTimer = new Timer(4000, event -> {
            bannerMessage = "";
            bannerClearTimer = null;
            updateBanner();
            revalidate();
            repaint();
        });
        bannerClearTimer.setRepeats(false);
        bannerClearTimer.start();
    }
}
```

The existing `attendanceTimer.stop()` call in the logout action listener is kept. The `bannerClearTimer` must also be stopped on logout to prevent callbacks after the panel is removed:

```java
logout.addActionListener(event -> {
    attendanceTimer.stop();
    if (bannerClearTimer != null) {
        bannerClearTimer.stop();
        bannerClearTimer = null;
    }
    onLogout.run();
});
```

---

### AppShell — Password Change Gate

`AppShell` gains a `mustChangePassword` boolean field set from `ModelUser`:

```java
private boolean mustChangePassword;
```

In the constructor, after building navigation:
```java
mustChangePassword = user.isMustChangePassword();
if (mustChangePassword) {
    selectedView = "password_change";
}
```

`openView()` is updated to block navigation while the flag is set:
```java
public void openView(String viewKey) {
    if (mustChangePassword) {
        return;  // block all navigation until password is changed
    }
    if (!navigation.containsKey(viewKey)) {
        return;
    }
    selectedView = viewKey;
    refreshSelectedView();
}
```

`buildView()` gains a case for `"password_change"`:
```java
if ("password_change".equals(selectedView)) {
    return PasswordChangeScreen.build(this);
}
```

After a successful password change, `AppShell` exposes:
```java
public void onPasswordChanged() {
    mustChangePassword = false;
    selectedView = "home";
    refreshSelectedView();
}
```

`ModelUser` gains a `isMustChangePassword()` method backed by the `must_change_password` column loaded during authentication.

---

### PanelLogin — Enter Key Action Listener

`KeyAdapter` is added to both `emailField` and `passwordField` in `buildForm()`:

```java
java.awt.event.KeyAdapter enterListener = new java.awt.event.KeyAdapter() {
    @Override
    public void keyPressed(java.awt.event.KeyEvent e) {
        if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
            if (loginHandler != null) {
                loginHandler.loginRequested(
                    emailField.getText(),
                    String.valueOf(passwordField.getPassword()),
                    role);
            }
        }
    }
};
emailField.addKeyListener(enterListener);
passwordField.addKeyListener(enterListener);
```

---

### New Screen: PasswordChangeScreen

A new class `src/ppb/qrattend/app/PasswordChangeScreen.java`:

```java
final class PasswordChangeScreen {
    private PasswordChangeScreen() {}

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        JTextField newPasswordField = shell.newTextField();
        JTextField confirmField = shell.newTextField();
        JLabel errorLabel = new JLabel(" ");
        // ... styled form
        JButton submitButton = new JButton("Set New Password");
        AppTheme.stylePrimaryButton(submitButton);
        submitButton.addActionListener(event -> {
            String newPw = newPasswordField.getText();
            String confirm = confirmField.getText();
            if (newPw.length() < 8) {
                errorLabel.setText("Password must be at least 8 characters.");
                return;
            }
            if (!newPw.equals(confirm)) {
                errorLabel.setText("Passwords do not match.");
                return;
            }
            AppStore.ActionResult result = shell.getStore().changePassword(
                shell.getCurrentUser().getUserId(), newPw);
            if (result.isSuccess()) {
                shell.onPasswordChanged();
                shell.showMessage(result.getMessage(), AppTheme.SUCCESS);
            } else {
                errorLabel.setText(result.getMessage());
            }
        });
        // build and return panel
        return page;
    }
}
```

---

### TeacherService — New Methods

**updateTeacher:**
```java
public ServiceResult<Teacher> updateTeacher(int teacherId, String fullName, String email)
```
- Validates non-blank name and email.
- Runs `UPDATE users SET full_name = ?, email = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND role = 'TEACHER'`.
- Returns failure if the email is already used by another user (catches `SQLIntegrityConstraintViolationException` or checks duplicate before update).

**deactivateTeacher:**
```java
public ServiceResult<Void> deactivateTeacher(int teacherId)
```
- Runs `UPDATE users SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND role = 'TEACHER'`.

**changePassword:**
```java
public ServiceResult<Void> changePassword(int teacherId, String newPassword)
```
- Validates length >= 8.
- Runs `UPDATE users SET password_hash = ?, must_change_password = 0, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?`.

---

### StudentService — New Methods

**updateStudent:**
```java
public ServiceResult<Student> updateStudent(int studentId, int sectionId,
    String studentCode, String fullName, String email)
```
- Validates all fields non-blank.
- Runs `UPDATE students SET section_id = ?, student_code = ?, full_name = ?, email = ?, updated_at = CURRENT_TIMESTAMP WHERE student_id = ?`.
- Returns failure on duplicate student_code or email.

**deactivateStudent:**
```java
public ServiceResult<Void> deactivateStudent(int studentId)
```
- Runs `UPDATE students SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE student_id = ?`.

---

### ScheduleService — New Methods

**updateSchedule:**
```java
public ServiceResult<Schedule> updateSchedule(int scheduleId, int teacherId,
    int sectionId, int subjectId, int roomId,
    DayOfWeek day, LocalTime startTime, LocalTime endTime)
```
- Validates all fields.
- Calls `hasTeacherConflict(connection, teacherId, day, startTime, endTime, scheduleId)` (existing overload that excludes the schedule being edited).
- On conflict, returns failure message naming the conflicting class (see conflict message improvement below).
- Runs `UPDATE schedules SET section_id=?, subject_id=?, room_id=?, day_of_week=?, start_time=?, end_time=?, updated_at=CURRENT_TIMESTAMP WHERE schedule_id=?`.

**deactivateSchedule:**
```java
public ServiceResult<Void> deactivateSchedule(int scheduleId)
```
- Runs `UPDATE schedules SET is_active = 0, updated_at = CURRENT_TIMESTAMP WHERE schedule_id = ?`.

**Conflict Message Improvement:**

The existing `hasTeacherConflict` returns a boolean. A new private helper `findConflictingSchedule` returns the conflicting `Schedule` object (or null):

```java
private Schedule findConflictingSchedule(Connection connection, int teacherId,
    DayOfWeek day, LocalTime startTime, LocalTime endTime, int ignoreScheduleId)
    throws SQLException
```

This runs a SELECT that joins `schedules` with `subjects` to get the subject name, and returns the first conflicting row. The conflict error message becomes:

```
"This time overlaps with " + conflicting.subjectName()
    + " (" + conflicting.getTimeLabel() + ")."
```

Both `addSchedule()` and `reviewScheduleRequest()` are updated to use this helper.

---

### SectionService — New Methods

**renameSection:**
```java
public ServiceResult<Section> renameSection(int sectionId, String newName)
```
- Validates non-blank name.
- Runs `UPDATE sections SET section_name = ? WHERE section_id = ?`.
- Returns failure on duplicate name.

**deleteSection:**
```java
public ServiceResult<Void> deleteSection(int sectionId)
```
- Checks for references: `SELECT 1 FROM students WHERE section_id = ? LIMIT 1` and `SELECT 1 FROM schedules WHERE section_id = ? AND is_active = 1 LIMIT 1`.
- If referenced, returns failure: `"This section is still used by students or schedules."`.
- Otherwise runs `DELETE FROM sections WHERE section_id = ?`.

---

### ScheduleService — Subject and Room CRUD

**renameSubject / deleteSubject:**
```java
public ServiceResult<Subject> renameSubject(int subjectId, String newName)
public ServiceResult<Void> deleteSubject(int subjectId)
```
- `deleteSubject` checks `SELECT 1 FROM schedules WHERE subject_id = ? AND is_active = 1 LIMIT 1` before deleting.

**renameRoom / deleteRoom:**
```java
public ServiceResult<Room> renameRoom(int roomId, String newName)
public ServiceResult<Void> deleteRoom(int roomId)
```
- `deleteRoom` checks `SELECT 1 FROM schedules WHERE room_id = ? AND is_active = 1 LIMIT 1` before deleting.

---

### AttendanceService — markAllAbsent

```java
public ServiceResult<Integer> markAllAbsent(int teacherId)
```

Logic:
1. Load current session via `getCurrentSessionForTeacher`.
2. If no open session, return failure.
3. Load all students for the session's section.
4. For each student, check `hasAttendance(connection, session.id(), student.id())`.
5. For students without a record, insert `ABSENT` attendance record with method `MANUAL`.
6. Return `ServiceResult.success("Marked N students absent.", count)` or `ServiceResult.success("All students are already marked.", 0)` when count is 0.

New SQL constant:
```sql
INSERT INTO attendance_records
    (session_id, student_id, attendance_method, attendance_status, note)
VALUES (?, ?, 'MANUAL', 'ABSENT', '')
```

Note: The `attendance_status` enum in the DB schema currently only has `PRESENT` and `LATE`. A migration is needed to add `ABSENT`:
```sql
ALTER TABLE attendance_records
    MODIFY attendance_status ENUM('PRESENT', 'LATE', 'ABSENT') NOT NULL DEFAULT 'PRESENT';
```

The `AttendanceStatus` enum in `CoreModels` gains:
```java
ABSENT("Absent")
```

---

### ReportService — exportCsv

```java
public ServiceResult<Integer> exportCsv(List<AttendanceRecord> records, java.io.File file)
```

Logic:
1. Open a `PrintWriter` with UTF-8 encoding.
2. Write header: `Student ID,Student Name,Section,Subject,Date/Time,Method,Status,Note`.
3. For each record, write one CSV row. Values containing commas or quotes are wrapped in double-quotes with internal quotes escaped as `""`.
4. Return `ServiceResult.success("Exported N rows.", count)`.
5. On `IOException`, return `ServiceResult.failure("Could not write the file: " + ex.getMessage())`.

A private helper `escapeCsv(String value)` handles quoting.

---

### ReportsScreen — Export CSV Button

A "Export CSV" button is added to `buildRecordsSection`. When clicked:
1. A `JFileChooser` is shown with a `.csv` filter.
2. If the user confirms, `reportService.exportCsv(records, file)` is called via `AppStore.exportCsv(records, file)`.
3. The result is passed to `shell.showResult()`.

---

### RequestsScreen — Reason Column

The schedule requests table model is updated from:
```java
shell.createTableModel("Teacher", "Section", "Subject", "Room", "Day", "Time", "Status")
```
to:
```java
shell.createTableModel("Teacher", "Section", "Subject", "Room", "Day", "Time", "Reason", "Status")
```

Each row gains `request.reason()` at index 6.

---

### AttendanceScreen — Mark All Absent Button

A "Mark All Absent" button is added to `buildManualSection`. It is enabled only when `session.status() == SessionStatus.OPEN`. When clicked:
```java
shell.showResult(shell.getStore().markAllAbsent(shell.getCurrentUser().getUserId()));
shell.refreshView();
```

---

### TeachersScreen — Edit and Deactivate

The teacher list section gains two new buttons: "Edit" and "Deactivate". When "Edit" is clicked, an inline edit form appears (or the existing form fields are populated). The form has name and email fields pre-filled from the selected teacher. "Save Changes" calls `shell.getStore().updateTeacher(teacher.id(), name, email)`. "Deactivate" calls `shell.getStore().deactivateTeacher(teacher.id())`.

---

### AdminStudentsScreen — Edit and Deactivate

The student list section gains "Edit" and "Deactivate" buttons. "Edit" populates an inline form with the student's current code, name, email, and section. "Save Changes" calls `shell.getStore().updateStudent(...)`. "Deactivate" calls `shell.getStore().deactivateStudent(student.id())`.

---

### AdminSchedulesScreen — Edit and Delete

The saved schedules table gains "Edit" and "Delete" buttons. "Edit" populates the existing schedule form fields with the selected schedule's values. "Save Changes" calls `shell.getStore().updateSchedule(...)`. "Delete" calls `shell.getStore().deactivateSchedule(schedule.id())`.

---

### SectionsScreen — Edit and Delete for Sections, Subjects, Rooms

Each of the three panels (Sections, Subjects, Rooms) gains "Edit" and "Delete" buttons next to each item in the list. Clicking "Edit" shows an inline text field pre-filled with the current name. "Save" calls the appropriate rename method. "Delete" calls the appropriate delete method.

---

## Data Models

### CoreModels.java — Plain Class Replacements

All records become `public static final class` with `private final` fields and accessor methods named identically to the old record accessors.

### AttendanceStatus Enum Addition

```java
public enum AttendanceStatus {
    PRESENT("Present"),
    LATE("Late"),
    ABSENT("Absent");
    // ...
}
```

### ModelUser — must_change_password

`ModelUser` gains:
```java
private boolean mustChangePassword;

public boolean isMustChangePassword() { return mustChangePassword; }
public void setMustChangePassword(boolean v) { mustChangePassword = v; }
```

`DatabaseAuthenticationService` loads `must_change_password` from the `users` table and sets it on the `ModelUser` after successful authentication.

### Database Migration

One schema change is required:
```sql
ALTER TABLE attendance_records
    MODIFY attendance_status ENUM('PRESENT', 'LATE', 'ABSENT') NOT NULL DEFAULT 'PRESENT';
```

This is a backward-compatible change (existing rows keep their values).

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Integer ID Lookup Returns Correct Index

*For any* list of `Integer` IDs (including values greater than 127) and any target ID that appears in the list, `indexForId` shall return the index at which the target appears, regardless of whether the JVM caches the `Integer` object.

**Validates: Requirements 1.1, 1.2, 1.3**

---

### Property 2: Warning Detail Extraction Is Type-Safe

*For any* `ServiceResult` in warning state, `AppStore.handleWrite()` shall produce an `ActionResult` whose `detail` field equals the `ServiceResult`'s data value when that data is a `String`, and equals an empty string for all other data types (including `null`, `Integer`, or any other object).

**Validates: Requirements 2.1, 2.2**

---

### Property 3: Schedule Conflict Message Names the Conflicting Class

*For any* pair of overlapping schedules for the same teacher on the same day, when the second schedule is submitted (via `addSchedule` or `reviewScheduleRequest`), the returned failure message shall contain the subject name and time range of the first (already-saved) schedule.

**Validates: Requirements 5.1, 5.2**

---

### Property 4: Schedule Requests Table Contains Reason for Every Row

*For any* list of `ScheduleRequest` objects, the table model built by `RequestsScreen` shall contain a "Reason" column whose value for each row equals `request.reason()`, and shall never contain `null` in that column even when `reason()` returns a blank string.

**Validates: Requirements 6.1, 6.2**

---

### Property 5: CSV Export Row Count Matches Record Count

*For any* non-empty list of `AttendanceRecord` objects, `ReportService.exportCsv` shall write exactly `records.size() + 1` lines to the output file (one header row plus one data row per record), and the header row shall contain the eight column names in the specified order.

**Validates: Requirements 7.2, 7.3**

---

### Property 6: Mark All Absent Inserts Exactly the Unmarked Students

*For any* open attendance session with N students in the section and M students already having a record for that session (0 ≤ M ≤ N), calling `markAllAbsent` shall insert exactly `N - M` new `ABSENT` records and shall not modify or duplicate any of the M existing records.

**Validates: Requirements 8.2, 8.3**

---

### Property 7: Password Change Clears the must_change_password Flag

*For any* valid new password (length ≥ 8), after `TeacherService.changePassword` succeeds, the `must_change_password` column for that user shall be `0` and the stored password hash shall match the new password.

**Validates: Requirements 9.2**

---

### Property 8: Short Passwords Are Always Rejected

*For any* string of length 0 through 7 (inclusive), `TeacherService.changePassword` shall return a failure result and shall not modify the database.

**Validates: Requirements 9.3**

---

### Property 9: Navigation Is Blocked While Password Change Is Required

*For any* navigation view key, calling `AppShell.openView()` while `mustChangePassword` is `true` shall leave `selectedView` unchanged and shall not render any screen other than `PasswordChangeScreen`.

**Validates: Requirements 9.6**

---

### Property 10: Teacher Update Persists New Name and Email

*For any* valid (non-blank, non-duplicate) name and email pair, after `TeacherService.updateTeacher` succeeds, loading the teacher by ID shall return a `Teacher` object whose `fullName()` and `email()` match the submitted values.

**Validates: Requirements 10.2**

---

### Property 11: Student Update Persists All Changed Fields

*For any* valid (non-blank, non-duplicate) student code, name, email, and section ID, after `StudentService.updateStudent` succeeds, loading the student by ID shall return a `Student` object whose fields match all submitted values.

**Validates: Requirements 11.2**

---

### Property 12: Section Rename Persists the New Name

*For any* valid (non-blank, non-duplicate) section name, after `SectionService.renameSection` succeeds, loading the section by ID shall return a `Section` whose `name()` equals the submitted name.

**Validates: Requirements 13.2**

---

### Property 13: In-Use Section Cannot Be Deleted

*For any* section that is referenced by at least one active student or active schedule, `SectionService.deleteSection` shall return a failure result and the section row shall remain in the database.

**Validates: Requirements 13.3, 13.4**

---

## Error Handling

### Service Layer

- All service methods validate inputs before touching the database and return `ServiceResult.failure(message)` for invalid inputs.
- All SQL operations are wrapped in try-with-resources. `SQLException` is caught and translated to a plain-language `ServiceResult.failure(message)`.
- Duplicate key violations (email, student code, section name, etc.) are caught and return a specific message: `"That email is already in use."` / `"That student ID is already in use."` etc.
- Transactions are used for multi-step writes (e.g., `changePassword` is a single UPDATE so no transaction needed; `markAllAbsent` uses a transaction to batch inserts).

### UI Layer

- All `AppStore` method calls go through `shell.showResult(result)` which maps success/warning/failure to the appropriate banner color.
- The auto-clear timer ensures banners disappear after 4 seconds without user action.
- Inline error labels (e.g., in `PasswordChangeScreen`) are used for validation errors that should not dismiss the form.

### Password Change Screen

- Validation errors (short password, mismatch) are shown in an inline `JLabel` below the form fields, not in the banner.
- The form is not dismissed on validation failure.
- On success, the banner shows the success message and the screen navigates to the home view.

---

## Testing Strategy

### Unit Tests

Unit tests cover specific examples, edge cases, and error conditions. They are written using JUnit 4 (already available via the Ant build, or JUnit 5 if added to `lib/`). Since the project has no existing test framework configured, JUnit 4 jar should be added to `lib/` and `build.xml` updated to compile and run tests from the `test/` directory.

Focus areas for unit tests:
- `indexForId` with IDs both ≤ 127 and > 127.
- `AppStore.handleWrite()` with warning results carrying String data, non-String data, and null data.
- `PasswordChangeScreen` validation: short password, mismatched passwords.
- `SectionService.deleteSection` with and without references.
- `ReportService.exportCsv` with an empty list (should write only the header row).
- `AttendanceService.markAllAbsent` when all students are already marked.

### Property-Based Tests

Property-based tests use [jqwik](https://jqwik.net/) or a similar PBT library for Java. Since no PBT library is currently in `lib/`, `jqwik-1.x.jar` should be added. Each property test runs a minimum of 100 iterations.

Each property test is tagged with a comment:
```java
// Feature: qrattend-full-fix, Property N: <property text>
```

**Property 1 test** — `indexForId` correctness:
- Generator: random `List<Integer>` with values in range 1–500 (crossing the 127 boundary), random target from the list.
- Assertion: returned index equals the actual position of the target.

**Property 2 test** — `handleWrite` detail extraction:
- Generator: random warning `ServiceResult` with data drawn from `{String, Integer, null, Object}`.
- Assertion: `ActionResult.getDetail()` equals the data value when it is a `String`, empty string otherwise.

**Property 3 test** — conflict message content:
- Generator: random teacher ID, random day, random overlapping time pair.
- Setup: insert a schedule, then attempt to add an overlapping one.
- Assertion: failure message contains the first schedule's subject name and time range.

**Property 4 test** — reason column in table:
- Generator: random list of `ScheduleRequest` objects with varying reason strings (including blank).
- Assertion: table model column "Reason" at each row equals `request.reason()`, never null.

**Property 5 test** — CSV row count:
- Generator: random non-empty list of `AttendanceRecord` objects.
- Assertion: line count of written file equals `records.size() + 1`.

**Property 6 test** — markAllAbsent count:
- Generator: random session with N students, M pre-marked (0 ≤ M ≤ N).
- Assertion: new records inserted = N - M, all with status ABSENT.

**Property 7 test** — changePassword clears flag:
- Generator: random valid password (length 8–30, printable ASCII).
- Assertion: after success, `must_change_password = 0` and hash matches.

**Property 8 test** — short password rejection:
- Generator: random string of length 0–7.
- Assertion: `changePassword` returns failure, DB unchanged.

**Property 9 test** — navigation blocked:
- Generator: random view key from the navigation map.
- Setup: `mustChangePassword = true`.
- Assertion: `selectedView` unchanged after `openView(key)`.

**Property 10 test** — teacher update round-trip:
- Generator: random valid name and email (non-blank, unique).
- Assertion: after `updateTeacher`, loaded teacher matches submitted values.

**Property 11 test** — student update round-trip:
- Generator: random valid student fields.
- Assertion: after `updateStudent`, loaded student matches submitted values.

**Property 12 test** — section rename round-trip:
- Generator: random valid section name.
- Assertion: after `renameSection`, loaded section name matches.

**Property 13 test** — in-use section delete blocked:
- Generator: random section with at least one student or schedule referencing it.
- Assertion: `deleteSection` returns failure, section still exists.

### Integration Tests

Integration tests run against a real (test) database instance and verify end-to-end flows:
- Full login → password change → dashboard navigation flow.
- Full schedule add → conflict detection → conflict message content.
- Full markAllAbsent → verify DB records.

These are run manually or in a CI environment with a test database configured.
