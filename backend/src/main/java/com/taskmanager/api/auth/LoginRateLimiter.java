package com.taskmanager.api.auth;

import com.taskmanager.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同じメールアドレスへのログインを、WINDOW の間に MAX_ATTEMPTS 回までにする(パスワードの総当たり対策)。
 * 成功・失敗どちらも1回と数える(収支管理アプリの rate_limit と同じ考え方)。
 * 記録はこのサーバーのメモリだけに持つ(サーバーは1台なので十分。再起動すると数え直し)
 */
@Component
public class LoginRateLimiter {

    public static final int MAX_ATTEMPTS = 10;
    public static final Duration WINDOW = Duration.ofMinutes(5);
    /** 記録するメールアドレスがこの数を超えたら、古い記録を掃除する(メモリを使い続けないように) */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    /** 1回分を数える。上限を超えていたら 429 を投げる(超えた回は数えない) */
    public void check(String email) {
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW);
        if (attempts.size() > CLEANUP_THRESHOLD) {
            cleanUp(since);
        }
        Deque<Instant> times = attempts.computeIfAbsent(email, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(since)) {
                times.pollFirst();
            }
            if (times.size() >= MAX_ATTEMPTS) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "ログインの試行回数が多すぎます。5分ほど待ってからもう一度お試しください");
            }
            times.addLast(now);
        }
    }

    private void cleanUp(Instant since) {
        attempts.entrySet().removeIf(entry -> {
            Deque<Instant> times = entry.getValue();
            synchronized (times) {
                return times.isEmpty() || !times.peekLast().isAfter(since);
            }
        });
    }

    /** テストで時間を進めるため */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** テストどうしで記録が混ざらないように消す */
    void reset() {
        attempts.clear();
        clock = Clock.systemUTC();
    }
}
