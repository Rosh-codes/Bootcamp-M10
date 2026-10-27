package com.accenture.externalapis.demo.dto;

// Raw response from GET /api/books/{id}.
public record BookApiResponse(
        Long id,
        String title,
        String author,
        String genre,
        double price,
        String isbn,
        Integer publishedYear) {
}
