/*
 * Copyright (c) Coveo Solutions Inc.
 */
package com.mikemybytes.jfr.snowflake;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

import com.snowflake.snowpark_java.DataFrame;
import com.snowflake.snowpark_java.Row;
import com.snowflake.snowpark_java.SaveMode;
import com.snowflake.snowpark_java.Session;
import com.snowflake.snowpark_java.types.StructType;

public class SnowflakeClient implements Closeable {
    private Session session;


    public SnowflakeClient(String url, String username, String password, String schema, String warehouse, String role, String database) {
        createSnowflakeSession(url, username, password, schema, warehouse, role, database);
    }

    private void createSnowflakeSession(String url, String username, String password, String schema, String warehouse, String role, String database) {
        Map<String, String> params = new HashMap<>();
        params.put("URL", url);
        params.put("USER", username);
        params.put("PASSWORD", password);
        params.put("SCHEMA", schema);
        params.put("WAREHOUSE", warehouse);
        params.put("ROLE", role);
        params.put("DATABASE", database);

        session = Session.builder().configs(params).create();
        session.sql("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING='TIMESTAMP_TZ'").collect();
        session.sql("ALTER SESSION SET TIMEZONE='UTC'").collect();
    }

    public void saveToSnowflake(Row[] rows, StructType structType, String tableName) {
        DataFrame dateFrame = session.createDataFrame(rows, structType);
        dateFrame.write().mode(SaveMode.Append).saveAsTable(tableName);
    }

    @Override
    public void close() {
        if (session != null) {
            session.close();
        }
    }
}
