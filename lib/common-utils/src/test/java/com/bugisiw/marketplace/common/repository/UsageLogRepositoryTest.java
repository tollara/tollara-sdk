package com.bugisiw.marketplace.common.repository;

import com.bugisiw.marketplace.common.model.usage.JobStatus;
import com.bugisiw.marketplace.common.model.usage.RequestType;
import com.bugisiw.marketplace.common.model.usage.UsageLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageLogRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private UsageLogRepository usageLogRepository;

    @Captor
    private ArgumentCaptor<Object[]> paramsCaptor;
    
    @BeforeEach
    public void setup() {
        // Create repository with our mocked jdbcTemplate
        usageLogRepository = new UsageLogRepository(
            jdbcTemplate,
            10, // queueCapacity
            8,  // queueWarningThreshold
            100, // batchSize
            3, // maxRetries
            100 // initialRetryDelayMs
        );
    }

/*     @Test
    void testLogAsync() {
        // Create a test log
        UsageLog log = createTestUsageLog();

        // Log it
        boolean result = usageLogRepository.logAsync(log);

        // Verify
        assertTrue(result);
        assertEquals(1, usageLogRepository.getQueueSize());
    } */

    @Test
    void testFindByUserId() {
        // Setup
        UUID userId = UUID.randomUUID();
        UsageLog expectedLog = createTestUsageLog();
        expectedLog.setUserId(userId);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(userId)))
                .thenReturn(List.of(expectedLog));

        // Execute
        List<UsageLog> result = usageLogRepository.findByUserId(userId);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(userId));
    }

    @Test
    void testUpdateStatus() {
        // Setup
        String requestId = "request123";
        JobStatus newStatus = JobStatus.COMPLETED;
        when(jdbcTemplate.update(anyString(), any(), any(), eq(requestId))).thenReturn(1);

        // Execute
        boolean result = usageLogRepository.updateStatus(requestId, newStatus);

        // Verify
        assertTrue(result);
        // The implementation uses Timestamp.from(Instant.now()), so we need to match Timestamp, not Instant
        verify(jdbcTemplate).update(anyString(), eq(newStatus.name()), any(Timestamp.class), eq(requestId));
    }

    private UsageLog createTestUsageLog() {
        return UsageLog.builder()
                .userId(UUID.randomUUID())
                .agentId(UUID.randomUUID())
                .requestId(UUID.randomUUID().toString())
                .requestType(RequestType.SYNC)
                //.requestCount(1)
                .isOverage(false)
                .cost(new BigDecimal("0.5"))
                .status(JobStatus.RUNNING)
                .startTime(Instant.now())
                .build();
    }
} 