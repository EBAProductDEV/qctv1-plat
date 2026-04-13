package com.qctv1.iam.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {

    private long pageNum;

    private long pageSize;

    private long total;

    private List<T> list;

    public static <T> PageResponse<T> of(long pageNum, long pageSize, long total, List<T> list) {
        PageResponse<T> response = new PageResponse<>();
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setList(list);
        return response;
    }
}
