package com.knowledge.system.operlog.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 操作日志分页查询条件
 * <p>自带分页字段，不依赖 kb 模块的 PageQuery（system 处于 kb 上游）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class SysOperLogQuery {

    /** 页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 模块标题关键字 */
    private String title;

    /** 业务类型 */
    private Integer businessType;

    /** 操作用户 */
    private String operUser;

    /** 状态 0正常 1异常 */
    private Integer status;

    /** 转换为 MyBatis-Plus 分页对象 */
    public <T> Page<T> toPage() {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        return new Page<>(no, size);
    }
}
