package ppb.qrattend.service;

public final class ServiceResult<T> {

    private final boolean success;
    private final boolean warning;
    private final String message;
    private final T data;
    private final String nextStepComment;

    private ServiceResult(boolean success, boolean warning, String message, T data, String nextStepComment) {
        this.success = success;
        this.warning = warning;
        this.message = message;
        this.data = data;
        this.nextStepComment = nextStepComment;
    }

    public static <T> ServiceResult<T> success(String message, T data) {
        return new ServiceResult<>(true, false, message, data, "");
    }

    public static <T> ServiceResult<T> warning(String message, T data) {
        return new ServiceResult<>(false, true, message, data, "");
    }

    public static <T> ServiceResult<T> failure(String message) {
        return new ServiceResult<>(false, false, message, null, "");
    }

    public static <T> ServiceResult<T> notImplemented(String message, String nextStepComment) {
        return new ServiceResult<>(false, false, message, null, nextStepComment);
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

    public T getData() {
        return data;
    }

    public String getNextStepComment() {
        return nextStepComment;
    }
}
