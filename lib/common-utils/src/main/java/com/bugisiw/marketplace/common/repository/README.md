# Shared Repository Package

This package contains shared repositories that can be used across multiple services in the marketplace.

## UsageLogRepository

The `UsageLogRepository` is a shared component used to log usage data to the database. It uses
a non-blocking approach with a `BlockingQueue` and virtual threads to ensure high performance.

### Features

- Non-blocking logging via a queue
- Batch processing of logs to improve database performance
- Scheduled flushing of the queue every 5 seconds
- Monitoring of queue size with warnings when thresholds are exceeded
- Support for both synchronous and asynchronous requests
- Comprehensive error handling

### Usage Example

```java
@Service
public class SomeService {
    private final UsageLogRepository usageLogRepository;
    
    @Autowired
    public SomeService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }
    
    public void processSomething(String userId, String agentId) {
        // Create a usage log
        UsageLog usageLog = UsageLog.builder()
                .userId(userId)
                .agentId(agentId)
                .requestId(UUID.randomUUID().toString())
                .requestType(RequestType.SYNC)
                .requestCount(1)
                .isOverage(false)
                .cost(0.0)
                .status(JobStatus.COMPLETED)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();
                
        // Log it asynchronously
        usageLogRepository.logAsync(usageLog);
    }
}
```

### Configuration

To use the `UsageLogRepository`, you need to configure a datasource for the usage database:

```yaml
# In application.yml (use jdbc-url for HikariCP when driver-class-name is set)
usage-db:
  jdbc-url: jdbc:postgresql://localhost:5432/usage_db
  username: postgres
  password: postgres
  driver-class-name: org.postgresql.Driver

usage-logging:
  queue:
    capacity: 10000
    warning-threshold: 7500
    batch-size: 100
    flush-rate-ms: 5000
```

And make sure to import the configuration in your Spring application:

```java
@SpringBootApplication
@Import(UsageLogConfig.class)
public class YourApplication {
    // ...
}
``` 