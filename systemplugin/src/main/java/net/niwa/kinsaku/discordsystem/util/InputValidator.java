package net.niwa.kinsaku.discordsystem.util;

import java.util.regex.Pattern;

public class InputValidator {

    private static final Pattern JAVA_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    // Bedrock(統合版)はスペースを含めることができる
    private static final Pattern BEDROCK_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_ ]{3,16}$");
    private static final Pattern UNIVERSITY_NAME_PATTERN = Pattern.compile("^.{2,100}$");

    /**
     * Minecraft IDの形式が正しいかチェックします。
     * 
     * @param id チェックするID
     * @param edition JAVA または BEDROCK
     * @return 正しい形式であれば true
     */
    public static boolean validateMinecraftId(String id, String edition) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        
        if ("JAVA".equalsIgnoreCase(edition)) {
            return JAVA_ID_PATTERN.matcher(id).matches();
        } else if ("BEDROCK".equalsIgnoreCase(edition)) {
            return BEDROCK_ID_PATTERN.matcher(id).matches();
        }
        return false;
    }

    /**
     * 大学名の形式が正しいかチェックします（2〜100文字、すべての文字を許容）。
     */
    public static boolean validateUniversityName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return UNIVERSITY_NAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * 文字列からコマンドやクエリに悪影響を与える危険文字を除去またはサニタイズします。
     * （PreparedStatementによりSQLインジェクションは防止されるため、制御文字以外のすべての文字種を許容）
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // コントロール文字のみ削除し、引用符や記号などの全文字種は許容する
        return input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
    }
}
