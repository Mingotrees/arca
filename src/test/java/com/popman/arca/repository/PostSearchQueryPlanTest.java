package com.popman.arca.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.dto.v1.post.PostSearchCriteria;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostSearchQueryPlanTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @Autowired PostSearchRepository repository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @BeforeAll
    void seedRepresentativeVolume() {
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(tx -> seedCorpus());
        jdbc.execute("ANALYZE TABLE posts,post_tags,votes");
    }

    private void seedCorpus() {
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities", "user_roles",
                "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'Synthetic school')");
        for (int id = 1; id <= 100; id++) {
            jdbc.update("INSERT INTO users(id,email,first_name,is_deleted) VALUES(?,?,?,false)", id, "fixture" + id + "@example.test", "Author " + id);
            jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ROLE_USER')", id);
            if (id <= 10) jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(?,?,1)", id, "Department " + id);
            jdbc.update("INSERT INTO subjects(id,code,name) VALUES(?,?,?)", id, "S" + id, "Subject " + id);
            jdbc.update("INSERT INTO department_subjects(subject_id,department_id) VALUES(?,?)", id, (id - 1) % 10 + 1);
        }
        List<Object[]> identities = new ArrayList<>();
        List<Object[]> posts = new ArrayList<>();
        List<Object[]> tags = new ArrayList<>();
        List<Object[]> votes = new ArrayList<>();
        List<Object[]> files = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            long id = i + 1000;
            int author = i % 100 + 1;
            identities.add(new Object[]{id, author});
            posts.add(new Object[]{id, id, "Corpus reference", i % 200 == 1 ? "quasar orbital mechanics" : "matrix vector algebra background",
                    i % 5 == 0 ? "PENDING_APPROVAL" : "APPROVED", author, i % 10 + 1, i % 50 == 0});
            tags.add(new Object[]{id, i % 100 + 1});
            tags.add(new Object[]{id, (i + 1) % 100 + 1});
            if (i % 10 == 1) votes.add(new Object[]{id, 1, i % 30 == 1 ? "DOWNVOTE" : "UPVOTE"});
            if (i % 50 == 1) files.add(new Object[]{id, author, "fixture.pdf", "application/pdf", 100});
        }
        jdbc.batchUpdate("INSERT INTO post_identities(id,owner_id) VALUES(?,?)", identities);
        jdbc.batchUpdate("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,is_deleted,created_at,updated_at) "
                + "VALUES(?,?,1,?,?,?,?,?,true,?,'2026-01-01','2026-01-01')", posts);
        jdbc.batchUpdate("INSERT INTO post_tags(post_id,subject_id) VALUES(?,?)", tags);
        jdbc.batchUpdate("INSERT INTO votes(post_id,user_id,vote_type) VALUES(?,?,?)", votes);
        jdbc.batchUpdate("INSERT INTO files(post_id,user_id,file_name,file_type,file_size) VALUES(?,?,?,?,?)", files);
    }

    @Test
    void actualSearchQueriesUseFulltextAndCompositeIndexesOnTenThousandPosts() throws Exception {
        Map<String, PostSearchCriteria> cases = new LinkedHashMap<>();
        cases.put("newest", criteria(null, null, null, null, "newest"));
        cases.put("department", criteria(null, null, 2L, null, "newest"));
        cases.put("author", criteria(null, null, null, 2L, "newest"));
        cases.put("subject", criteria(null, 2L, null, null, "newest"));
        cases.put("fulltext", criteria("quasar", null, null, null, "relevance"));
        cases.put("vote_score", criteria(null, null, null, null, "vote_score"));
        Map<String, JsonNode> plans = new LinkedHashMap<>();
        for (var entry : cases.entrySet()) {
            var sql = repository.buildQuery(entry.getValue());
            JsonNode plan = mapper.readTree(namedJdbc.queryForObject("EXPLAIN FORMAT=JSON " + sql.pageSql(), sql.parameters(), String.class));
            plans.put(entry.getKey(), plan);
            plans.put(entry.getKey() + "_count", mapper.readTree(namedJdbc.queryForObject("EXPLAIN FORMAT=JSON " + sql.countSql(), sql.parameters(), String.class)));
            System.out.println("SEARCH_PLAN " + entry.getKey() + " " + accesses(plan));
        }
        assertTrue(accesses(plans.get("fulltext")).stream().anyMatch(value -> value.contains("fulltext:ft_posts_title_content")));
        assertTrue(accesses(plans.get("newest")).stream().anyMatch(value -> value.contains("idx_posts_search_created")));
        assertTrue(accesses(plans.get("department")).stream().anyMatch(value -> value.contains("idx_posts_search_department")));
        assertTrue(accesses(plans.get("author")).stream().anyMatch(value -> value.contains("idx_posts_search_author")));
        assertTrue(accesses(plans.get("vote_score")).stream().anyMatch(value -> value.contains("uk_votes_post_user")));
        assertEquals(50, repository.search(cases.get("fulltext")).total());
        Files.writeString(Path.of("target/phase-3-query-plans.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plans));
    }

    @Test
    void responseMappingUsesConstantJpaQueryCountAsPageGrows() throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        User actor = new User(); actor.setId(1L); actor.setEmail("fixture1@example.test"); actor.addRole("ROLE_USER");
        statistics.clear();
        mvc.perform(get("/api/v1/posts/search").param("size", "1").with(user(new UserPrincipal(actor))))
                .andExpect(status().isOk());
        long single = statistics.getPrepareStatementCount();
        statistics.clear();
        mvc.perform(get("/api/v1/posts/search").param("size", "100").with(user(new UserPrincipal(actor))))
                .andExpect(status().isOk());
        long hundred = statistics.getPrepareStatementCount();
        System.out.println("SEARCH_MAPPING JPA statements: size=1:" + single + ", size=100:" + hundred + "; JDBC count/page adds 2");
        assertEquals(single, hundred, "Mapping query count must not scale with authors, subjects or files");
        assertTrue(hundred <= 3, "Two entity fetches and one grouped vote query suffice");
    }

    private PostSearchCriteria criteria(String q, Long subject, Long department, Long author, String sort) {
        return new PostSearchCriteria(q, subject, department, author, null, null, 0, 20, sort, null);
    }

    private List<String> accesses(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.has("table_name")) result.add(node.path("table_name").asText() + ":" + node.path("access_type").asText()
                + ":" + node.path("key").asText());
        node.elements().forEachRemaining(child -> { if (child.isContainerNode()) result.addAll(accesses(child)); });
        return result;
    }
}
