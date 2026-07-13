package net.niwa.kinsaku.discordsystem.api;

public enum SystemApiError {
    INVALID_REQUEST(400, "ERR_INVALID_REQUEST", "リクエストの形式が不正です。"),
    MISSING_FIELD(400, "ERR_MISSING_FIELD", "必要なフィールドが不足しています。"),
    INVALID_EDITION(400, "ERR_INVALID_EDITION", "エディションの指定が不正です。"),
    INVALID_MC_ID(400, "ERR_INVALID_MC_ID", "Minecraft IDの形式が正しくありません。"),
    UNIVERSITY_NOT_FOUND(400, "ERR_UNIVERSITY_NOT_FOUND", "登録されていない大学名です。"),
    PLAYER_DUPLICATE(409, "ERR_PLAYER_DUPLICATE", "既に登録されています（別のエディションなどで登録されている可能性があります）。"),
    MC_ID_DUPLICATE(409, "ERR_MC_ID_DUPLICATE", "このMinecraft IDは既に登録されています。"),
    UNIVERSITY_DUPLICATE(409, "ERR_UNIVERSITY_DUPLICATE", "既に同じ名前の大学が登録されています。"),
    MC_NOT_FOUND_JE(400, "ERR_MC_NOT_FOUND", "Minecraftアカウント(JE)が見つかりません。"),
    MC_NOT_FOUND_BE(400, "ERR_MC_NOT_FOUND", "Minecraftアカウント(BE)が見つかりません。"),
    MC_NOT_FOUND_GENERIC(400, "ERR_MC_NOT_FOUND", "Minecraftアカウントが見つかりません。"),
    UNAUTHORIZED(401, "ERR_UNAUTHORIZED", "APIキーが無効または設定されていません。"),
    DB_ERROR(500, "ERR_DB_ERROR", "データベース保存中にエラーが発生しました。"),
    INTERNAL_ERROR(500, "ERR_INTERNAL", "サーバーエラーが発生しました。");

    private final int statusCode;
    private final String code;
    private final String defaultMessage;

    SystemApiError(int statusCode, String code, String defaultMessage) {
        this.statusCode = statusCode;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String getFormattedMessage() {
        return "[" + code + "] " + defaultMessage;
    }

    public String getFormattedMessage(String detail) {
        return "[" + code + "] " + defaultMessage + " (" + detail + ")";
    }
}
