package com.example.splice.fixture;

import com.example.splice.repo.GeneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 数据库为空时导入固定 fixture；清空数据库后重启即可复核。 */
@Component
@Order(20)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final GeneRepository genes;

    public DataSeeder(GeneRepository genes) {
        this.genes = genes;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (genes.isEmpty()) {
            genes.replaceAll(FixtureData.gene());
            log.info("固定 fixture 已导入: {}", FixtureData.GENE_ID);
        }
    }
}
