# Software Requirements Specification (SRS)

## QR-Based Attendance System (QR-BAS)

Macabali, Jose Victor Mikael Pacio
Pajarillaga, Renz John Cobrado
Galope, LonLon Laranang
Novilla, Arjay Tablang

Nueva Ecija University of Science and Technology – Gabaldon Campus
Bachelor of Science in Information Technology

---

## 1. Introduction

### 1.1 Purpose

The NEUST-Gabaldon Campus QR-Based Attendance System records student attendance using QR codes assigned to individual students. It replaces manual attendance checking. It improves accuracy, speed, and record management for both teachers and administrators.

### 1.2 Scope

The system assigns one unique QR code per student. Teachers scan student QR codes using a laptop or PC webcam, or by entering the QR value manually. Attendance records store the date, time, subject, section, and student identity. Teachers and administrators manage records, schedules, and reports through a desktop application. The system also sends QR codes and account credentials by email, and provides an AI assistant for teachers to analyze attendance data.

### 1.3 Definitions, Acronyms, and Abbreviations

| Term | Meaning |
|---|---|
| NEUST | Nueva Ecija University of Science and Technology |
| QR | Quick Response |
| SRS | Software Requirements Specification |
| UAT | User Acceptance Testing |
| Admin | School administrator with full system access |
| Teacher | Instructor who takes attendance and views reports |
| Section | A class group, e.g., BSIT-2A |
| Schedule | A saved class assignment linking a teacher, section, subject, room, day, and time |
| Session | An opened class instance for a specific schedule or temporary class |
| PHT | Philippine Standard Time (Asia/Manila) |

### 1.4 References

- IEEE Standard 830-1998 Software Requirements Specification
- NEUST IT Policy Guidelines

---

## 2. Overall Description

### 2.1 Product Perspective

QR-Based Attendance System is a standalone Java desktop application. It operates on Windows computers. It integrates with PC or laptop webcams for QR scanning. It connects to a MariaDB database for persistent storage, the Resend email service for automated credential and QR code delivery, and the Google Gemini AI API for teacher-facing attendance insights.

### 2.2 Product Functions

1. User authentication and role-based access control.
2. Unique QR code generation and email delivery per student.
3. QR scanning using PC or laptop webcams or manual QR entry.
4. Automatic and manual attendance session management.
5. Attendance recording per subject, section, and schedule.
6. Schedule management and schedule change request workflow.
7. Student management and student removal request workflow.
8. Attendance viewing, filtering, and CSV export.
9. AI-assisted attendance analysis for teachers.

### 2.3 User Characteristics

- **Teachers:** Operate the desktop application and webcam scanner. Take attendance, view class lists, submit schedule change requests, and view reports.
- **Administrators:** Manage teacher accounts, student records, school lists (sections, subjects, rooms), and class schedules. Review and act on teacher requests. View reports across all teachers and sections.

> Note: Students are not system users. They receive their QR codes by email and present them to the teacher for scanning.

### 2.4 Constraints

1. Windows operating system required.
2. Webcam required for camera-based QR scanning.
3. Java Runtime Environment required.
4. MariaDB or MySQL database required.
5. Internet connection required for email delivery and AI features.

### 2.5 Assumptions and Dependencies

- Each student holds one unique QR code. Resending the QR generates a new token and invalidates the previous one.
- Each teacher must have at least one saved schedule before the system can open a class session automatically.
- School lists (sections, subjects, rooms) must be set up by the admin before schedules can be created.
- Email delivery requires a valid Resend API key in the system configuration.
- AI features require a valid Google Gemini API key in the system configuration.

---

## 3. Specific Requirements

### 3.1 Functional Requirements

#### a) Authentication

- Users log in using their email address, password, and selected role (Admin or Teacher).
- The system verifies the email, hashed password, role, and active account status.
- Inactive accounts are denied login with a plain-language error message.
- When a teacher's account requires a password change, the system displays a password change screen immediately after login and blocks navigation to any other screen until the new password is set.
- New passwords must be at least 8 characters. Shorter passwords are rejected with an inline error.

#### b) Teacher Account Management

- Administrators register teacher accounts by entering a full name and email address.
- On account creation, the system generates a temporary password, sends it to the teacher's email, and flags the account to require a password change on first login.
- Administrators can resend or reset a teacher's password at any time.
- Administrators can edit a teacher's full name and email. Duplicate emails are rejected.
- Administrators can deactivate a teacher account. Deactivated teachers cannot log in and are shown as "Hidden" in the teacher list.

#### c) School Lists Management

- Administrators manage three saved lists: sections, subjects, and rooms.
- Each list supports adding, renaming, and deleting entries.
- A section cannot be deleted if it is still referenced by a student or an active schedule.
- A subject or room cannot be deleted if it is still referenced by an active schedule.

#### d) Student Registration and QR Code Management

- Administrators add students one at a time by entering a student code, full name, email, and section.
- Administrators can import multiple students at once by pasting rows in the format: `student code, full name, email`.
- On student creation, the system generates a unique QR token, stores only its SHA-256 hash in the database, and sends the QR code image to the student's email.
- Administrators can resend the QR email to a student. Resending generates a new QR token and invalidates the old one.
- Administrators can edit a student's code, full name, email, and section. Duplicate student codes or emails are rejected.
- Administrators can deactivate a student record. Deactivated students do not appear in class lists or attendance screens.

#### e) Schedule Management

- Administrators create class schedules by selecting a teacher, section, subject, room, day of week, start time, and end time from the saved lists. No manual text entry is required.
- The system prevents saving a schedule that overlaps in time with another active schedule for the same teacher on the same day. The error message names the conflicting subject and its time range.
- Administrators can edit a saved schedule. The conflict check excludes the schedule being edited.
- Administrators can delete (deactivate) a saved schedule. Deleted schedules are hidden from all views.

#### f) QR Scanning and Attendance Recording

- When a teacher opens the Attendance screen, the system automatically checks whether a saved schedule matches the current day and time (PHT). If a match is found, the class session opens automatically.
- The Attendance screen refreshes every 30 seconds to detect when a scheduled class time begins.
- If no scheduled class is active, the teacher can start a temporary class by selecting a section and subject and providing a reason.
- The teacher can end a temporary class manually.
- The teacher records attendance by scanning a student's QR code using a webcam or by entering the QR value in a text field.
- If QR scanning fails, the teacher can mark a student present by clicking the student's name button. Only students belonging to the current class section are shown.
- The teacher can mark all unrecorded students as Absent with a single "Mark All Absent" button. This button is enabled only when a class session is open.
- The system prevents duplicate attendance records for the same student in the same session.
- The Attendance screen displays the current class status, subject name, and number of students in the class.

#### g) Schedule Change Request Workflow

- Teachers can view their saved class schedule.
- Teachers can submit a schedule change request by selecting one of their saved classes, choosing new values from the saved lists, and providing a reason.
- Administrators review pending schedule change requests in the Requests screen, which shows the teacher name, requested class details, reason, and current status.
- Administrators can approve or reject a schedule change request. Approving a request updates the saved schedule to the requested values.
- When approving a request, the system checks for time conflicts. If a conflict exists, the approval is blocked and the conflicting class is named in the error message.
- Teachers can view the history of their own schedule change requests and their statuses.

#### h) Student Removal Request Workflow

- Teachers can view the list of active students from all sections in their saved schedule.
- Teachers can submit a student removal request by selecting a student and providing a reason.
- Administrators review pending student removal requests in the Requests screen, which shows the teacher name, student name, section, and reason.
- Administrators can approve or reject a student removal request. Approving a request deactivates the student record.

#### i) Record Management and Reports

- Both admins and teachers can view attendance reports filtered by teacher, section, and subject.
- The report screen displays a summary showing total unique students, total present records, and total late records for the current filter.
- The report screen displays a detailed table of individual attendance records showing student name, section, subject, date/time, method, status, and note.
- Users can export the current filtered attendance records to a CSV file with the columns: Student ID, Student Name, Section, Subject, Date/Time, Method, Status, Note.
- On successful export, the system displays a banner showing the number of rows written. On failure, a plain-language error message is shown.

#### j) AI Assistant

- Teachers have access to an AI assistant panel on the Dashboard, Attendance, and Reports screens.
- The system builds a context summary from the current page data (current class, recent records, report summary) and sends it along with the teacher's question to the Google Gemini AI API.
- Teachers can clear the AI conversation history for the current session.

---

### 3.2 Non-Functional Requirements

#### a) Performance

- The Attendance screen refreshes automatically every 30 seconds to detect scheduled class start times without requiring user action.

#### b) Security

- Passwords are stored as bcrypt hashes. Plain-text passwords are never stored in the database.
- Student QR tokens are not stored in the database. Only the SHA-256 hash of the token is stored. The readable token is used only when sending the QR email.
- Database credentials are stored in an external configuration file excluded from version control.
- All database write operations use parameterized SQL statements to prevent SQL injection.

#### c) Usability

- All form fields use dropdown lists for data that already exists in the system (teachers, sections, subjects, rooms, days, times). Users do not need to type these values.
- Status banners disappear automatically after 4 seconds without user action.
- The Enter key submits the login form from either the email or password field.
- The system displays plain-language error messages for all validation failures and database constraint violations.

#### d) Reliability

- Multi-step write operations use database transactions. A failure in any step rolls back the entire operation.
- Email send failures do not prevent the primary record (teacher or student) from being saved. The system returns a warning indicating that the email could not be sent.

---

### 3.3 Interface Requirements

#### a) User Interface

- Java Swing desktop application with a sidebar navigation, page header, status banner, and content area.
- Admin screens: Teachers, School Lists, Students, Schedule, Requests, Reports.
- Teacher screens: Attendance, My Class List, My Schedule, Reports.

#### b) Hardware Interface

- Webcam access for live QR scanning via the built-in camera dialog.

#### c) Software Interface

- JDBC connection to MariaDB / MySQL for all data storage.
- Resend HTTP API for teacher password emails and student QR code emails.
- Google Gemini REST API for the AI assistant feature.
- ZXing library for QR code generation and decoding.

---

## 4. System Features

### Student QR Registry

Central list of students with their assigned section, student code, email, and QR delivery status. Supports single-add and bulk import. QR codes are delivered by email; only the hash is stored in the database.

### Live Scan View

Real-time webcam preview during QR scanning. The teacher can also type or paste a QR value directly into a text field as a fallback.

### Automatic Class Session Management

The system detects the teacher's scheduled class time automatically and opens the session without manual action. A temporary class option is available when no scheduled class is active.

### Attendance Summary and Records

Filterable attendance report showing a summary (students, present, late) and a full record table. Supports CSV export.

### Schedule and Request Management

Admin-controlled schedule creation using saved lists. Teacher-initiated change requests reviewed and acted on by the admin.

### AI Attendance Assistant

Teacher-facing AI panel powered by Google Gemini. Provides context-aware answers about attendance patterns, late students, and class performance.

---

## 5. Software and Hardware Requirements

### 5.1 Software Requirements

1. Database: MariaDB or MySQL
2. Operating System: Windows 10 or higher
3. Runtime: Java 11 or later
4. Build Tool: Apache Ant
5. Libraries: ZXing (QR code), JDBC MySQL Connector, SLF4J, Webcam Capture

### 5.2 Hardware Requirements

a) Host PC: Desktop or laptop with webcam. Minimum 8 GB RAM recommended.
b) Peripheral Devices: External webcam optional.

---

## 6. System Constraints

- The application is desktop-only. There is no web or mobile interface.
- The AI conversation history is session-only. It is cleared when the application is closed or when the teacher clicks "Clear."
- Class time detection uses the system clock set to PHT (Asia/Manila). Incorrect system time will affect automatic session opening.
- One student can only be marked once per class session. Duplicate scans are blocked with a warning.
- CSV export is supported. PDF export is not supported in the current version.

---

## 7. Appendices

### Appendix A: User Roles and Permissions

| Feature | Admin | Teacher |
|---|---|---|
| Manage teacher accounts | ✓ | — |
| Manage school lists (sections, subjects, rooms) | ✓ | — |
| Manage student records | ✓ | — |
| Create and edit schedules | ✓ | — |
| Review and act on requests | ✓ | — |
| View all reports | ✓ | — |
| Take attendance | — | ✓ |
| View own class list | — | ✓ |
| Submit schedule change request | — | ✓ |
| Submit student removal request | — | ✓ |
| View own reports | — | ✓ |
| Use AI assistant | — | ✓ |

### Appendix B: Use Case Summary

| Use Case | Actor |
|---|---|
| Log in to the system | Admin, Teacher |
| Change password on first login | Teacher |
| Add / edit / deactivate teacher | Admin |
| Add / rename / delete section, subject, room | Admin |
| Add / import / edit / deactivate student | Admin |
| Resend student QR email | Admin |
| Create / edit / delete schedule | Admin |
| Review and approve/reject schedule request | Admin |
| Review and approve/reject student removal request | Admin |
| View attendance reports | Admin, Teacher |
| Export attendance to CSV | Admin, Teacher |
| Open attendance session (auto or temporary) | Teacher |
| Scan student QR code | Teacher |
| Mark student present manually | Teacher |
| Mark all absent | Teacher |
| Submit schedule change request | Teacher |
| Submit student removal request | Teacher |
| Ask AI assistant | Teacher |

### Appendix C: Database Tables

| Table | Purpose |
|---|---|
| `users` | Admin and teacher accounts |
| `sections` | Saved section list |
| `subjects` | Saved subject list |
| `rooms` | Saved room list |
| `students` | Student records with QR hash |
| `schedules` | Saved class schedules |
| `schedule_requests` | Teacher schedule change requests |
| `student_removal_requests` | Teacher student removal requests |
| `attendance_sessions` | Opened class sessions |
| `attendance_records` | Individual student attendance records |
| `email_logs` | Email send history |

---

## 8. Conclusion

The NEUST-Gabaldon Campus QR-Based Attendance System automates attendance recording using unique student QR codes. It uses a Java Swing desktop application, PC webcams, and a MariaDB database. The system supports two roles — Administrator and Teacher — with a clear workflow for schedule management, student management, and attendance taking. It provides accurate, filterable attendance reports with CSV export, and includes an AI assistant to help teachers analyze attendance patterns. The system improves accuracy, saves class time, and ensures reliable attendance records for academic monitoring and administrative decision-making.

---

*End of Software Requirements Specification*
