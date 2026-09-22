package com.splice.repo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 读取 classpath:fixtures/splice-fixture.json，内容视为固定输入并提供 SHA-256 复现指纹。 */
@Component
public class FixtureLoader {
    private static final String CLASSPATH = "fixtures/splice-fixture.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private String cachedJson;
    private String cachedSha;

    public synchronized String rawJson() {
        if (cachedJson == null) {
            try {
                cachedJson = new String(new ClassPathResource(CLASSPATH).getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("无法读取 fixture：" + CLASSPATH, e);
            }
        }
        return cachedJson;
    }

    /**
     * 允许从外部文件读取（清空库后重新导入复核时使用）。默认仍指向 classpath 固定 fixture。
     */
    public FixtureDocument load() {
        try {
            return mapper.readValue(rawJson(), FixtureDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("fixture 解析失败", e);
        }
    }

    public synchronized String sha256() {
        if (cachedSha == null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(rawJson().getBytes(StandardCharsets.UTF_8));
                cachedSha = HexFormat.of().formatHex(hash);
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 计算失败", e);
            }
        }
        return cachedSha;
    }

    /** 从显式文件加载（复核/重放用），不改变 classpath fixture。 */
    public FixtureDocument loadFromFile(Path path) {
        try {
            return mapper.readValue(Files.readString(path, StandardCharsets.UTF_8), FixtureDocument.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败：" + path, e);
        }
    }
}
