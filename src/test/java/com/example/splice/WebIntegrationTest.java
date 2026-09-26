package com.example.splice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:./data/test-splice-bench.db")
@AutoConfigureMockMvc
class WebIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void indexPageShowsConsoleTitle() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("剪接路径裁决台")));
    }

    @Test
    void graphApiExposesHalfOpenCoordsAndCells() throws Exception {
        mvc.perform(get("/api/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storedStrand").value("-"))
                .andExpect(jsonPath("$.nodes[0].start").value(4400))
                .andExpect(jsonPath("$.paths.length()").value(4))
                .andExpect(jsonPath("$.cells.j1.length()").value(6));
    }

    @Test
    void pooledRunKeepsTwoTiedDecompositions() throws Exception {
        String body = "{\"strand\":\"-\",\"note\":\"integration-pooled\"}";
        mvc.perform(post("/api/runs").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[0].scope").value("POOLED"))
                .andExpect(jsonPath("$.scopes[0].tied").value(true))
                .andExpect(jsonPath("$.scopes[0].solutions.length()").value(2))
                .andExpect(jsonPath("$.scopes[1].tied").value(true))
                .andExpect(jsonPath("$.scopes[2].tied").value(false));
    }

    @Test
    void excludedEdgeAndLockChangeResult() throws Exception {
        String locked = "{\"strand\":\"-\",\"excludeEdges\":[\"j3\"],\"locks\":{\"P2\":\"110\"},\"note\":\"locked\"}";
        mvc.perform(post("/api/runs").contentType("application/json").content(locked))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[0].tied").value(false))
                .andExpect(jsonPath("$.scopes[0].solutions[0].weights[?(@.pathId=='P2')].exact")
                        .value(org.hamcrest.Matchers.hasItem("110")))
                .andExpect(jsonPath("$.scopes[0].edges[2].excluded").value(true));
    }

    private long postRun(String body) throws Exception {
        String json = mvc.perform(post("/api/runs").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("id").asLong();
    }

    @Test
    void fullLifecycleReimportDiffExport() throws Exception {
        long idA = postRun("{\"strand\":\"-\",\"note\":\"a\"}");
        long idB = postRun("{\"strand\":\"+\",\"note\":\"b\"}");

        mvc.perform(get("/api/runs/" + idA + "/diff/" + idB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingsChanged").value(true));

        mvc.perform(get("/api/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":" + idA)))
                .andExpect(content().contentType("application/x-ndjson"));

        mvc.perform(post("/api/admin/reimport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reimported").value(true))
                .andExpect(jsonPath("$.exons").value(7))
                .andExpect(jsonPath("$.junctions").value(8));

        // 默认重导入会一并清空运行历史，便于从干净状态复核
        mvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // keepRuns=true 时仅重建业务表并保留运行记录
        mvc.perform(post("/api/runs").contentType("application/json").content("{\"note\":\"keep\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/reimport").param("keepRuns", "true"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
