package com.smartlock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> list;
    private long total;
    private int pageNo;
    private int pageSize;

    public static <T> PageResponse<T> of(List<T> list, long total, int pageNo, int pageSize) {
        return new PageResponse<>(list, total, pageNo, pageSize);
    }
}
