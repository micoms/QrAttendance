package ppb.qrattend.service;

public final class ServiceResult<T> {

    private final boolean success;
    private final boolean warning;
    private final String message;
    private final T data;
    private final String nextStepComment;

    private ServiceResult(boolean success, boolean warning, String message, T data, String nextStepComment) {
        // Mini-code guide:
        // 1. Capture the final outcome shape in one immutable object.
        // 2. success controls whether callers should trust data.
        // 3. warning allows partial-success states like "saved in DB, but email delivery failed".
        // 4. message is the UI-facing explanation.
        // 5. nextStepComment is reserved for stubbed methods so developers know what to build next.
        this.success = success;
        this.warning = warning;
        this.message = message;
        this.data = data;
        this.nextStepComment = nextStepComment;
    }

    public static <T> ServiceResult<T> success(String message, T data) {
        // Mini-code guide:
        // 1. Use this factory when the requested operation completed successfully.
        // 2. Attach the mapped DTO/entity in data when available.
        return new ServiceResult<>(true, false, message, data, "");
    }

    public static <T> ServiceResult<T> warning(String message, T data) {
        // Mini-code guide:
        // 1. Use this factory when the main DB action completed but a secondary step failed.
        // 2. Keep the data payload so callers can still refresh the UI from the committed state.
        return new ServiceResult<>(false, true, message, data, "");
    }

    public static <T> ServiceResult<T> failure(String message) {
        // Mini-code guide:
        // 1. Use this factory when validation, DB access, or API access fails.
        // 2. Keep data null so callers do not accidentally use partial results.
        return new ServiceResult<>(false, false, message, null, "");
    }

    public static <T> ServiceResult<T> notImplemented(String message, String nextStepComment) {
        // Mini-code guide:
        // 1. Use this factory while methods are still design skeletons.
        // 2. message explains what is missing now.
        // 3. nextStepComment tells the next developer what logic to add first.
        return new ServiceResult<>(false, false, message, null, nextStepComment);
    }

    public boolean isSuccess() {
        // Mini-code guide:
        // 1. Return whether the operation completed successfully.
        // 2. UI/store callers should check this before reading data.
        return success;
    }

    public boolean isWarning() {
        // Mini-code guide:
        // 1. Return whether the main action succeeded but needs user attention.
        return warning;
    }

    public String getMessage() {
        // Mini-code guide:
        // 1. Return the user/developer-facing explanation of the result.
        return message;
    }

    public T getData() {
        // Mini-code guide:
        // 1. Return the payload mapped by the service, or null when the operation failed/stubbed.
        return data;
    }

    public String getNextStepComment() {
        // Mini-code guide:
        // 1. Return the placeholder implementation note for unfinished methods.
        return nextStepComment;
    }
}
