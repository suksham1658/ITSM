package com.nbfc.itsm.security;

import com.nbfc.itsm.util.TimeUtc;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing through the portal: after {@link #MAX_PER_USER} wrong passwords for one
 * username, or {@link #MAX_PER_IP} from one computer, within {@link #WINDOW}, further sign-ins are refused for
 * {@link #BLOCK} without contacting Active Directory. That also stops the portal from being used to lock users
 * out of AD (every wrong password counts towards the AD lockout policy).
 * Kept in memory (cleared on restart), bounded in size.
 */
@Component
public class LoginAttemptService {

    static final int MAX_PER_USER = 5;
    static final int MAX_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration BLOCK = Duration.ofMinutes(15);
    private static final int MAX_ENTRIES = 50_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<String, Attempts>();

    /** True when this username or address is currently blocked. */
    public boolean isBlocked(String username, String ip) {
        Instant now = TimeUtc.now();
        return blocked(userKey(username), now) || blocked(ipKey(ip), now);
    }

    public void failed(String username, String ip) {
        Instant now = TimeUtc.now();
        purgeIfLarge(now);
        count(userKey(username), MAX_PER_USER, now);
        count(ipKey(ip), MAX_PER_IP, now);
    }

    /** A correct password clears the username's count (the address count still expires on its own). */
    public void succeeded(String username) {
        String key = userKey(username);
        if (key != null) {
            attempts.remove(key);
        }
    }

    private boolean blocked(String key, Instant now) {
        if (key == null) {
            return false;
        }
        Attempts a = attempts.get(key);
        return a != null && a.blockedUntil != null && now.isBefore(a.blockedUntil);
    }

    private void count(String key, int max, Instant now) {
        if (key == null) {
            return;
        }
        attempts.compute(key, (k, a) -> {
            if (a == null || now.isAfter(a.windowStart.plus(WINDOW))) {
                a = new Attempts(now);
            }
            a.failures++;
            if (a.failures >= max) {
                a.blockedUntil = now.plus(BLOCK);
            }
            return a;
        });
    }

    private void purgeIfLarge(Instant now) {
        if (attempts.size() < MAX_ENTRIES) {
            return;
        }
        for (Iterator<Map.Entry<String, Attempts>> it = attempts.entrySet().iterator(); it.hasNext(); ) {
            Attempts a = it.next().getValue();
            boolean windowOver = now.isAfter(a.windowStart.plus(WINDOW));
            boolean blockOver = a.blockedUntil == null || now.isAfter(a.blockedUntil);
            if (windowOver && blockOver) {
                it.remove();
            }
        }
        if (attempts.size() >= MAX_ENTRIES) {
            attempts.clear(); // flood of random names: start over rather than grow without limit
        }
    }

    /** "CORP\\User" and "user" are the same account. */
    private static String userKey(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        String u = username.trim().toLowerCase(Locale.ROOT);
        int slash = u.lastIndexOf('\\');
        if (slash >= 0) {
            u = u.substring(slash + 1);
        }
        int at = u.indexOf('@');
        if (at > 0) {
            u = u.substring(0, at);
        }
        return "u:" + (u.length() > 128 ? u.substring(0, 128) : u);
    }

    private static String ipKey(String ip) {
        return ip == null || ip.isEmpty() ? null : "ip:" + ip;
    }

    private static final class Attempts {
        final Instant windowStart;
        int failures;
        Instant blockedUntil;

        Attempts(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
