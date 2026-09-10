package com.shanyangcode.common.model.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared pagination response structure
 * <p>
 * Response shape:
 * <pre>
 * {
 *   "list": [...],         // rows on the current page
 *   "total": 100,          // total number of records
 *   "pageSize": 20,        // page size
 *   "pageNum": 1,          // current page number
 *   "pages": 5,            // total number of pages
 *   "hasNext": true,       // whether a next page exists
 *   "hasPrevious": false   // whether a previous page exists
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rows on the current page
     */
    private List<T> list;

    /**
     * Total number of records
     */
    private Long total;

    /**
     * Page size
     */
    private Long pageSize;

    /**
     * Current page number
     */
    private Long pageNum;

    /**
     * Total number of pages
     */
    private Long pages;

    /**
     * Whether a next page exists
     */
    private Boolean hasNext;

    /**
     * Whether a previous page exists
     */
    private Boolean hasPrevious;

    /**
     * Converts from a MyBatis-Plus IPage
     *
     * @param page MyBatis-Plus pagination result
     * @param <T>  row type
     * @return the shared pagination response
     */
    public static <T> PageResponse<T> of(IPage<T> page) {
        return PageResponse.<T>builder()
                .list(page.getRecords())
                .total(page.getTotal())
                .pageSize(page.getSize())
                .pageNum(page.getCurrent())
                .pages(page.getPages())
                .hasNext(page.getCurrent() < page.getPages())
                .hasPrevious(page.getCurrent() > 1)
                .build();
    }

    /**
     * Converts from a MyBatis-Plus IPage, mapping each row
     *
     * @param page      MyBatis-Plus pagination result
     * @param converter row mapping function
     * @param <T>       source row type
     * @param <R>       target row type
     * @return the shared pagination response
     */
    public static <T, R> PageResponse<R> of(IPage<T> page, Function<T, R> converter) {
        List<R> convertedList = page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());

        return PageResponse.<R>builder()
                .list(convertedList)
                .total(page.getTotal())
                .pageSize(page.getSize())
                .pageNum(page.getCurrent())
                .pages(page.getPages())
                .hasNext(page.getCurrent() < page.getPages())
                .hasPrevious(page.getCurrent() > 1)
                .build();
    }

    /**
     * Builds a pagination response by hand (for in-memory paging)
     *
     * @param allData  the complete row list
     * @param pageNum  current page number
     * @param pageSize page size
     * @param <T>      row type
     * @return the shared pagination response
     */
    public static <T> PageResponse<T> of(List<T> allData, int pageNum, int pageSize) {
        if (allData == null || allData.isEmpty()) {
            return PageResponse.<T>builder()
                    .list(Collections.emptyList())
                    .total(0L)
                    .pageSize((long) pageSize)
                    .pageNum((long) pageNum)
                    .pages(0L)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build();
        }

        long total = allData.size();
        long pages = (total + pageSize - 1) / pageSize;

        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allData.size());

        List<T> list = fromIndex < allData.size()
                ? allData.subList(fromIndex, toIndex)
                : Collections.emptyList();

        return PageResponse.<T>builder()
                .list(list)
                .total(total)
                .pageSize((long) pageSize)
                .pageNum((long) pageNum)
                .pages(pages)
                .hasNext(pageNum < pages)
                .hasPrevious(pageNum > 1)
                .build();
    }

    /**
     * Builds an empty pagination response
     *
     * @param pageNum  current page number
     * @param pageSize page size
     * @param <T>      row type
     * @return an empty pagination response
     */
    public static <T> PageResponse<T> empty(int pageNum, int pageSize) {
        return PageResponse.<T>builder()
                .list(Collections.emptyList())
                .total(0L)
                .pageSize((long) pageSize)
                .pageNum((long) pageNum)
                .pages(0L)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }
}