package com.knowledge.kb.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 分页查询基类
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class PageQuery {

    /** 页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 转换为 MyBatis-Plus 分页对象 */
    public <T> Page<T> toPage() {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        return new Page<>(no, size);
    }
}
