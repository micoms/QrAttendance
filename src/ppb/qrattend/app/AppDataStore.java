package ppb.qrattend.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ppb.qrattend.app.store.StoreMessages;
import ppb.qrattend.app.store.StoreTeacherAssistantSupport;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.AttendanceSession;
import ppb.qrattend.model.AppDomain.EmailDispatch;
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.AppDomain.SessionState;
import ppb.qrattend.model.AppDomain.StudentProfile;
import ppb.qrattend.model.AppDomain.StudentRemovalRequest;
import ppb.qrattend.model.AppDomain.TeacherProfile;
import ppb.qrattend.model.ModelUser;
import ppb.qrattend.service.AiInsightService;
import ppb.qrattend.service.AttendanceService;
import ppb.qrattend.service.EmailDispatchService;
import ppb.qrattend.service.ReportService;
import ppb.qrattend.service.ScheduleService;
import ppb.qrattend.service.ServiceResult;
import ppb.qrattend.service.StudentService;
import ppb.qrattend.service.TeacherService;

public class AppDataStore implements StoreTeacherAssistantSupport.DataProvider {

    public static final class ActionResult {

        private final boolean success;
        private final boolean warning;
        private final String message;

        private ActionResult(boolean success, boolean warning, String message) {
            this.success = success;
            this.warning = warning;
            this.message = StoreMessages.clean(message);
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, false, message);
        }

        public static ActionResult warning(String message) {
            return new ActionResult(false, true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, false, message);
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
    }

    private final List<Runnable> listeners = new ArrayList<>();

    private final TeacherService teacherService = new TeacherService();
    private final StudentService studentService = new StudentService();
    private final ScheduleService scheduleService = new ScheduleService();
    private final AttendanceService attendanceService = new AttendanceService();
    private final ReportService reportService = new ReportService();
    private final EmailDispatchService emailDispatchService = new EmailDispatchService();
    private final StoreTeacherAssistantSupport teacherAssistantSupport =
            new StoreTeacherAssistantSupport(this, AiInsightService.createDefault(), this::notifyListeners);

    public AppDataStore() {
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public ModelUser prepareWorkspaceUser(ModelUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.isAdmin()) {
            return authenticatedUser;
        }

        TeacherProfile teacher = findTeacher(authenticatedUser.getUserId());
        if (teacher != null) {
            return new ModelUser(teacher.getId(), teacher.getFullName(), teacher.getEmail(), authenticatedUser.getRole());
        }

        return new ModelUser(
                authenticatedUser.getUserId(),
                safe(authenticatedUser.getFullName()),
                normalizeEmail(authenticatedUser.getEmail()),
                authenticatedUser.getRole()
        );
    }

    public List<TeacherProfile> getTeachers() {
        return listOrEmpty(teacherService.getTeachers());
    }

    public List<StudentProfile> getStudentsForTeacher(int teacherId) {
        return listOrEmpty(studentService.getStudentsForTeacher(teacherId));
    }

    public List<StudentProfile> getAllStudents() {
        return listOrEmpty(studentService.getAllStudents());
    }

    public List<ScheduleSlot> getSchedules() {
        return listOrEmpty(scheduleService.getSchedules());
    }

    public List<ScheduleSlot> getSchedulesForTeacher(int teacherId) {
        return listOrEmpty(scheduleService.getSchedulesForTeacher(teacherId));
    }

    public List<ScheduleSlot> getTodaySchedules() {
        return listOrEmpty(scheduleService.getTodaySchedules());
    }

    public List<ScheduleChangeRequest> getScheduleRequests() {
        return listOrEmpty(scheduleService.getScheduleRequests());
    }

    public List<ScheduleChangeRequest> getScheduleRequestsForTeacher(int teacherId) {
        return listOrEmpty(scheduleService.getScheduleRequestsForTeacher(teacherId));
    }

    @Override
    public List<AttendanceRecord> getAttendanceRecords() {
        return listOrEmpty(attendanceService.getAttendanceRecords());
    }

    @Override
    public List<AttendanceRecord> getAttendanceRecordsForTeacher(int teacherId) {
        return listOrEmpty(attendanceService.getAttendanceRecordsForTeacher(teacherId));
    }

    @Override
    public List<AttendanceRecord> getRecentAttendanceRecords(int limit, Integer teacherId) {
        return listOrEmpty(attendanceService.getRecentAttendanceRecords(limit, teacherId));
    }

    public List<EmailDispatch> getEmailDispatches() {
        return listOrEmpty(emailDispatchService.getEmailDispatches());
    }

    public List<EmailDispatch> getRecentEmailDispatches(int limit) {
        return listOrEmpty(emailDispatchService.getRecentEmailDispatches(limit));
    }

    public String getTeacherAssistantConversation(int teacherId, String scopeKey) {
        return teacherAssistantSupport.getConversation(teacherId, scopeKey);
    }

    public ActionResult askTeacherAssistant(int teacherId, String scopeKey, String question) {
        return teacherAssistantSupport.ask(teacherId, scopeKey, question);
    }

    public ActionResult clearTeacherAssistantConversation(int teacherId, String scopeKey) {
        return teacherAssistantSupport.clear(teacherId, scopeKey);
    }

    public List<String> getSubjectOptions(Integer teacherId) {
        return listOrEmpty(reportService.getSubjectOptions(teacherId));
    }

    @Override
    public TeacherProfile findTeacher(int teacherId) {
        return dataOrNull(teacherService.findTeacher(teacherId));
    }

    public StudentProfile findStudentForTeacher(int teacherId, String studentId) {
        return dataOrNull(studentService.findStudentForTeacher(teacherId, studentId));
    }

    public int getTeacherCount() {
        ReportService.AdminDashboardMetrics metrics = getAdminMetrics();
        return metrics == null ? getTeachers().size() : metrics.getTeacherCount();
    }

    public int getPendingRequestCount() {
        ReportService.AdminDashboardMetrics metrics = getAdminMetrics();
        return metrics == null ? countPendingRequests() : metrics.getPendingRequestCount();
    }

    public int getFailedEmailCount() {
        ReportService.AdminDashboardMetrics metrics = getAdminMetrics();
        if (metrics != null) {
            return metrics.getFailedEmailCount();
        }
        Integer failedCount = dataOrNull(emailDispatchService.getFailedEmailCount());
        return failedCount == null ? 0 : failedCount;
    }

    public int getActiveClassCount() {
        ReportService.AdminDashboardMetrics metrics = getAdminMetrics();
        return metrics == null ? getTodaySchedules().size() : metrics.getActiveClassCount();
    }

    @Override
    public int getStudentCountForTeacher(int teacherId) {
        ReportService.TeacherDashboardMetrics metrics = getTeacherMetrics(teacherId);
        return metrics == null ? getStudentsForTeacher(teacherId).size() : metrics.getStudentCount();
    }

    @Override
    public int getPendingStudentRemovalCountForTeacher(int teacherId) {
        ReportService.TeacherDashboardMetrics metrics = getTeacherMetrics(teacherId);
        if (metrics != null) {
            int pendingSchedule = countPendingTeacherScheduleRequests(teacherId);
            return Math.max(0, metrics.getPendingRequestCount() - pendingSchedule);
        }

        int count = 0;
        for (StudentRemovalRequest request : getStudentRemovalRequestsForTeacher(teacherId)) {
            if (request.getStatus() == ppb.qrattend.model.AppDomain.ScheduleRequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    @Override
    public ScheduleSlot getActiveScheduleForTeacher(int teacherId) {
        return dataOrNull(scheduleService.getActiveScheduleForTeacher(teacherId));
    }

    @Override
    public ScheduleSlot getNextScheduleForTeacher(int teacherId) {
        return dataOrNull(scheduleService.getNextScheduleForTeacher(teacherId));
    }

    @Override
    public AttendanceSession getSessionForTeacher(int teacherId) {
        AttendanceSession session = dataOrNull(attendanceService.getSessionForTeacher(teacherId));
        if (session != null) {
            return session;
        }
        return new AttendanceSession(
                0,
                teacherId,
                "No Class Open",
                SessionState.LOCKED,
                LocalDateTime.now(),
                "-",
                "",
                false
        );
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

    public ActionResult addStudent(int actorUserId, int teacherId, String sectionName, String studentId, String fullName, String email) {
        return handleWrite(studentService.createStudentProfileByAdmin(actorUserId, teacherId, sectionName, studentId, fullName, email));
    }

    public ActionResult addStudent(int teacherId, String studentId, String fullName, String email) {
        return handleWrite(studentService.createStudentProfile(teacherId, studentId, fullName, email));
    }

    public ActionResult resendStudentQr(int teacherId, String studentId) {
        return handleWrite(studentService.resendStudentQr(teacherId, studentId));
    }

    public List<StudentRemovalRequest> getStudentRemovalRequests() {
        return listOrEmpty(studentService.getStudentRemovalRequests());
    }

    public List<StudentRemovalRequest> getStudentRemovalRequestsForTeacher(int teacherId) {
        return listOrEmpty(studentService.getStudentRemovalRequestsForTeacher(teacherId));
    }

    public ActionResult requestStudentRemoval(int teacherId, String studentId, String reason) {
        return handleWrite(studentService.submitStudentRemovalRequest(teacherId, studentId, reason));
    }

    public ActionResult reviewStudentRemovalRequest(int reviewerUserId, int requestId, boolean approve, String reviewerName) {
        return handleWrite(studentService.reviewStudentRemovalRequest(reviewerUserId, requestId, approve, reviewerName));
    }

    public ActionResult addScheduleSlot(int actorUserId, int teacherId, String subjectName,
            java.time.DayOfWeek day, java.time.LocalTime start, java.time.LocalTime end, String room) {
        return handleWrite(scheduleService.createApprovedScheduleSlot(actorUserId, teacherId, subjectName, subjectName, day, start, end, room));
    }

    public ActionResult addScheduleSlot(int teacherId, String subjectName,
            java.time.DayOfWeek day, java.time.LocalTime start, java.time.LocalTime end, String room) {
        return handleWrite(scheduleService.createApprovedScheduleSlot(teacherId, teacherId, subjectName, subjectName, day, start, end, room));
    }

    public ActionResult submitScheduleChangeRequest(int teacherId, int sourceSlotId, String subjectName,
            java.time.DayOfWeek day, java.time.LocalTime start, java.time.LocalTime end, String room,
            String reason, String requesterName) {
        return handleWrite(scheduleService.submitScheduleCorrectionRequest(
                teacherId, sourceSlotId, subjectName, day, start, end, room, reason
        ));
    }

    public ActionResult reviewScheduleRequest(int reviewerUserId, int requestId, boolean approve, String reviewerName) {
        ServiceResult<ScheduleChangeRequest> result = approve
                ? scheduleService.approveScheduleRequest(reviewerUserId, requestId)
                : scheduleService.rejectScheduleRequest(reviewerUserId, requestId, buildRejectionNote(reviewerName));
        return handleWrite(result);
    }

    public ActionResult openOverrideSession(int teacherId, String subjectName, String reason) {
        return handleWrite(attendanceService.openOverrideSession(teacherId, subjectName, reason));
    }

    public ActionResult closeOverrideSession(int teacherId) {
        return handleWrite(attendanceService.closeOverrideSession(teacherId));
    }

    public ActionResult markAttendanceFromQr(int teacherId, String qrValue) {
        return handleWrite(attendanceService.recordQrAttendance(teacherId, qrValue));
    }

    public ActionResult markManualAttendance(int teacherId, String studentId, String note) {
        return handleWrite(attendanceService.recordManualAttendance(teacherId, studentId, note));
    }

    public String exportAttendanceSummary(Integer teacherId, String subjectFilter) {
        String summary = dataOrNull(reportService.exportAttendanceSummary(teacherId, subjectFilter));
        return summary == null || summary.isBlank()
                ? "No matching attendance records were found."
                : summary;
    }

    private ReportService.AdminDashboardMetrics getAdminMetrics() {
        return dataOrNull(reportService.getAdminDashboardMetrics());
    }

    private ReportService.TeacherDashboardMetrics getTeacherMetrics(int teacherId) {
        return dataOrNull(reportService.getTeacherDashboardMetrics(teacherId));
    }

    private int countPendingRequests() {
        return countPendingScheduleRequests() + countPendingStudentRemovalRequests();
    }

    private int countPendingScheduleRequests() {
        int count = 0;
        for (ScheduleChangeRequest request : getScheduleRequests()) {
            if (request.getStatus() == ppb.qrattend.model.AppDomain.ScheduleRequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    private int countPendingTeacherScheduleRequests(int teacherId) {
        int count = 0;
        for (ScheduleChangeRequest request : getScheduleRequestsForTeacher(teacherId)) {
            if (request.getStatus() == ppb.qrattend.model.AppDomain.ScheduleRequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    private int countPendingStudentRemovalRequests() {
        int count = 0;
        for (StudentRemovalRequest request : getStudentRemovalRequests()) {
            if (request.getStatus() == ppb.qrattend.model.AppDomain.ScheduleRequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    private ActionResult handleWrite(ServiceResult<?> result) {
        if (result == null) {
            return ActionResult.failure("This part of the app is not ready right now. Please ask the admin.");
        }
        if (result.isSuccess()) {
            notifyListeners();
            return ActionResult.success(result.getMessage());
        }
        if (result.isWarning()) {
            notifyListeners();
            return ActionResult.warning(result.getMessage());
        }
        return ActionResult.failure(result.getMessage());
    }

    private <T> List<T> listOrEmpty(ServiceResult<List<T>> result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return new ArrayList<>();
        }
        return result.getData();
    }

    private <T> T dataOrNull(ServiceResult<T> result) {
        if (result == null || !result.isSuccess()) {
            return null;
        }
        return result.getData();
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private String buildRejectionNote(String reviewerName) {
        String cleaned = safe(reviewerName);
        return cleaned.isBlank() ? "Rejected by admin." : "Rejected by " + cleaned + ".";
    }

    private String normalizeEmail(String email) {
        return safe(email).toLowerCase(Locale.ENGLISH);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
