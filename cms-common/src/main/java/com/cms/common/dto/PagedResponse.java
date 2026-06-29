package com.cms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response wrapper for list endpoints.
 *
 * <p>Used as the {@code data} payload inside {@link ApiResponse} for any
 * endpoint that returns a collection with pagination support.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "items":      [ {...}, {...} ],
 *     "page":       1,
 *     "size":       20,
 *     "total":      150,
 *     "totalPages": 8
 *   }
 * }
 * }</pre>
 *
 * @param <T> the type of items in the list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    /** The current page of results. */
    private List<T> items;

    /** Current page number (1-indexed). */
    private int page;

    /** Number of items per page. */
    private int size;

    /** Total number of items across all pages. */
    private long total;

    /** Total number of pages. */
    private int totalPages;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        return PagedResponse.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .totalPages(totalPages)
                .build();
    }
}
