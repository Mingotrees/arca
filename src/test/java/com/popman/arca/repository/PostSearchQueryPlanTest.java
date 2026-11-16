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
    @Autowired ProfileStatisticsRepository statisticsRepository;
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
        jdbc.update("INSERT INTO post_links(post_id,url,normalized_url,normalized_hash,position) "
                + "SELECT id,'https://example.test/reference','https://example.test/reference',SHA2('https://example.test/reference',256),0 FROM posts");
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
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.content[0].links[0].url").value("https://example.test/reference"));
        long hundred = statistics.getPrepareStatementCount();
        System.out.println("SEARCH_MAPPING JPA statements: size=1:" + single + ", size=100:" + hundred + "; JDBC count/page adds 2");
        assertEquals(single, hundred, "Mapping query count must not scale with authors, subjects or files");
        assertTrue(hundred <= 4, "Three separate entity/collection fetches and one grouped vote query suffice");
    }

    @Test
    void profileAggregatesUseExistingIndexesOnRepresentativeCorpus() throws Exception {
        JsonNode user = mapper.readTree(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + ProfileStatisticsRepository.USER_STATS_SQL, String.class, 2L));
        JsonNode subject = mapper.readTree(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + ProfileStatisticsRepository.SUBJECT_STATS_SQL, String.class, 2L));
        System.out.println("PROFILE_PLAN user " + accesses(user));
        System.out.println("PROFILE_PLAN subject " + accesses(subject));
        assertTrue(accesses(user).stream().anyMatch(value -> value.startsWith("p:ref:")
                && (value.contains("idx_posts_search_author") || value.contains("idx_user_id"))));
        assertTrue(accesses(user).stream().anyMatch(value -> value.contains("uk_votes_post_user")));
        assertTrue(accesses(subject).stream().anyMatch(value -> value.contains("idx_post_tags_subject_post")));
        assertEquals(100, statisticsRepository.user(2L).approvedPostCount());
        assertEquals(66, statisticsRepository.user(2L).upvotesReceived());
        assertEquals(34, statisticsRepository.user(2L).downvotesReceived());
        assertEquals(100, statisticsRepository.subject(2L).approvedPostCount());
        Files.writeString(Path.of("target/phase-5-query-plans.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("user", user, "subject", subject)));
    }

    private PostSearchCriteria criteria(String q, Long subject, Long department, Long author, String sort) {
        return new PostSearchCriteria(q, subject, department, author, null, null, 0, 20, sort, null);
    }

    @Test
    void dashboardUsesSevenRealQueriesOnTenThousandPostsAndCapturesPlans() throws Exception {
        Map<String, JsonNode> plans = new LinkedHashMap<>();
        Map<String, String> queries = Map.of("users", AdminDashboardRepository.USERS_SQL,
                "roles", AdminDashboardRepository.ROLES_SQL, "status", AdminDashboardRepository.STATUS_SQL,
                "activity", AdminDashboardRepository.ACTIVITY_SQL, "files", AdminDashboardRepository.FILES_SQL,
                "votes", AdminDashboardRepository.VOTES_SQL, "vaults", AdminDashboardRepository.VAULTS_SQL);
        var from = java.time.LocalDateTime.parse("2026-01-01T00:00:00");
        var to = from.plusDays(90);
        for (var entry : queries.entrySet()) {
            Object[] parameters = entry.getKey().equals("activity") ? new Object[]{from, to, from, to} : new Object[0];
            plans.put(entry.getKey(), mapper.readTree(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + entry.getValue(), String.class, parameters)));
        }
        var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var connection = jdbc.getDataSource().getConnection()) {
            var counted = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) count.incrementAndGet();
                        try { return method.invoke(connection, args); }
                        catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                    });
            var repository = new AdminDashboardRepository(new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(counted, true)));
            long start = System.nanoTime();
            var result = repository.dashboard(from, to, java.time.Instant.now());
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            assertEquals(7, count.get(), "Aggregate query count must not scale with corpus size");
            assertEquals(9800, result.posts().active());
            assertEquals(200, result.posts().deleted());
            assertEquals(10000, result.posts().createdInPeriod());
            assertEquals(8000, result.posts().legacyApprovedVersionsWithoutDate());
            assertEquals(1800, result.moderation().pending());
            assertEquals(20000, result.engagement().attachmentBytes());
            System.out.println("DASHBOARD_QUERIES count=" + count.get() + " posts=10000 elapsed_ms=" + elapsed);
            for (var entry : plans.entrySet()) System.out.println("DASHBOARD_PLAN " + entry.getKey() + " " + accesses(entry.getValue()));
            Files.writeString(Path.of("target/phase-8-query-plans.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plans));
        }
    }

    private List<String> accesses(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.has("table_name")) result.add(node.path("table_name").asText() + ":" + node.path("access_type").asText()
                + ":" + node.path("key").asText());
        node.elements().forEachRemaining(child -> { if (child.isContainerNode()) result.addAll(accesses(child)); });
        return result;
    }
}
