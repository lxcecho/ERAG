package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档信息实体
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("kb_document")
public class KbDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库ID */
    private Long kbId;

    /** 原始文件名 */
    private String originalName;

    /** 存储文件名 */
    private String storedName;

    /** 存储相对路径 */
    private String filePath;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件类型 pdf/doc/docx/md */
    private String fileType;

    /** 文件后缀(小写无点) */
    private String fileSuffix;

    /** MD5 校验值 */
    private String md5;

    /** 解析状态：0待解析 1解析中 2已解析 3解析失败 */
    private Integer status;

    /** 切片数量 */
    private Integer chunkCount;

    /** ES索引状态：0未索引 1已索引 2索引失败（混合检索双写状态跟踪） */
    private Integer esIndexed;

    /** 当前版本号（同文档多版本递增，知识治理） */
    private Integer version;

    /** 审核状态：PENDING/APPROVED/REJECTED（知识治理） */
    private String reviewStatus;

    /** 生命周期状态：DRAFT/REVIEW/PUBLISHED/ARCHIVED（知识治理·生命周期，顶层治理状态） */
    private String lifecycleStatus;

    /** 归档时间（ARCHIVED 时写入，RESTORE 清空；供保留期硬删除判定） */
    private LocalDateTime archivedAt;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 生效时间（NULL=立即生效，知识治理·有效期） */
    private LocalDateTime effectiveFrom;

    /** 过期时间（NULL=永久，知识治理·有效期） */
    private LocalDateTime expireAt;

    /** 质量评分 0-100（NULL=未评估，知识治理） */
    private Integer qualityScore;

    /** 可见性 P=公开 R=私有 T=保护级（控制 viewer 默认可见范围） */
    private String visibility;

    /** 是否继承 KB 角色权限 1=是 0=仅按 ACL（绝密文档关闭继承） */
    private Integer inheritKbPermission;

    /** 所属租户ID（与 kb_base.tenant_id 冗余便于直接过滤） */
    private Long tenantId;

    /** 上传人ID */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
