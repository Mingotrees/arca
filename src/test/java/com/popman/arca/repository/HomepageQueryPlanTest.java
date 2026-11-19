package com.popman.arca.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HomepageQueryPlanTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;

    @Test void boundedHomepageQueriesOnTenThousandPostsAndOneThousandAnnouncements() throws Exception {
        Instant now = Instant.parse("2026-11-18T12:00:00Z"), from = now.minus(Duration.ofDays(7));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> seed(now));
        jdbc.execute("ANALYZE TABLE posts,votes,announcements");
        LocalDateTime start = LocalDateTime.ofInstant(from, ZoneOffset.UTC), end = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        Map<String, JsonNode> plans = new LinkedHashMap<>();
        plans.put("trending", mapper.readTree(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + TrendingRepository.TRENDING_SQL, String.class, start,end,start,end,end,50)));
        plans.put("announcements", mapper.readTree(jdbc.queryForObject("EXPLAIN FORMAT=JSON " + AnnouncementRepository.ACTIVE_SQL, String.class, end,end,50)));
        var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var connection = jdbc.getDataSource().getConnection()) {
            var counted = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) count.incrementAndGet();
                        try { return method.invoke(connection, args); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                    });
            var template = new JdbcTemplate(new SingleConnectionDataSource(counted, true));
            long began = System.nanoTime();
            var trending = new TrendingRepository(template).find(from, now, 50);
            long trendingMs = (System.nanoTime() - began) / 1_000_000;
            assertEquals(50, trending.size()); assertEquals(50, trending.stream().map(p -> p.postId()).distinct().count()); assertEquals(1, count.get());
            count.set(0); began = System.nanoTime();
            var announcements = new AnnouncementRepository(template).active(now, 50);
            long announcementsMs = (System.nanoTime() - began) / 1_000_000;
            assertEquals(50, announcements.size()); assertEquals(1, count.get());
            System.out.println("HOMEPAGE_QUERIES posts=10000 votes=10000 announcements=1000 trending_ms=" + trendingMs + " announcements_ms=" + announcementsMs + " queries_each=1");
        }
        assertTrue(plans.get("trending").toString().contains("idx_posts_trending"));
        Files.writeString(Path.of("target/phase-10-query-plans.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plans));
    }

    private void seed(Instant now) {
        for (String table : new String[]{"announcements", "audit_events", "files", "votes", "vaults", "post_tags", "posts", "post_identities", "user_roles",
                "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,email,is_deleted) VALUES(1,'corpus@example.test',false)");
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')"); jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
        var identities = new ArrayList<Object[]>(); var posts = new ArrayList<Object[]>(); var votes = new ArrayList<Object[]>();
        for (int i = 0; i < 10000; i++) {
            int id = 1000 + i;
            identities.add(new Object[]{id});
            posts.add(new Object[]{id,id,LocalDateTime.ofInstant(now.minus(Duration.ofHours(i % 168 + 1)), ZoneOffset.UTC)});
            votes.add(new Object[]{id, i % 5 == 0 ? "DOWNVOTE" : "UPVOTE", LocalDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC)});
        }
        jdbc.batchUpdate("INSERT INTO post_identities(id,owner_id) VALUES(?,1)", identities);
        jdbc.batchUpdate("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,created_at,updated_at,approved_at) VALUES(?,?,1,'Corpus','Body','APPROVED',1,1,true,'2026-01-01','2026-01-01',?)", posts);
        jdbc.batchUpdate("INSERT INTO votes(post_id,user_id,vote_type,changed_at) VALUES(?,1,?,?)", votes);
        var announcements = new ArrayList<Object[]>();
        for (int i = 0; i < 1000; i++) announcements.add(new Object[]{"Announcement " + i, i % 10, i % 5 != 0});
        jdbc.batchUpdate("INSERT INTO announcements(title,body,priority,active,created_by,updated_by,created_at,updated_at) VALUES(?,'Body',?,?,1,1,'2026-11-01','2026-11-01')", announcements);
    }
}
