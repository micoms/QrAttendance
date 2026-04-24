package ppb.qrattend.app.store;

import java.util.Locale;

public final class StoreMessages {

    private StoreMessages() {
    }

    public static String clean(String message) {
        if (message == null) {
            return "";
        }

        String text = message.trim();
        if (text.isBlank()) {
            return "";
        }

        String lower = text.toLowerCase(Locale.ENGLISH);
        if (looksLikeAiSetupIssue(lower)) {
            return "Ask AI is not ready right now.";
        }
        if (looksLikeSetupIssue(lower)) {
            return "This part of the app is not ready right now. Please ask the admin.";
        }
        if (looksLikeEmailIssue(lower)) {
            return "The email could not be sent right now.";
        }

        return switch (text) {
            case "Teacher account is required." -> "Choose a teacher first.";
            case "Type a question for the AI assistant." -> "Type your question first.";
            case "AI assistant conversation cleared." -> "AI chat cleared.";
            case "Teacher name and email are required." -> "Enter the teacher's name and email.";
            case "Enter a valid teacher email address." -> "Enter a valid teacher email.";
            case "That email is already reserved for the admin account." -> "That email is already being used.";
            case "A teacher already uses that email address." -> "That email is already being used.";
            case "Select a teacher first." -> "Choose a teacher first.";
            case "Admin and assigned teacher are required." -> "Choose a teacher first.";
            case "Section, student ID, full name, and email are required." -> "Complete the section, student ID, name, and email.";
            case "Enter a valid student email address." -> "Enter a valid student email.";
            case "That student ID already exists." -> "That student ID is already in the list.";
            case "That student email is already registered." -> "That student email is already in the list.";
            case "Student records are now managed by admin by section." -> "Only the admin can add students.";
            case "Select a student first." -> "Choose a student first.";
            case "Select a student from your roster first." -> "Choose a student from your class list first.";
            case "Add a reason for the removal request." -> "Enter a reason first.";
            case "There is already a pending removal request for this student." -> "There is already a request waiting for this student.";
            case "Removal request sent to admin for approval." -> "Your request was sent to the admin.";
            case "Select a student removal request first." -> "Choose a student request first.";
            case "Only pending removal requests can be reviewed." -> "This request was already reviewed.";
            case "Student removal approved." -> "Student was removed from the class list.";
            case "Student removal rejected." -> "Student removal request was not approved.";
            case "Select a teacher for the schedule." -> "Choose a teacher first.";
            case "Teacher, subject, room, day, and time are required." -> "Complete the teacher, subject, room, day, and time.";
            case "The class end time must be after the start time." -> "End time must be after start time.";
            case "Select one of your schedule rows first." -> "Choose one of your classes first.";
            case "Complete the requested subject, room, day, time, and reason." -> "Complete the subject, room, day, time, and reason.";
            case "The requested end time must be after the start time." -> "End time must be after start time.";
            case "Schedule correction submitted for admin approval." -> "Your schedule change was sent to the admin.";
            case "Select a request first." -> "Choose a request first.";
            case "Only pending requests can be reviewed." -> "This request was already reviewed.";
            case "Schedule request approved." -> "Request approved.";
            case "Schedule request rejected." -> "Request rejected.";
            case "Override subject and reason are required." -> "Enter the subject and reason first.";
            case "No override session is currently open." -> "There is no temporary class open right now.";
            case "Override session closed." -> "Temporary class closed.";
            case "No scheduled class is active. Open an override session to scan." -> "There is no class open right now. Open a temporary class first.";
            case "No student matched that QR token." -> "We couldn't find a student for that QR code.";
            case "No scheduled class is active. Open an override session first." -> "There is no class open right now. Open a temporary class first.";
            case "Select a student from the class list." -> "Choose a student from the class list.";
            default -> cleanStartsWith(text);
        };
    }

    private static boolean looksLikeAiSetupIssue(String lower) {
        return lower.contains("ai unavailable")
                || lower.contains("ai insights are disabled")
                || lower.contains("gemini")
                || lower.contains("api key")
                || lower.contains("no cached ai insight");
    }

    private static boolean looksLikeSetupIssue(String lower) {
        return lower.contains("database login")
                || lower.contains("mariadb")
                || lower.contains("mysql")
                || lower.contains("jdbc")
                || lower.contains("config/database.properties")
                || lower.contains("db.url")
                || lower.contains("db.username")
                || lower.contains("driver not found");
    }

    private static boolean looksLikeEmailIssue(String lower) {
        return lower.contains("rejected the email")
                || lower.contains("could not reach resend")
                || lower.contains("email sending was interrupted")
                || lower.contains("api key is invalid");
    }

    private static String cleanStartsWith(String text) {
        if (text.startsWith("Teacher account created and password email sent to ")) {
            return text.replace("Teacher account created and password email sent to ", "Teacher added and the password email was sent to ");
        }
        if (text.startsWith("Password reset email sent to ")) {
            return text.replace("Password reset email sent to ", "Password reset and sent to ");
        }
        if (text.startsWith("A new temporary password was sent to ")) {
            return text.replace("A new temporary password was sent to ", "Password email sent again to ");
        }
        if (text.startsWith("Student added to ") && text.endsWith(" and QR email sent.")) {
            return text.replace(" and QR email sent.", " and the QR code was sent.");
        }
        if (text.startsWith("QR email re-sent to ")) {
            return text.replace("QR email re-sent to ", "QR code sent again to ");
        }
        if (text.startsWith("Conflict with ")) {
            return text.replace("Conflict with ", "This time overlaps with ");
        }
        if (text.startsWith("Schedule saved for ")) {
            return text.replace("Schedule saved for ", "Class saved for ");
        }
        if (text.startsWith("Override attendance session opened for ")) {
            return text.replace("Override attendance session opened for ", "Temporary class opened for ");
        }
        if (text.contains(" is already marked for this session.")) {
            return text.replace(" for this session.", " for this class.");
        }
        if (text.contains(" marked present via QR.")) {
            return text.replace(" marked present via QR.", " was marked present.");
        }
        if (text.contains(" marked late via QR.")) {
            return text.replace(" marked late via QR.", " was marked late.");
        }
        if (text.contains(" marked absent via QR.")) {
            return text.replace(" marked absent via QR.", " was marked absent.");
        }
        if (text.contains(" marked without QR.")) {
            return text.replace(" marked without QR.", " was marked without QR.");
        }
        if (text.startsWith("Loaded ")) {
            return "";
        }
        return text;
    }
}
