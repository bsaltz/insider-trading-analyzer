package com.github.bsaltz.insider.test

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles

/**
 * Meta-annotation for repository tests that combines common test configurations.
 *
 * This annotation includes:
 * - @DataJdbcTest: Configures a Spring Data JDBC test slice
 * - @ActiveProfiles("test"): Activates the "test" profile
 * - @AutoConfigureEmbeddedDatabase: Configures Zonky embedded PostgreSQL database
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
annotation class RepositoryTest
