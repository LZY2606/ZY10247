package com.splice.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 数据库为空时自动导入固定 fixture；清空后重启可重新导入复核。 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SpliceRepository repository;
    private final FixtureLoader fixtureLoader;

    public DataSeeder(SpliceRepository repository, FixtureLoader fixtureLoader) {
        this.repository = repository;
        this.fixtureLoader = fixtureLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.isEmpty()) {
            FixtureDocument doc = fixtureLoader.load();
            repository.reseed(doc);
            log.info("已从固定 fixture 导入 {} 个位点 (sha256={})", doc.loci.size(),
                    fixtureLoader.sha256());
        } else {
            log.info("数据库非空，跳过 fixture 导入 (fixture sha256={})", fixtureLoader.sha256());
        }
    }
}
