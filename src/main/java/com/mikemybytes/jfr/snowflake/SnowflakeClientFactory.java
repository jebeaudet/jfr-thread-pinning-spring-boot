package com.mikemybytes.jfr.snowflake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeClientFactory {
    @Value("${snowflake.url}")
    private String url;
    @Value("${snowflake.username}")
    private String username;
    @Value("${snowflake.password}")
    private String password;
    @Value("${snowflake.schema}")
    private String schema;
    @Value("${snowflake.warehouse}")
    private String warehouse;
    @Value("${snowflake.role}")
    private String role;
    @Value("${snowflake.database}")
    private String database;

    public SnowflakeClient createClient() {
        return new SnowflakeClient(url, username, password, schema, warehouse, role, database);
    }
}
