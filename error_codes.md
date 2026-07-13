# Discord Login System Error Codes

DiscordBotとPlugin間のAPI連携で発生するエラーメッセージには、以下の独自フォーマットのエラーコードがプレフィックスとして付与されます。
DiscordBotはこれらのエラーコードの `startsWith` で分岐を行い、ユーザーの入力ミス（メンション不要）かシステムエラー（メンション必要）かを判断します。

| エラーコード | 概要 | HTTPステータス |
| :--- | :--- | :--- |
| `[ERR_INVALID_REQUEST]` | リクエストの形式が不正（JSONボディが空など） | 400 Bad Request |
| `[ERR_MISSING_FIELD]` | 必要なフィールドが不足している | 400 Bad Request |
| `[ERR_INVALID_EDITION]` | エディション（JAVA / BEDROCK）の指定が不正 | 400 Bad Request |
| `[ERR_INVALID_MC_ID]` | Minecraft IDの形式が不正 | 400 Bad Request |
| `[ERR_UNIVERSITY_NOT_FOUND]` | 指定された大学が登録されていない | 400 Bad Request |
| `[ERR_PLAYER_DUPLICATE]` | すでにMinecraft IDがデータベースに登録されている | 409 Conflict |
| `[ERR_UNIVERSITY_DUPLICATE]` | すでに同じ名前の大学が登録されている | 409 Conflict |
| `[ERR_MC_NOT_FOUND]` | MinecraftアカウントがMojangやGeyser等で見つからない | 400 Bad Request |
| `[ERR_DB_ERROR]` | データベース保存や処理中にエラーが発生した | 500 / 400 |
| `[ERR_INTERNAL]` | その他の予期せぬ内部システムエラー | 500 Internal Server Error |
