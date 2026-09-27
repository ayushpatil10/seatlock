package com.seatlock.backend.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SeatLockService {

    private final RedisTemplate<String, String> redisTemplate;

    public SeatLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean lockSeat(Integer seatId) {

        String key = "seat:lock:" + seatId;

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, "LOCKED", 10, TimeUnit.MINUTES);

        return Boolean.TRUE.equals(locked);
    }
}
