package com.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类
 * <p>包路径为 {@code com.knowledge}，{@link SpringBootApplication} 默认扫描
 * 该包及其子包，因此 common / infra / 各业务模块的 Bean 均可被自动装配。
 * <p>{@link MapperScan} 统一扫描所有模块下的 mapper 接口。
 * <p>{@link EnableScheduling} 开启定时任务支持（知识治理过期扫描等）。
 *
 * @author: lxcechoo@gmail.com
 */
@SpringBootApplication
@MapperScan("com.knowledge.**.mapper")
@EnableScheduling
public class KnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
        System.out.println("""

                ╔══════════════════════════════════════════════╗
                ║  Knowledge AI Assistant 启动成功 🚀           ║
                ║  接口文档: http://localhost:8080/api/doc.html ║
                ╚══════════════════════════════════════════════╝
                """);
    }
}
