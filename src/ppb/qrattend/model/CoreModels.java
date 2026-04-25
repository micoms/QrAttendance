package ppb.qrattend.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class CoreModels {

    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    public static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private CoreModels() {
    }

    public enum EmailStatus {
        NOT_SENT("Not sent"),
        SENT("Sent"),
        FAILED("Failed");

        private final String label;

        EmailStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum RequestStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String label;

        RequestStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum SessionStatus {
        OPEN("Class open"),
        CLOSED("Closed"),
        NONE("No class open");

        private final String label;

        SessionStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum AttendanceMethod {
        QR("QR"),
        MANUAL("Manual");

        private final String label;

        AttendanceMethod(String label) {
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

        public int id()                    { return id; }
        public String fullName()           { return fullName; }
        public String email()              { return email; }
        public boolean active()            { return active; }
        public EmailStatus emailStatus()   { return emailStatus; }
    }

    public static final class Section {
        private final int id;
        private final String name;

        public Section(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int id()      { return id; }
        public String name() { return name; }
    }

    public static final class Subject {
        private final int id;
        private final String name;

        public Subject(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int id()      { return id; }
        public String name() { return name; }
    }

    public static final class Room {
        private final int id;
        private final String name;

        public Room(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int id()      { return id; }
        public String name() { return name; }
    }

    public static final class Student {
        private final int id;
        private final String studentCode;
        private final String fullName;
        private final String email;
        private final int sectionId;
        private final String sectionName;
        private final boolean active;
        private final EmailStatus qrStatus;

        public Student(int id, String studentCode, String fullName, String email,
                int sectionId, String sectionName, boolean active, EmailStatus qrStatus) {
            this.id = id;
            this.studentCode = studentCode;
            this.fullName = fullName;
            this.email = email;
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.active = active;
            this.qrStatus = qrStatus;
        }

        public int id()                { return id; }
        public String studentCode()    { return studentCode; }
        public String fullName()       { return fullName; }
        public String email()          { return email; }
        public int sectionId()         { return sectionId; }
        public String sectionName()    { return sectionName; }
        public boolean active()        { return active; }
        public EmailStatus qrStatus()  { return qrStatus; }
    }

    public static final class Schedule {
        private final int id;
        private final int teacherId;
        private final String teacherName;
        private final int sectionId;
        private final String sectionName;
        private final int subjectId;
        private final String subjectName;
        private final int roomId;
        private final String roomName;
        private final DayOfWeek day;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final boolean active;

        public Schedule(int id, int teacherId, String teacherName, int sectionId, String sectionName,
                int subjectId, String subjectName, int roomId, String roomName,
                DayOfWeek day, LocalTime startTime, LocalTime endTime, boolean active) {
            this.id = id;
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.roomId = roomId;
            this.roomName = roomName;
            this.day = day;
            this.startTime = startTime;
            this.endTime = endTime;
            this.active = active;
        }

        public int id()                { return id; }
        public int teacherId()         { return teacherId; }
        public String teacherName()    { return teacherName; }
        public int sectionId()         { return sectionId; }
        public String sectionName()    { return sectionName; }
        public int subjectId()         { return subjectId; }
        public String subjectName()    { return subjectName; }
        public int roomId()            { return roomId; }
        public String roomName()       { return roomName; }
        public DayOfWeek day()         { return day; }
        public LocalTime startTime()   { return startTime; }
        public LocalTime endTime()     { return endTime; }
        public boolean active()        { return active; }

        public String getTimeLabel() {
            return startTime.format(TIME_FORMAT) + " - " + endTime.format(TIME_FORMAT);
        }
    }

    public static final class ScheduleRequest {
        private final int id;
        private final int scheduleId;
        private final int teacherId;
        private final String teacherName;
        private final int sectionId;
        private final String sectionName;
        private final int subjectId;
        private final String subjectName;
        private final int roomId;
        private final String roomName;
        private final DayOfWeek day;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String reason;
        private final RequestStatus status;
        private final String reviewedBy;
        private final LocalDateTime createdAt;
        private final LocalDateTime reviewedAt;

        public ScheduleRequest(int id, int scheduleId, int teacherId, String teacherName,
                int sectionId, String sectionName, int subjectId, String subjectName,
                int roomId, String roomName, DayOfWeek day, LocalTime startTime, LocalTime endTime,
                String reason, RequestStatus status, String reviewedBy,
                LocalDateTime createdAt, LocalDateTime reviewedAt) {
            this.id = id;
            this.scheduleId = scheduleId;
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.roomId = roomId;
            this.roomName = roomName;
            this.day = day;
            this.startTime = startTime;
            this.endTime = endTime;
            this.reason = reason;
            this.status = status;
            this.reviewedBy = reviewedBy;
            this.createdAt = createdAt;
            this.reviewedAt = reviewedAt;
        }

        public int id()                    { return id; }
        public int scheduleId()            { return scheduleId; }
        public int teacherId()             { return teacherId; }
        public String teacherName()        { return teacherName; }
        public int sectionId()             { return sectionId; }
        public String sectionName()        { return sectionName; }
        public int subjectId()             { return subjectId; }
        public String subjectName()        { return subjectName; }
        public int roomId()                { return roomId; }
        public String roomName()           { return roomName; }
        public DayOfWeek day()             { return day; }
        public LocalTime startTime()       { return startTime; }
        public LocalTime endTime()         { return endTime; }
        public String reason()             { return reason; }
        public RequestStatus status()      { return status; }
        public String reviewedBy()         { return reviewedBy; }
        public LocalDateTime createdAt()   { return createdAt; }
        public LocalDateTime reviewedAt()  { return reviewedAt; }

        public String getTimeLabel() {
            return startTime.format(TIME_FORMAT) + " - " + endTime.format(TIME_FORMAT);
        }
    }

    public static final class StudentRemovalRequest {
        private final int id;
        private final int teacherId;
        private final String teacherName;
        private final int studentId;
        private final String studentCode;
        private final String studentName;
        private final String sectionName;
        private final String reason;
        private final RequestStatus status;
        private final String reviewedBy;
        private final LocalDateTime createdAt;
        private final LocalDateTime reviewedAt;

        public StudentRemovalRequest(int id, int teacherId, String teacherName,
                int studentId, String studentCode, String studentName, String sectionName,
                String reason, RequestStatus status, String reviewedBy,
                LocalDateTime createdAt, LocalDateTime reviewedAt) {
            this.id = id;
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.studentId = studentId;
            this.studentCode = studentCode;
            this.studentName = studentName;
            this.sectionName = sectionName;
            this.reason = reason;
            this.status = status;
            this.reviewedBy = reviewedBy;
            this.createdAt = createdAt;
            this.reviewedAt = reviewedAt;
        }

        public int id()                    { return id; }
        public int teacherId()             { return teacherId; }
        public String teacherName()        { return teacherName; }
        public int studentId()             { return studentId; }
        public String studentCode()        { return studentCode; }
        public String studentName()        { return studentName; }
        public String sectionName()        { return sectionName; }
        public String reason()             { return reason; }
        public RequestStatus status()      { return status; }
        public String reviewedBy()         { return reviewedBy; }
        public LocalDateTime createdAt()   { return createdAt; }
        public LocalDateTime reviewedAt()  { return reviewedAt; }
    }

    public static final class AttendanceSession {
        private final int id;
        private final int teacherId;
        private final int sectionId;
        private final String sectionName;
        private final int subjectId;
        private final String subjectName;
        private final String roomName;
        private final LocalDate sessionDate;
        private final LocalDateTime openedAt;
        private final boolean temporary;
        private final String reason;
        private final SessionStatus status;

        public AttendanceSession(int id, int teacherId, int sectionId, String sectionName,
                int subjectId, String subjectName, String roomName, LocalDate sessionDate,
                LocalDateTime openedAt, boolean temporary, String reason, SessionStatus status) {
            this.id = id;
            this.teacherId = teacherId;
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.roomName = roomName;
            this.sessionDate = sessionDate;
            this.openedAt = openedAt;
            this.temporary = temporary;
            this.reason = reason;
            this.status = status;
        }

        public int id()                    { return id; }
        public int teacherId()             { return teacherId; }
        public int sectionId()             { return sectionId; }
        public String sectionName()        { return sectionName; }
        public int subjectId()             { return subjectId; }
        public String subjectName()        { return subjectName; }
        public String roomName()           { return roomName; }
        public LocalDate sessionDate()     { return sessionDate; }
        public LocalDateTime openedAt()    { return openedAt; }
        public boolean temporary()         { return temporary; }
        public String reason()             { return reason; }
        public SessionStatus status()      { return status; }
    }

    public static final class AttendanceRecord {
        private final int id;
        private final String studentCode;
        private final String studentName;
        private final String sectionName;
        private final String subjectName;
        private final LocalDateTime recordedAt;
        private final AttendanceMethod method;
        private final AttendanceStatus status;
        private final String note;

        public AttendanceRecord(int id, String studentCode, String studentName, String sectionName,
                String subjectName, LocalDateTime recordedAt, AttendanceMethod method,
                AttendanceStatus status, String note) {
            this.id = id;
            this.studentCode = studentCode;
            this.studentName = studentName;
            this.sectionName = sectionName;
            this.subjectName = subjectName;
            this.recordedAt = recordedAt;
            this.method = method;
            this.status = status;
            this.note = note;
        }

        public int id()                    { return id; }
        public String studentCode()        { return studentCode; }
        public String studentName()        { return studentName; }
        public String sectionName()        { return sectionName; }
        public String subjectName()        { return subjectName; }
        public LocalDateTime recordedAt()  { return recordedAt; }
        public AttendanceMethod method()   { return method; }
        public AttendanceStatus status()   { return status; }
        public String note()               { return note; }
    }

    public static final class EmailLog {
        private final int id;
        private final String emailType;
        private final String recipientEmail;
        private final String subjectLine;
        private final String infoText;
        private final EmailStatus status;
        private final LocalDateTime createdAt;

        public EmailLog(int id, String emailType, String recipientEmail, String subjectLine,
                String infoText, EmailStatus status, LocalDateTime createdAt) {
            this.id = id;
            this.emailType = emailType;
            this.recipientEmail = recipientEmail;
            this.subjectLine = subjectLine;
            this.infoText = infoText;
            this.status = status;
            this.createdAt = createdAt;
        }

        public int id()                    { return id; }
        public String emailType()          { return emailType; }
        public String recipientEmail()     { return recipientEmail; }
        public String subjectLine()        { return subjectLine; }
        public String infoText()           { return infoText; }
        public EmailStatus status()        { return status; }
        public LocalDateTime createdAt()   { return createdAt; }
    }

    public static final class ReportSummary {
        private final int totalStudents;
        private final int totalPresent;
        private final int totalLate;
        private final int totalRecords;

        public ReportSummary(int totalStudents, int totalPresent, int totalLate, int totalRecords) {
            this.totalStudents = totalStudents;
            this.totalPresent = totalPresent;
            this.totalLate = totalLate;
            this.totalRecords = totalRecords;
        }

        public int totalStudents() { return totalStudents; }
        public int totalPresent()  { return totalPresent; }
        public int totalLate()     { return totalLate; }
        public int totalRecords()  { return totalRecords; }

        public String toPlainText() {
            return "Students: " + totalStudents + System.lineSeparator()
                    + "Present: " + totalPresent + System.lineSeparator()
                    + "Late: " + totalLate + System.lineSeparator()
                    + "Saved records: " + totalRecords;
        }
    }
}
