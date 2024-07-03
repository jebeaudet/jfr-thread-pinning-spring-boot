package com.mikemybytes.jfr.job;

import com.mikemybytes.jfr.snowflake.SnowflakeClient;
import com.mikemybytes.jfr.snowflake.SnowflakeClientFactory;
import com.snowflake.snowpark_java.Row;
import com.snowflake.snowpark_java.types.DataTypes;
import com.snowflake.snowpark_java.types.StructField;
import com.snowflake.snowpark_java.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SnowflakeDummyJob implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(SnowflakeDummyJob.class);

    private SnowflakeClientFactory snowflakeClientFactory;

    private Thread thread;
    private AtomicBoolean running = new AtomicBoolean(true);

    public SnowflakeDummyJob(SnowflakeClientFactory snowflakeClientFactory) {
        this.snowflakeClientFactory = snowflakeClientFactory;
    }

    @Autowired
    public void run() {
        thread = new Thread(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (ThreadLocalRandom.current().nextInt(100) == 99) {
                        logger.info("Running dummy snowflake query job");
                    }

                    try (SnowflakeClient client = snowflakeClientFactory.createClient()) {
                        Row[] rows = getData();

                        client.saveToSnowflake(rows, createActivityStructType(), "SOURCE");
                    }
                    Thread.sleep(1000);
                }catch (InterruptedException e) {
                    logger.info("Job interrupted");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Error running job", e);
                }
            }
        });

        thread.setName("SnowflakeDummyJob");
        thread.start();
    }

    private static StructType createActivityStructType() {
        return new StructType(new StructField[]{new StructField("id", DataTypes.StringType, false),
                new StructField("name", DataTypes.StringType, false),
                new StructField("organizationId", DataTypes.StringType, false),
                new StructField("createdDate", DataTypes.TimestampType, false),
                new StructField("updatedDate", DataTypes.TimestampType, true),
                new StructField("sourceRawConfig", DataTypes.StringType, false)});
    }

    private Row[] getData() {
        String randomData = UUID.randomUUID().toString();
        var data = new Object[]{randomData, randomData, randomData,
                LocalDateTime.now().toString(), LocalDateTime.now().toString(),
                "{}"};

        return new Row[]{new Row(data)};
    }

    @Override
    public void destroy() throws InterruptedException {
        running.set(false);
        thread.interrupt();
        thread.join();
    }
}
