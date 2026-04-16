package com.example.batch.entity;

import java.time.LocalDateTime;

public record Customer(Long id, String name, boolean processed, LocalDateTime updatedAt) {
}
