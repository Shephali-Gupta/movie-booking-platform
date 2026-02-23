
package com.movie.booking.locking;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class SeatLockManager {

    private final Map<String, String> locks = new ConcurrentHashMap<>();

    public boolean lockSeat(String key, String user) {
        return locks.putIfAbsent(key, user) == null;
    }

    public void releaseSeat(String key) {
        locks.remove(key);
    }
}
