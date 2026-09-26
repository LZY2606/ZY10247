package com.example.splice.config;

import com.example.splice.repo.Schema;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 幂等建表（清空数据库文件后重启可自动重建），先于数据播种执行。 */
@Component
@Order(0)
public class DbInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public DbInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String ddl : Schema.DDL) {
            jdbc.execute(ddl);
        }
        jdbc.execute("PRAGMA foreign_keys=ON");
    }
}
