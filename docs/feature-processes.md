# Feature Processes

This file explains the most important user flows.

## 1. Teacher Creation

1. Admin opens `Teachers`
2. Admin types teacher name and email
3. `AppStore.addTeacher(...)` is called
4. `TeacherService`:
   - validates input
   - creates the teacher in `users`
   - creates an email log row
   - sends password email through Resend
   - updates the email log result

## 2. Student Creation

There are two ways:

- bulk import
- manual add

Both start in `Students`.

### Bulk import

1. Admin chooses a section
2. Admin pastes lines like `student id, full name, email`
3. `StudentService.importStudents(...)` loops over the lines
4. Each good row calls `addStudent(...)`

### Manual add

1. Admin chooses a section
2. Admin types one student ID, one name, one email
3. `StudentService.addStudent(...)`:
   - validates input
   - creates one QR token value
   - stores only the QR hash in the database
   - saves the student
   - saves an email log
   - sends the QR email through Resend

## 3. Schedule Creation

1. Admin opens `Schedule`
2. Admin picks teacher, section, subject, room, day, and time from dropdowns
3. `ScheduleService.addSchedule(...)`:
   - validates input
   - checks teacher time conflict
   - saves the schedule row

## 4. Schedule Change Request

1. Teacher opens `My Schedule`
2. Teacher picks the saved class to change
3. Teacher picks the new section, subject, room, day, and time
4. Teacher adds a reason
5. `ScheduleService.submitScheduleRequest(...)` saves the request
6. Admin reviews it in `Requests`

## 5. Student Removal Request

1. Teacher opens `My Class List`
2. Teacher selects a student
3. Teacher types a short reason
4. `StudentService.submitStudentRemovalRequest(...)` saves the request
5. Admin reviews it in `Requests`

## 6. Attendance

### Automatic scheduled class

1. Teacher opens `Attendance`
2. `AttendanceService.getCurrentSessionForTeacher(...)` checks the current day and time
3. If the saved schedule matches the current time:
   - it loads or creates the class session automatically

### Temporary class

If no class is open:

1. Teacher picks section and subject
2. Teacher clicks `Start Temporary Class`
3. `AttendanceService.startTemporaryClass(...)` saves a temporary session

### QR attendance

1. Teacher scans the QR code
2. `AttendanceService.markAttendanceFromQr(...)`:
   - loads the current open class
   - hashes the scanned value
   - looks for a student in the current section
   - blocks duplicate attendance
   - saves the record

### Manual attendance

1. Teacher clicks a student button
2. `AttendanceService.markManualAttendance(...)`:
   - checks the current open class
   - checks that the student belongs to the current class section
   - blocks duplicate attendance
   - saves the record

## 7. Reports

1. User chooses filters
2. `ReportService` loads:
   - summary numbers
   - matching attendance records

Teachers can also use `Ask AI` on the report page.

## 8. Ask AI

1. Teacher opens Dashboard, Attendance, or Reports
2. Teacher types a question
3. `AppStore.askAi(...)` builds page context
4. `AiChatService` sends the question and the page facts to Gemini
5. The answer stays in memory for the current app session only
