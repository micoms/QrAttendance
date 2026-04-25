package ppb.qrattend.app;

import java.util.ArrayList;
import java.util.List;
import ppb.qrattend.model.CoreModels.AttendanceRecord;
import ppb.qrattend.model.CoreModels.AttendanceSession;
import ppb.qrattend.model.CoreModels.EmailLog;
import ppb.qrattend.model.CoreModels.ReportSummary;
import ppb.qrattend.model.CoreModels.RequestStatus;
import ppb.qrattend.model.CoreModels.Room;
import ppb.qrattend.model.CoreModels.Schedule;
import ppb.qrattend.model.CoreModels.ScheduleRequest;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.Student;
import ppb.qrattend.model.CoreModels.StudentRemovalRequest;
import ppb.qrattend.model.CoreModels.Subject;
import ppb.qrattend.model.CoreModels.Teacher;
import ppb.qrattend.model.ModelUser;
import ppb.qrattend.service.AiChatService;
import ppb.qrattend.service.AttendanceService;
import ppb.qrattend.service.EmailService;
import ppb.qrattend.service.ReportService;
import ppb.qrattend.service.ScheduleService;
import ppb.qrattend.service.SectionService;
import ppb.qrattend.service.ServiceResult;
import ppb.qrattend.service.StudentService;
import ppb.qrattend.service.TeacherService;
import ppb.qrattend.util.AppClock;

public class AppStore {

    public static final class ActionResult {

        private final boolean success;
        private final boolean warning;
        private final String message;
        private final String detail;

        private ActionResult(boolean success, boolean warning, String message, String detail) {
            this.success = success;
            this.warning = warning;
            this.message = message == null ? "" : message.trim();
            this.detail = detail == null ? "" : detail.trim();
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, false, message, "");
        }

        public static ActionResult warning(String message, String detail) {
            return new ActionResult(false, true, message, detail);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, false, message, "");
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isWarning() {
            return warning;
        }

        public String getMessage() {
            return message;
        }

        public String getDetail() {
            return detail;
        }
    }

    private final List<Runnable> listeners = new ArrayList<>();

    private final TeacherService teacherService = new TeacherService();
    private final SectionService sectionService = new SectionService();
    private final StudentService studentService = new StudentService();
    private final ScheduleService scheduleService = new ScheduleService();
    private final AttendanceService attendanceService = new AttendanceService();
    private final ReportService reportService = new ReportService();
    private final EmailService emailService = new EmailService();
    private final AiChatService aiChatService = new AiChatService();

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public ModelUser prepareWorkspaceUser(ModelUser authenticatedUser) {
        return authenticatedUser;
    }

    public List<Teacher> getTeachers() {
        return listOrEmpty(teacherService.getTeachers());
    }

    public Teacher findTeacher(int teacherId) {
        return dataOrNull(teacherService.findTeacher(teacherId));
    }

    public List<Section> getSections() {
        return listOrEmpty(sectionService.getSections());
    }

    public List<Subject> getSubjects() {
        return listOrEmpty(scheduleService.getSubjects());
    }

    public List<Room> getRooms() {
        return listOrEmpty(scheduleService.getRooms());
    }

    public List<Student> getAllStudents() {
        return listOrEmpty(studentService.getAllStudents());
    }

    public List<Student> getStudentsBySection(int sectionId) {
        return listOrEmpty(studentService.getStudentsBySection(sectionId));
    }

    public List<Student> getStudentsForTeacher(int teacherId) {
        return listOrEmpty(studentService.getStudentsForTeacher(teacherId));
    }

    public List<String> getTeacherSectionNames(int teacherId) {
        return listOrEmpty(studentService.getTeacherSectionNames(teacherId));
    }

    public List<Schedule> getSchedules() {
        return listOrEmpty(scheduleService.getSchedules());
    }

    public List<Schedule> getSchedulesForTeacher(int teacherId) {
        return listOrEmpty(scheduleService.getSchedulesForTeacher(teacherId));
    }

    public Schedule getCurrentScheduleForTeacher(int teacherId) {
        return dataOrNull(scheduleService.getCurrentScheduleForTeacher(teacherId));
    }

    public Schedule getNextScheduleForTeacher(int teacherId) {
        return dataOrNull(scheduleService.getNextScheduleForTeacher(teacherId));
    }

    public List<ScheduleRequest> getScheduleRequests() {
        return listOrEmpty(scheduleService.getScheduleRequests());
    }

    public List<ScheduleRequest> getScheduleRequestsForTeacher(int teacherId) {
        return listOrEmpty(scheduleService.getScheduleRequestsForTeacher(teacherId));
    }

    public List<StudentRemovalRequest> getStudentRemovalRequests() {
        return listOrEmpty(studentService.getStudentRemovalRequests());
    }

    public List<StudentRemovalRequest> getStudentRemovalRequestsForTeacher(int teacherId) {
        return listOrEmpty(studentService.getStudentRemovalRequestsForTeacher(teacherId));
    }

    public AttendanceSession getCurrentSessionForTeacher(int teacherId) {
        AttendanceSession session = dataOrNull(attendanceService.getCurrentSessionForTeacher(teacherId));
        if (session != null) {
            return session;
        }
        return new AttendanceSession(0, teacherId, 0, "No class", 0, "No class", "", AppClock.today(),
                AppClock.nowDateTime(), false, "", ppb.qrattend.model.CoreModels.SessionStatus.NONE);
    }

    public List<Student> getCurrentClassStudents(int teacherId) {
        return listOrEmpty(attendanceService.getCurrentClassStudents(teacherId));
    }

    public List<AttendanceRecord> getRecentAttendanceRecords(Integer teacherId, int limit) {
        return listOrEmpty(attendanceService.getRecentRecords(teacherId, limit));
    }

    public ReportSummary getReportSummary(Integer teacherId, Integer sectionId, Integer subjectId) {
        return dataOrNull(reportService.getSummary(teacherId, sectionId, subjectId));
    }

    public List<AttendanceRecord> getReportRecords(Integer teacherId, Integer sectionId, Integer subjectId) {
        return listOrEmpty(reportService.getRecords(teacherId, sectionId, subjectId));
    }

    public List<String> getReportSubjectNames(Integer teacherId) {
        return listOrEmpty(reportService.getSubjectsForReport(teacherId));
    }

    public List<EmailLog> getRecentEmailLogs(int limit) {
        return listOrEmpty(emailService.getRecentLogs(limit));
    }

    public ActionResult addTeacher(int actorUserId, String fullName, String email) {
        return handleWrite(teacherService.createTeacherAccount(actorUserId, fullName, email));
    }

    public ActionResult resendTeacherPassword(int actorUserId, int teacherId) {
        return handleWrite(teacherService.resendTeacherPassword(actorUserId, teacherId));
    }

    public ActionResult resetTeacherPassword(int actorUserId, int teacherId) {
        return handleWrite(teacherService.resetTeacherPassword(actorUserId, teacherId));
    }

    public ActionResult addSection(String sectionName) {
        return handleWrite(sectionService.addSection(sectionName));
    }

    public ActionResult addSubject(String subjectName) {
        return handleWrite(scheduleService.addSubject(subjectName));
    }

    public ActionResult addRoom(String roomName) {
        return handleWrite(scheduleService.addRoom(roomName));
    }

    public ActionResult addStudent(int sectionId, String studentCode, String fullName, String email) {
        return handleWrite(studentService.addStudent(sectionId, studentCode, fullName, email));
    }

    public ActionResult importStudents(int sectionId, String pastedRows) {
        return handleWrite(studentService.importStudents(sectionId, pastedRows));
    }

    public ActionResult resendStudentQr(int studentId) {
        return handleWrite(studentService.resendStudentQr(studentId));
    }

    public ActionResult addSchedule(int teacherId, int sectionId, int subjectId, int roomId,
            java.time.DayOfWeek day, java.time.LocalTime startTime, java.time.LocalTime endTime) {
        return handleWrite(scheduleService.addSchedule(teacherId, sectionId, subjectId, roomId, day, startTime, endTime));
    }

    public ActionResult submitScheduleRequest(int teacherId, int scheduleId, int sectionId, int subjectId, int roomId,
            java.time.DayOfWeek day, java.time.LocalTime startTime, java.time.LocalTime endTime, String reason) {
        return handleWrite(scheduleService.submitScheduleRequest(teacherId, scheduleId, sectionId, subjectId, roomId, day, startTime, endTime, reason));
    }

    public ActionResult reviewScheduleRequest(int reviewerId, int requestId, boolean approve) {
        return handleWrite(scheduleService.reviewScheduleRequest(reviewerId, requestId, approve));
    }

    public ActionResult submitStudentRemovalRequest(int teacherId, int studentId, String reason) {
        return handleWrite(studentService.submitStudentRemovalRequest(teacherId, studentId, reason));
    }

    public ActionResult reviewStudentRemovalRequest(int reviewerId, int requestId, boolean approve) {
        return handleWrite(studentService.reviewStudentRemovalRequest(reviewerId, requestId, approve));
    }

    public ActionResult startTemporaryClass(int teacherId, int sectionId, int subjectId, String reason) {
        return handleWrite(attendanceService.startTemporaryClass(teacherId, sectionId, subjectId, reason));
    }

    public ActionResult endTemporaryClass(int teacherId) {
        return handleWrite(attendanceService.endTemporaryClass(teacherId));
    }

    public ActionResult markAttendanceFromQr(int teacherId, String qrValue) {
        return handleWrite(attendanceService.markAttendanceFromQr(teacherId, qrValue));
    }

    public ActionResult markManualAttendance(int teacherId, int studentId, String note) {
        return handleWrite(attendanceService.markManualAttendance(teacherId, studentId, note));
    }

    public ActionResult updateTeacher(int teacherId, String fullName, String email) {
        return handleWrite(teacherService.updateTeacher(teacherId, fullName, email));
    }

    public ActionResult deactivateTeacher(int teacherId) {
        return handleWrite(teacherService.deactivateTeacher(teacherId));
    }

    public ActionResult changePassword(int userId, String newPassword) {
        return handleWrite(teacherService.changePassword(userId, newPassword));
    }

    public ActionResult updateStudent(int studentId, int sectionId, String code, String name, String email) {
        return handleWrite(studentService.updateStudent(studentId, sectionId, code, name, email));
    }

    public ActionResult deactivateStudent(int studentId) {
        return handleWrite(studentService.deactivateStudent(studentId));
    }

    public ActionResult updateSchedule(int scheduleId, int teacherId, int sectionId, int subjectId, int roomId,
            java.time.DayOfWeek day, java.time.LocalTime start, java.time.LocalTime end) {
        return handleWrite(scheduleService.updateSchedule(scheduleId, teacherId, sectionId, subjectId, roomId, day, start, end));
    }

    public ActionResult deactivateSchedule(int scheduleId) {
        return handleWrite(scheduleService.deactivateSchedule(scheduleId));
    }

    public ActionResult markAllAbsent(int teacherId) {
        return handleWrite(attendanceService.markAllAbsent(teacherId));
    }

    public ActionResult exportCsv(List<AttendanceRecord> records, java.io.File file) {
        return handleWrite(reportService.exportCsv(records, file));
    }

    public ActionResult renameSection(int sectionId, String newName) {
        return handleWrite(sectionService.renameSection(sectionId, newName));
    }

    public ActionResult deleteSection(int sectionId) {
        return handleWrite(sectionService.deleteSection(sectionId));
    }

    public ActionResult renameSubject(int subjectId, String newName) {
        return handleWrite(scheduleService.renameSubject(subjectId, newName));
    }

    public ActionResult deleteSubject(int subjectId) {
        return handleWrite(scheduleService.deleteSubject(subjectId));
    }

    public ActionResult renameRoom(int roomId, String newName) {
        return handleWrite(scheduleService.renameRoom(roomId, newName));
    }

    public ActionResult deleteRoom(int roomId) {
        return handleWrite(scheduleService.deleteRoom(roomId));
    }

    public String getAiConversation(int teacherId, String scopeKey) {
        return aiChatService.getConversation(teacherId, scopeKey);
    }

    public ActionResult askAi(int teacherId, String scopeKey, String question) {
        List<String> contextLines = buildAiContext(teacherId, scopeKey);
        return handleWrite(aiChatService.ask(teacherId, scopeKey, question, contextLines));
    }

    public ActionResult clearAiConversation(int teacherId, String scopeKey) {
        return handleWrite(aiChatService.clear(teacherId, scopeKey));
    }

    private List<String> buildAiContext(int teacherId, String scopeKey) {
        List<String> lines = new ArrayList<>();
        Schedule current = getCurrentScheduleForTeacher(teacherId);
        if (current != null) {
            lines.add("Current class: " + current.subjectName() + " for " + current.sectionName() + " at " + current.getTimeLabel());
        } else {
            lines.add("Current class: none");
        }

        if (scopeKey.startsWith("reports")) {
            ReportSummary summary = getReportSummary(teacherId, null, null);
            if (summary != null) {
                lines.add("Report summary: " + summary.toPlainText().replace(System.lineSeparator(), " | "));
            }
            List<AttendanceRecord> reportRecords = getReportRecords(teacherId, null, null);
            int reportLimit = Math.min(8, reportRecords.size());
            for (int i = 0; i < reportLimit; i++) {
                AttendanceRecord record = reportRecords.get(i);
                lines.add("Record: " + record.studentName() + " | " + record.subjectName() + " | " + record.status().getLabel());
            }
        } else if (scopeKey.startsWith("attendance")) {
            AttendanceSession session = getCurrentSessionForTeacher(teacherId);
            lines.add("Attendance page status: " + session.status().getLabel());
            lines.add("Current class students: " + getCurrentClassStudents(teacherId).size());
            for (AttendanceRecord record : getRecentAttendanceRecords(teacherId, 6)) {
                lines.add("Recent attendance: " + record.studentName() + " | " + record.method().getLabel() + " | " + record.status().getLabel());
            }
        } else {
            lines.add("Teacher class sections: " + String.join(", ", getTeacherSectionNames(teacherId)));
            lines.add("Student count: " + getStudentsForTeacher(teacherId).size());
            lines.add("Pending schedule requests: " + countPending(getScheduleRequestsForTeacher(teacherId)));
            lines.add("Pending student removal requests: " + countPendingStudentRemoval(getStudentRemovalRequestsForTeacher(teacherId)));
        }
        return lines;
    }

    private int countPending(List<ScheduleRequest> requests) {
        int count = 0;
        for (ScheduleRequest request : requests) {
            if (request.status() == RequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    private int countPendingStudentRemoval(List<StudentRemovalRequest> requests) {
        int count = 0;
        for (StudentRemovalRequest request : requests) {
            if (request.status() == RequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    private ActionResult handleWrite(ServiceResult<?> result) {
        if (result == null) {
            return ActionResult.failure("This action is not ready right now.");
        }
        if (result.isSuccess()) {
            notifyListeners();
            return ActionResult.success(result.getMessage());
        }
        if (result.isWarning()) {
            notifyListeners();
            String detail = "";
            if (result.getData() instanceof String) {
                detail = (String) result.getData();
            }
            return ActionResult.warning(result.getMessage(), detail);
        }
        return ActionResult.failure(result.getMessage());
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private <T> List<T> listOrEmpty(ServiceResult<List<T>> result) {
        if (result == null || (!result.isSuccess() && !result.isWarning()) || result.getData() == null) {
            return new ArrayList<>();
        }
        return result.getData();
    }

    private <T> T dataOrNull(ServiceResult<T> result) {
        if (result == null || (!result.isSuccess() && !result.isWarning())) {
            return null;
        }
        return result.getData();
    }
}
