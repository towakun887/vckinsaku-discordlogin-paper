# Kinsaku Discord System

Minecraft サーバー（PaperMC / Folia）と Discord Bot を統合し、Discord からホワイトリスト追加やアカウントの紐付け・管理を行うためのシステムです。

## システムアーキテクチャ

本システムは以下の2つのモジュールで構成されています。
1. **systemplugin**: Minecraft サーバーにインストールするプラグイン（REST API サーバー内蔵）
2. **systembot**: Discord サーバーで動作する Bot アプリケーション

両者はローカルの HTTP 通信（デフォルトポート: `25580`）でステートレスに連携します。

---

## セットアップ手順

### 1. ビルド
Java 21 および Maven がインストールされている環境で、プロジェクトのルートディレクトリにて以下を実行します。

```bash
mvn clean package
```

ビルドが完了すると、以下の場所に JAR ファイルが生成されます。
- **Plugin:** `systemplugin/target/KinsakuDiscordSystem-1.0-SNAPSHOT.jar`
- **Bot:** `systembot/target/systembot-1.0-SNAPSHOT-shaded.jar`

### 2. Plugin のセットアップ
1. 生成された `KinsakuDiscordSystem-1.0-SNAPSHOT-shaded.jar` を Minecraft サーバーの `plugins` フォルダに配置します。
2. サーバーを起動すると、`plugins/KinsakuDiscordSystem/config.yml` が生成されます。
3. `config.yml` を開き、必要に応じて設定を変更します。
   - `api.key`: Bot との通信で認証に使用する共有 API キーを設定します。
   - `crossplay`: Bedrock Edition (統合版) とクロスプレイを行っている場合の設定です。

### 3. Bot のセットアップ
1. プロジェクトルートにある `.env.example` をコピーして `.env` ファイルを作成します。
2. `.env` ファイル内の項目を設定します。
   - `DISCORD_BOT_TOKEN`: Discord Developer Portal から取得した Bot のトークン。
   - `DISCORD_GUILD_ID`: コマンドを即座に登録・反映する Discord サーバーの ID。
   - `ADMIN_ROLE_ID`: `/server-admin` および `/server-admin-delete` などの管理コマンドを実行可能にする Discord ロール ID。
   - `PLUGIN_API_KEY`: 上記 Plugin 側の `config.yml` に設定した `api.key` と同一の文字列。
   - `BOT_TEST_MODE`: `true` に設定すると、Minecraft サーバー（Plugin API）が動作していなくても、Bot単体ですべての機能（申請、自己削除、管理者メニュー、統計、リアクションUIなど）をモックデータを用いてテスト可能です（デフォルト: `false`）。

### 4. 起動
1. Minecraft サーバーを起動します（API サーバーが自動起動します）。
2. Bot を以下のコマンドで起動します。

```bash
java -jar systembot/target/systembot-1.0-SNAPSHOT-shaded.jar
```

---

## 提供コマンド

### Discord スラッシュコマンド
- `/server-support`: サーバー案内 Embed を表示します。ホワイトリスト追加および自己削除のコマンド案内が配置されています。
- `/server-whitelist-add`: Minecraft アカウントをホワイトリストに追加します（大学、エディション、Minecraft ID を指定）。
- `/server-whitelist-delete`: 自分の登録した Minecraft アカウントを選択してホワイトリストから解除します。
- `/server-admin`: 管理者メニュー（プルダウン型）を表示します（ロール制限あり）。
- `/server-admin-delete`: 管理者用のホワイトリスト解除コマンド。Minecraft ID もしくは Discord メンションを指定して解除します。

### Minecraft ゲーム内コマンド（OP権限）
- `/kinsaku-usersystem reload`: 設定ファイルおよび大学キャッシュのリロードを行います。
- `/kinsaku-usersystem status`: API サーバーの動作ポートや接続状態を表示します。
