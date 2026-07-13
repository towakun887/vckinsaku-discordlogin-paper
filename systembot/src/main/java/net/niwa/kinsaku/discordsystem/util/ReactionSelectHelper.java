package net.niwa.kinsaku.discordsystem.util;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReactionSelectHelper {

    private static final String[] NUM_EMOJIS = {"1\uFE0F\u20E3", "2\uFE0F\u20E3", "3\uFE0F\u20E3", "4\uFE0F\u20E3", "5\uFE0F\u20E3", "6\uFE0F\u20E3", "7\uFE0F\u20E3", "8\uFE0F\u20E3", "9\uFE0F\u20E3"};
    private static final String ALL_EMOJI = "\uD83C\uDD70\uFE0F"; // 🅰️
    private static final String CONFIRM_EMOJI = "\u2705"; // ✅

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * リアクションによる複数選択を非同期に処理します。
     *
     * @param message 選択肢を表示したメッセージ
     * @param targetUser 操作を行う対象ユーザー (横入り防止用)
     * @param optionCount 選択肢の数 (1-9)
     * @param showAllOption 🅰️ (全選択) オプションを表示するかどうか
     * @return 選択されたインデックス（0から始まる）のセット。タイムアウト時は空のセット。
     */
    public CompletableFuture<Set<Integer>> select(Message message, User targetUser, int optionCount, boolean showAllOption) {
        CompletableFuture<Set<Integer>> future = new CompletableFuture<>();
        int count = Math.min(Math.max(optionCount, 1), 9);
        Set<Integer> selectedIndices = new HashSet<>();

        // リアクションをメッセージに追加
        for (int i = 0; i < count; i++) {
            message.addReaction(Emoji.fromFormatted(NUM_EMOJIS[i])).queue();
        }
        if (showAllOption) {
            message.addReaction(Emoji.fromFormatted(ALL_EMOJI)).queue();
        }
        message.addReaction(Emoji.fromFormatted(CONFIRM_EMOJI)).queue();

        // リアクションを監視する一時的なリスナーを作成
        ListenerAdapter listener = new ListenerAdapter() {
            @Override
            public void onMessageReactionAdd(MessageReactionAddEvent event) {
                if (!event.getMessageId().equals(message.getId())) {
                    return;
                }
                
                // Bot自身のリアクションは無視
                if (event.getUser().isBot()) {
                    return;
                }

                // 操作対象ユーザー以外によるリアクションは横入り防止として除去
                if (!event.getUserId().equals(targetUser.getId())) {
                    event.getReaction().removeReaction(event.getUser()).queue();
                    return;
                }

                String emojiName = event.getEmoji().getName();

                if (CONFIRM_EMOJI.equals(emojiName)) {
                    // 確定
                    event.getJDA().removeEventListener(this);
                    future.complete(selectedIndices);
                } else if (ALL_EMOJI.equals(emojiName) && showAllOption) {
                    // 全選択
                    for (int i = 0; i < count; i++) {
                        selectedIndices.add(i);
                    }
                    // 全選択リアクションが追加されたら、確定前だが全て選択済みとして扱う
                } else {
                    // 数字リアクションの判定
                    for (int i = 0; i < count; i++) {
                        if (NUM_EMOJIS[i].equals(emojiName)) {
                            selectedIndices.add(i);
                            break;
                        }
                    }
                }
            }
        };

        // イベントリスナーの登録
        message.getJDA().addEventListener(listener);

        // タイムアウト設定 (コマンド更新期限15分の20秒前 = 880秒)
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                message.getJDA().removeEventListener(listener);
                // タイムアウト時は空のセットで完了
                future.complete(new HashSet<>());
                // リアクションをクリア
                try {
                    message.clearReactions().queue();
                } catch (Exception ignored) {}
            }
        }, 880, TimeUnit.SECONDS);

        // futureが完了したときにスケジューラをクリーンアップ
        future.whenComplete((res, ex) -> {
            message.getJDA().removeEventListener(listener);
            scheduler.shutdown();
        });

        return future;
    }
}
