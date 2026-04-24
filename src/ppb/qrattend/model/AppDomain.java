package ppb.qrattend.model;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class AppDomain {

    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    public static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private AppDomain() {
    }

    public enum UserRole {
        ADMIN("Admin"),
        TEACHER("Teacher");

        private final String label;

        UserRole(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum AttendanceSource {
        QR("QR Scan"),
        MANUAL("Without QR");

        private final String label;

        AttendanceSource(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ScheduleRequestStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String label;

        ScheduleRequestStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum SessionState {
        LOCKED("No Class Open"),
        ACTIVE("Class Open"),
        OVERRIDE_ACTIVE("Temporary Class Open"),
        CLOSED("Closed");

        private final String label;

        SessionState(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum EmailStatus {
        QUEUED("Waiting"),
        SENT("Sent"),
        FAILED("Problem");

        private final String label;

        EmailStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum AttendanceStatus {
        PRESENT("Present"),
        LATE("Late"),
        ABSENT("Absent");

        private final String label;

        AttendanceStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class TeacherProfile {

        private final int id;
        private String fullName;
        private String email;
        private EmailStatus emailStatus;
        private String status;
        private LocalDateTime createdAt;

        public TeacherProfile(int id, String fullName, String email, EmailStatus emailStatus, String status, LocalDateTime createdAt) {
            this.id = id;
            this.fullName = fullName;
            this.email = email;
            this.emailStatus = emailStatus;
            this.status = status;
            this.createdAt = createdAt;
        }

        public int getId() {
            return id;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return "";
        }

        public void setPassword(String password) {
            // Teacher profiles no longer keep readable passwords in memory.
        }

        public EmailStatus getEmailStatus() {
            return emailStatus;
        }

        public void setEmailStatus(EmailStatus emailStatus) {
            this.emailStatus = emailStatus;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static final class StudentProfile {

        private final String studentId;
        private final int teacherId;
        private String sectionName;
        private String fullName;
        private String email;
        private EmailStatus qrStatus;
        private LocalDateTime createdAt;

        public StudentProfile(String studentId, int teacherId, String fullName, String email, EmailStatus qrStatus, LocalDateTime createdAt) {
            this(studentId, teacherId, "Unassigned", fullName, email, qrStatus, createdAt);
        }

        public StudentProfile(String studentId, int teacherId, String sectionName, String fullName, String email, EmailStatus qrStatus, LocalDateTime createdAt) {
            this.studentId = studentId;
            this.teacherId = teacherId;
            this.sectionName = sectionName;
            this.fullName = fullName;
            this.email = email;
            this.qrStatus = qrStatus;
            this.createdAt = createdAt;
        }

        public String getStudentId() {
            return studentId;
        }

        public int getTeacherId() {
            return teacherId;
        }

        public String getSectionName() {
            return sectionName;
        }

        public void setSectionName(String sectionName) {
            this.sectionName = sectionName;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public EmailStatus getQrStatus() {
            return qrStatus;
        }

        public void setQrStatus(EmailStatus qrStatus) {
            this.qrStatus = qrStatus;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static final class ScheduleSlot {

        private final int id;
        private final int teacherId;
        private String subjectName;
        private DayOfWeek day;
        private LocalTime startTime;
        private LocalTime endTime;
        private String room;
        private String status;

        public ScheduleSlot(int id, int teacherId, String subjectName, DayOfWeek day, LocalTime startTime, LocalTime endTime, String room, String status) {
            this.id = id;
            this.teacherId = teacherId;
            this.subjectName = subjectName;
            this.day = day;
            this.startTime = startTime;
            this.endTime = endTime;
            this.room = room;
            this.status = status;
        }

        public int getId() {
            return id;
        }

        public int getTeacherId() {
            return teacherId;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public DayOfWeek getDay() {
            return day;
        }

        public void setDay(DayOfWeek day) {
            this.day = day;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalTime startTime) {
            this.startTime = startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalTime endTime) {
            this.endTime = endTime;
        }

        public String getRoom() {
            return room;
        }

        public void setRoom(String room) {
            this.room = room;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isApproved() {
            return "APPROVED".equalsIgnoreCase(status);
        }

        public String getTimeLabel() {
            return startTime.format(TIME_FORMAT) + " - " + endTime.format(TIME_FORMAT);
        }

        public String getScheduleLabel() {
            return day + " " + getTimeLabel() + " / " + room;
        }
    }

    public static final class ScheduleChangeRequest {

        private final int id;
        private final int teacherId;
        private final Integer sourceSlotId;
        private final String requester;
        private final String oldValue;
        private final String requestedValue;
        private final String requestedSubjectName;
        private final DayOfWeek requestedDay;
        private final LocalTime requestedStartTime;
        private final LocalTime requestedEndTime;
        private final String requestedRoom;
        private final String reason;
        private ScheduleRequestStatus status;
        private String reviewedBy;
        private LocalDateTime reviewedAt;

        public ScheduleChangeRequest(int id, int teacherId, Integer sourceSlotId, String requester, String oldValue, String requestedValue,
                String requestedSubjectName, DayOfWeek requestedDay, LocalTime requestedStartTime, LocalTime requestedEndTime,
                String requestedRoom, String reason, ScheduleRequestStatus status) {
            this.id = id;
            this.teacherId = teacherId;
            this.sourceSlotId = sourceSlotId;
            this.requester = requester;
            this.oldValue = oldValue;
            this.requestedValue = requestedValue;
            this.requestedSubjectName = requestedSubjectName;
            this.requestedDay = requestedDay;
            this.requestedStartTime = requestedStartTime;
            this.requestedEndTime = requestedEndTime;
            this.requestedRoom = requestedRoom;
            this.reason = reason;
            this.status = status;
        }

        public int getId() {
            return id;
        }

        public int getTeacherId() {
            return teacherId;
        }

        public Integer getSourceSlotId() {
            return sourceSlotId;
        }

        public String getRequester() {
            return requester;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getRequestedValue() {
            return requestedValue;
        }

        public String getRequestedSubjectName() {
            return requestedSubjectName;
        }

        public DayOfWeek getRequestedDay() {
            return requestedDay;
        }

        public LocalTime getRequestedStartTime() {
            return requestedStartTime;
        }

        public LocalTime getRequestedEndTime() {
            return requestedEndTime;
        }

        public String getRequestedRoom() {
            return requestedRoom;
        }

        public String getReason() {
            return reason;
        }

        public ScheduleRequestStatus getStatus() {
            return status;
        }

        public void setStatus(ScheduleRequestStatus status) {
            this.status = status;
        }

        public String getReviewedBy() {
            return reviewedBy;
        }

        public void setReviewedBy(String reviewedBy) {
            this.reviewedBy = reviewedBy;
        }

        public LocalDateTime getReviewedAt() {
            return reviewedAt;
        }

        public void setReviewedAt(LocalDateTime reviewedAt) {
            this.reviewedAt = reviewedAt;
        }
    }

    public static final class StudentRemovalRequest {

        private final int id;
        private final int teacherId;
        private final String teacherName;
        private final String studentId;
        private final String studentName;
        private final String sectionName;
        private final String reason;
        private ScheduleRequestStatus status;
        private String reviewedBy;
        private LocalDateTime reviewedAt;
        private final LocalDateTime createdAt;

        public StudentRemovalRequest(int id, int teacherId, String teacherName, String studentId, String studentName,
                String sectionName, String reason, ScheduleRequestStatus status, LocalDateTime createdAt) {
            this.id = id;
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.studentId = studentId;
            this.studentName = studentName;
            this.sectionName = sectionName;
            this.reason = reason;
            this.status = status;
            this.createdAt = createdAt;
        }

        public int getId() {
            return id;
        }

        public int getTeacherId() {
            return teacherId;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public String getStudentId() {
            return studentId;
        }

        public String getStudentName() {
            return studentName;
        }

        public String getSectionName() {
            return sectionName;
        }

        public String getReason() {
            return reason;
        }

        public ScheduleRequestStatus getStatus() {
            return status;
        }

        public void setStatus(ScheduleRequestStatus status) {
            this.status = status;
        }

        public String getReviewedBy() {
            return reviewedBy;
        }

        public void setReviewedBy(String reviewedBy) {
            this.reviewedBy = reviewedBy;
        }

        public LocalDateTime getReviewedAt() {
            return reviewedAt;
        }

        public void setReviewedAt(LocalDateTime reviewedAt) {
            this.reviewedAt = reviewedAt;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    public static final class AttendanceSession {

        private final int id;
        private final int teacherId;
        private String subjectName;
        private SessionState state;
        private LocalDateTime openedAt;
        private String room;
        private String note;
        private boolean overrideSession;

        public AttendanceSession(int id, int teacherId, String subjectName, SessionState state, LocalDateTime openedAt, String room, String note, boolean overrideSession) {
            this.id = id;
            this.teacherId = teacherId;
            this.subjectName = subjectName;
            this.state = state;
            this.openedAt = openedAt;
            this.room = room;
            this.note = note;
            this.overrideSession = overrideSession;
        }

        public int getId() {
            return id;
        }

        public int getTeacherId() {
            return teacherId;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public SessionState getState() {
            return state;
        }

        public void setState(SessionState state) {
            this.state = state;
        }

        public LocalDateTime getOpenedAt() {
            return openedAt;
        }

        public void setOpenedAt(LocalDateTime openedAt) {
            this.openedAt = openedAt;
        }

        public String getRoom() {
            return room;
        }

        public void setRoom(String room) {
            this.room = room;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public boolean isOverrideSession() {
            return overrideSession;
        }

        public void setOverrideSession(boolean overrideSession) {
            this.overrideSession = overrideSession;
        }
    }

    public static final class AttendanceRecord {

        private final int id;
        private final String studentId;
        private final int teacherId;
        private final String studentName;
        private final String subjectName;
        private final LocalDateTime timestamp;
        private final AttendanceSource source;
        private final AttendanceStatus status;
        private final String note;

        public AttendanceRecord(int id, String studentId, int teacherId, String studentName, String subjectName, LocalDateTime timestamp, AttendanceSource source, AttendanceStatus status, String note) {
            this.id = id;
            this.studentId = studentId;
            this.teacherId = teacherId;
            this.studentName = studentName;
            this.subjectName = subjectName;
            this.timestamp = timestamp;
            this.source = source;
            this.status = status;
            this.note = note;
        }

        public int getId() {
            return id;
        }

        public String getStudentId() {
            return studentId;
        }

        public int getTeacherId() {
            return teacherId;
        }

        public String getStudentName() {
            return studentName;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public AttendanceSource getSource() {
            return source;
        }

        public AttendanceStatus getStatus() {
            return status;
        }

        public String getNote() {
            return note;
        }
    }

    public static final class EmailDispatch {

        private final int id;
        private final String recipient;
        private final String subject;
        private final String preview;
        private EmailStatus status;
        private LocalDateTime timestamp;

        public EmailDispatch(int id, String recipient, String subject, String preview, EmailStatus status, LocalDateTime timestamp) {
            this.id = id;
            this.recipient = recipient;
            this.subject = subject;
            this.preview = preview;
            this.status = status;
            this.timestamp = timestamp;
        }

        public int getId() {
            return id;
        }

        public String getRecipient() {
            return recipient;
        }

        public String getSubject() {
            return subject;
        }

        public String getPreview() {
            return preview;
        }

        public EmailStatus getStatus() {
            return status;
        }

        public void setStatus(EmailStatus status) {
            this.status = status;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}
