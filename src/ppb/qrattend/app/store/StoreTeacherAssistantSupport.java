package ppb.qrattend.app.store;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ppb.qrattend.ai.AiInsightRequest;
import ppb.qrattend.ai.AiInsightResponse;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.AttendanceSession;
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.AppDomain.ScheduleRequestStatus;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.AppDomain.TeacherProfile;
import ppb.qrattend.service.AiInsightService;
import ppb.qrattend.service.ServiceResult;
import ppb.qrattend.app.AppDataStore;

public final class StoreTeacherAssistantSupport {

    public interface DataProvider {
        TeacherProfile findTeacher(int teacherId);
        ScheduleSlot getActiveScheduleForTeacher(int teacherId);
        ScheduleSlot getNextScheduleForTeacher(int teacherId);
        int getStudentCountForTeacher(int teacherId);
        List<ScheduleChangeRequest> getScheduleRequestsForTeacher(int teacherId);
        int getPendingStudentRemovalCountForTeacher(int teacherId);
        AttendanceSession getSessionForTeacher(int teacherId);
        List<AttendanceRecord> getRecentAttendanceRecords(int limit, Integer teacherId);
        List<AttendanceRecord> getAttendanceRecords();
        List<AttendanceRecord> getAttendanceRecordsForTeacher(int teacherId);
    }

    private final DataProvider dataProvider;
    private final AiInsightService aiInsightService;
    private final Runnable refreshAction;
    private final Map<String, List<String>> conversationHistory = new HashMap<>();

    public StoreTeacherAssistantSupport(DataProvider dataProvider, AiInsightService aiInsightService, Runnable refreshAction) {
        this.dataProvider = dataProvider;
        this.aiInsightService = aiInsightService;
        this.refreshAction = refreshAction;
    }

    public String getConversation(int teacherId, String scopeKey) {
        String key = buildConversationKey(teacherId, scopeKey);
        List<String> history = conversationHistory.get(key);
        if (history == null || history.isEmpty()) {
            if (!aiInsightService.isAiAvailable()) {
                return aiInsightService.getStatusMessage();
            }
            return """
                    Ask AI about attendance, reports, late students, or follow-up actions.

                    Example questions:
                    - Who should I follow up with based on my recent attendance?
                    - Summarize my current report filter.
                    - Are there unusual scan patterns in this class?
                    """.trim();
        }
        return String.join(System.lineSeparator() + System.lineSeparator(), history);
    }

    public AppDataStore.ActionResult ask(int teacherId, String scopeKey, String question) {
        String normalizedQuestion = safe(question);
        if (teacherId <= 0) {
            return AppDataStore.ActionResult.failure("Teacher account is required.");
        }
        if (normalizedQuestion.isBlank()) {
            return AppDataStore.ActionResult.failure("Type a question for the AI assistant.");
        }

        String historyKey = buildConversationKey(teacherId, scopeKey);
        List<String> history = conversationHistory.computeIfAbsent(historyKey, ignored -> new ArrayList<>());
        List<String> promptContext = buildAssistantContext(teacherId, scopeKey, history);

        AiInsightRequest request = new AiInsightRequest(
                "CHAT_" + sanitizeKeySegment(scopeKey).toUpperCase(Locale.ENGLISH),
                "TEACHER_CHAT",
                historyKey,
                normalizedQuestion,
                promptContext,
                220
        );

        ServiceResult<AiInsightResponse> result = aiInsightService.generateTeacherInsights(request);
        if (!result.isSuccess() || result.getData() == null) {
            return AppDataStore.ActionResult.failure(result.getMessage());
        }

        history.add("Teacher: " + normalizedQuestion);
        history.add("AI: " + result.getData().getSummaryText());
        keepRecentHistory(history);
        refreshAction.run();
        return AppDataStore.ActionResult.success("AI assistant replied for the current " + scopeLabel(scopeKey) + " view.");
    }

    public AppDataStore.ActionResult clear(int teacherId, String scopeKey) {
        conversationHistory.remove(buildConversationKey(teacherId, scopeKey));
        refreshAction.run();
        return AppDataStore.ActionResult.success("AI assistant conversation cleared.");
    }

    private List<String> buildAssistantContext(int teacherId, String scopeKey, List<String> history) {
        List<String> lines;
        if (scopeKey != null && scopeKey.startsWith("reports|")) {
            String subjectFilter = scopeKey.substring("reports|".length());
            lines = buildReportContext(teacherId, subjectFilter);
            lines.add("The teacher is asking about the current report.");
        } else if ("attendance".equalsIgnoreCase(scopeKey)) {
            lines = buildAttendanceContext(teacherId);
            lines.add("The teacher is asking about the current class.");
        } else {
            lines = buildDashboardContext(teacherId);
            lines.add("The teacher may ask about reports, attendance, schedules, and follow-up actions.");
        }

        if (!history.isEmpty()) {
            lines.add("Recent conversation:");
            int start = Math.max(0, history.size() - 6);
            for (int index = start; index < history.size(); index++) {
                lines.add(history.get(index));
            }
        }
        return lines;
    }

    private List<String> buildDashboardContext(int teacherId) {
        List<String> lines = new ArrayList<>();
        TeacherProfile teacher = dataProvider.findTeacher(teacherId);
        ScheduleSlot activeSlot = dataProvider.getActiveScheduleForTeacher(teacherId);
        ScheduleSlot nextSlot = dataProvider.getNextScheduleForTeacher(teacherId);

        lines.add("Teacher: " + (teacher == null ? "Teacher #" + teacherId : teacher.getFullName()));
        lines.add("Current subject: " + (activeSlot == null ? "No active class" : activeSlot.getSubjectName()));
        lines.add("Next subject: " + (nextSlot == null ? "No next class" : nextSlot.getSubjectName()));
        lines.add("Assigned students: " + dataProvider.getStudentCountForTeacher(teacherId));
        lines.add("Pending schedule requests: " + countPendingScheduleRequests(teacherId));
        lines.add("Pending student removal requests: " + dataProvider.getPendingStudentRemovalCountForTeacher(teacherId));
        appendAttendanceLines(lines, dataProvider.getRecentAttendanceRecords(8, teacherId));
        return lines;
    }

    private List<String> buildAttendanceContext(int teacherId) {
        List<String> lines = new ArrayList<>();
        TeacherProfile teacher = dataProvider.findTeacher(teacherId);
        AttendanceSession session = dataProvider.getSessionForTeacher(teacherId);
        List<AttendanceRecord> records = dataProvider.getRecentAttendanceRecords(12, teacherId);

        long manualCount = records.stream()
                .filter(record -> record.getSource() == AppDomain.AttendanceSource.MANUAL)
                .count();
        long lateCount = records.stream()
                .filter(record -> record.getStatus() == AppDomain.AttendanceStatus.LATE)
                .count();

        lines.add("Teacher: " + (teacher == null ? "Teacher #" + teacherId : teacher.getFullName()));
        lines.add("Class status: " + session.getState().getLabel());
        lines.add("Subject: " + session.getSubjectName());
        lines.add("Room: " + session.getRoom());
        lines.add("Temporary class: " + (session.isOverrideSession() ? "Yes" : "No"));
        lines.add("Recent attendance without QR: " + manualCount);
        lines.add("Late attendance entries in recent records: " + lateCount);
        appendAttendanceLines(lines, records);
        return lines;
    }

    private List<String> buildReportContext(int teacherId, String subjectFilter) {
        List<String> lines = new ArrayList<>();
        List<AttendanceRecord> records = getFilteredAttendanceRecords(teacherId, subjectFilter);
        long presentCount = records.stream()
                .filter(record -> record.getStatus() == AppDomain.AttendanceStatus.PRESENT)
                .count();
        long lateCount = records.stream()
                .filter(record -> record.getStatus() == AppDomain.AttendanceStatus.LATE)
                .count();
        long manualCount = records.stream()
                .filter(record -> record.getSource() == AppDomain.AttendanceSource.MANUAL)
                .count();

        lines.add("Report date: " + LocalDate.now().format(AppDomain.DATE_FORMAT));
        lines.add("Subject filter: " + normalizeSubjectFilter(subjectFilter));
        lines.add("Matching attendance records: " + records.size());
        lines.add("Present count: " + presentCount);
        lines.add("Late count: " + lateCount);
        lines.add("Attendance without QR: " + manualCount);
        appendAttendanceLines(lines, records.size() > 12 ? records.subList(0, 12) : records);
        return lines;
    }

    private List<AttendanceRecord> getFilteredAttendanceRecords(int teacherId, String subjectFilter) {
        List<AttendanceRecord> source = dataProvider.getAttendanceRecordsForTeacher(teacherId);
        String normalizedSubject = normalizeSubjectFilter(subjectFilter);
        if ("All Subjects".equalsIgnoreCase(normalizedSubject)) {
            return source;
        }

        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord record : source) {
            if (record.getSubjectName().equalsIgnoreCase(normalizedSubject)) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    private void appendAttendanceLines(List<String> lines, List<AttendanceRecord> records) {
        if (records.isEmpty()) {
            lines.add("Attendance activity: none recorded yet.");
            return;
        }

        lines.add("Recent attendance activity:");
        for (AttendanceRecord record : records) {
            lines.add(record.getStudentName() + " | "
                    + record.getSubjectName() + " | "
                    + record.getStatus().getLabel() + " | "
                    + record.getSource().getLabel() + " | "
                    + record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT));
        }
    }

    private int countPendingScheduleRequests(int teacherId) {
        int count = 0;
        for (ScheduleChangeRequest request : dataProvider.getScheduleRequestsForTeacher(teacherId)) {
            if (request.getStatus() == ScheduleRequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    private void keepRecentHistory(List<String> history) {
        while (history.size() > 12) {
            history.remove(0);
        }
    }

    private String normalizeSubjectFilter(String subjectFilter) {
        String cleaned = safe(subjectFilter);
        return cleaned.isBlank() ? "All Subjects" : cleaned;
    }

    private String buildConversationKey(int teacherId, String scopeKey) {
        return "teacher-" + teacherId + "|assistant|" + sanitizeKeySegment(scopeKey);
    }

    private String scopeLabel(String scopeKey) {
        if (scopeKey != null && scopeKey.startsWith("reports|")) {
            return "reports";
        }
        if ("attendance".equalsIgnoreCase(scopeKey)) {
            return "attendance";
        }
        return "dashboard";
    }

    private String sanitizeKeySegment(String value) {
        String cleaned = safe(value).toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-");
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32);
        }
        return cleaned.isBlank() ? "all" : cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
