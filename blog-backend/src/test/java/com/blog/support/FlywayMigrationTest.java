package com.blog.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FlywayMigrationTest extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migratesAnEmptyDatabaseToVersionSix() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.info().current()).isNull();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(6);
        assertThat(result.targetSchemaVersion).isEqualTo("6");
    }
}
