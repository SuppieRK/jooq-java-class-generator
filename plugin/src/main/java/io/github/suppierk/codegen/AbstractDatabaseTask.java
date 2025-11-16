/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.suppierk.codegen;

import io.github.suppierk.codegen.config.DefaultFlywaySettings;
import io.github.suppierk.codegen.config.FlywayConfig;
import io.github.suppierk.codegen.docker.AbstractDatabaseContainer;
import io.github.suppierk.codegen.extensions.DatabaseExtension;
import io.github.suppierk.codegen.util.Strings;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import nu.studer.gradle.jooq.JooqConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Base task that encapsulates the plumbing for Testcontainers database orchestration, Flyway
 * configuration, and project classpath discovery.
 *
 * <p>{@link GeneratorTask} and {@link FlywayMigrateTask} both extend this class to reuse the logic
 * for resolving drivers, locating migration resources, and wiring Flyway.
 *
 * @see GeneratorTask
 * @see FlywayMigrateTask
 */
abstract class AbstractDatabaseTask extends DefaultTask {
  /** Fallback Flyway location used when no migration locations are defined. */
  protected static final String FALLBACK_FLYWAY_LOCATION = "classpath:db/migration";

  /** Database definition supplying container overrides and driver selection. */
  @Nonnull protected final DatabaseExtension databaseExtension;

  /** Flyway configuration provided by the schema DSL. */
  @Nonnull protected final FlywayConfig flywayConfig;

  /** jOOQ configuration currently being processed. */
  @Nonnull protected final JooqConfig jooqConfig;

  /** Optional driver override supplied by the DSL. */
  @Nullable protected String overrideDriverClassName;

  /** Name of the schema currently processed (if provided by the DSL). */
  @Nullable protected String schemaName;

  /** Name of the logical database currently processed (if provided by the DSL). */
  @Nullable protected String databaseName;

  /** File collection representing the Flyway runtime classpath. */
  private final ConfigurableFileCollection flywayRuntimeClasspath;

  /** File collection Gradle fingerprints to detect Flyway classpath changes. */
  private final ConfigurableFileCollection flywayClasspathInput;

  /**
   * Creates a database-aware task bound to the supplied DSL database entry, Flyway configuration,
   * and jOOQ configuration.
   *
   * @param databaseExtension DSL-backed database definition
   * @param flywayConfig schema-specific Flyway configuration
   * @param jooqConfig jOOQ configuration associated with the task
   */
  protected AbstractDatabaseTask(
      @Nonnull DatabaseExtension databaseExtension,
      @Nonnull FlywayConfig flywayConfig,
      @Nonnull JooqConfig jooqConfig) {
    this.databaseExtension = databaseExtension;
    this.flywayConfig = flywayConfig;
    this.jooqConfig = jooqConfig;
    this.flywayRuntimeClasspath = getProject().getObjects().fileCollection();
    this.flywayClasspathInput = getProject().getObjects().fileCollection();
    configureFlywayClasspathSources();
  }

  /**
   * Applies schema metadata captured from the DSL.
   *
   * @param databaseName logical database identifier (used for diagnostics)
   * @param schemaName logical schema identifier (used for diagnostics)
   * @param driverClassName optional driver override provided by the DSL
   */
  public void applyDslOverrides(
      @Nonnull String databaseName, @Nonnull String schemaName, @Nullable String driverClassName) {
    this.databaseName = databaseName;
    this.schemaName = schemaName;
    this.overrideDriverClassName = driverClassName;
  }

  /**
   * Runtime classpath Gradle should fingerprint to detect Flyway code changes.
   *
   * @return file collection representing the Flyway runtime classpath
   */
  @Classpath
  public @Nonnull ConfigurableFileCollection getFlywayClasspath() {
    return flywayClasspathInput;
  }

  /**
   * Runs Flyway migrations using a container supplied by the extension.
   *
   * @param db database container to target
   * @param schema schema to migrate
   */
  protected final void runFlywayMigrations(
      @Nonnull AbstractDatabaseContainer db, @Nonnull String schema) {
    try (URLClassLoader flywayClassLoader = createFlywayClassLoader()) {
      runFlywayMigrations(db, schema, flywayClassLoader);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to execute Flyway migrations", e);
    }
  }

  /**
   * Runs Flyway migrations using a dedicated class loader.
   *
   * @param db database container to target
   * @param schema schema to migrate
   * @param classLoader class loader to use when resolving application classes
   */
  protected final void runFlywayMigrations(
      @Nonnull AbstractDatabaseContainer db,
      @Nonnull String schema,
      @Nonnull URLClassLoader classLoader) {
    final var originalClassLoader = Thread.currentThread().getContextClassLoader();

    try {
      Thread.currentThread().setContextClassLoader(classLoader);

      final FluentConfiguration configuration = Flyway.configure(classLoader);
      applyFlywayConfiguration(configuration, db, schema);
      configuration.load().migrate();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to execute Flyway migrations", e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  /**
   * Resolves the schema to target, preferring DSL overrides, then Flyway defaults, and finally the
   * existing jOOQ configuration. Throws a detailed exception when the DSL does not identify a
   * schema or when Flyway and jOOQ disagree.
   *
   * @param fallbackSchema schema declared in the jOOQ configuration
   * @return resolved schema name
   */
  @Nonnull
  protected final String resolveSchema(@Nullable String fallbackSchema) {
    final String flywayDefaultSchema =
        normalizeSchema(resolveString(FlywayConfig::getDefaultSchema));
    final String flywaySchemaFromList = determineFlywaySchemaFromList();
    final String jooqInputSchema = normalizeSchema(fallbackSchema);
    final String dslSchemaName = normalizeSchema(schemaName);

    final String preferredFlywaySchema =
        flywayDefaultSchema != null ? flywayDefaultSchema : flywaySchemaFromList;

    if (preferredFlywaySchema != null && jooqInputSchema != null) {
      if (!canonicalizeSchema(preferredFlywaySchema).equals(canonicalizeSchema(jooqInputSchema))) {
        throw new IllegalStateException(
            "Flyway default schema '%s' does not match jOOQ input schema '%s' for %s."
                .formatted(preferredFlywaySchema, jooqInputSchema, describeSchemaContext()));
      }
      return preferredFlywaySchema;
    }

    if (preferredFlywaySchema != null) {
      return preferredFlywaySchema;
    }

    if (jooqInputSchema != null) {
      return jooqInputSchema;
    }

    if (dslSchemaName != null) {
      return dslSchemaName;
    }

    throw new IllegalStateException(
        "Database schema must be declared via Flyway 'defaultSchema' or jOOQ 'inputSchema' for "
            + describeSchemaContext()
            + ".");
  }

  private @Nullable String determineFlywaySchemaFromList() {
    final var schemas = resolveList(FlywayConfig::getSchemas);
    for (String schema : schemas) {
      final String normalized = normalizeSchema(schema);
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }

  /**
   * Resolves the JDBC driver to use, preferring DSL overrides, then Flyway defaults, and finally
   * the existing jOOQ configuration. Throws a descriptive exception when no driver can be found so
   * that users know how to amend their configuration.
   *
   * @param fallbackDriver driver declared in the jOOQ configuration
   * @return resolved driver class name
   */
  @Nonnull
  protected final String resolveDriver(@Nullable String fallbackDriver) {
    if (overrideDriverClassName != null && !overrideDriverClassName.isBlank()) {
      return overrideDriverClassName;
    }

    final String databaseDriver = databaseExtension.getDriver().getOrNull();
    if (databaseDriver != null && !databaseDriver.isBlank()) {
      return databaseDriver;
    }

    final String flywayDriver = resolveString(FlywayConfig::getDriver);
    if (flywayDriver != null && !flywayDriver.isBlank()) {
      return flywayDriver;
    }

    if (fallbackDriver != null && !fallbackDriver.isBlank()) {
      return fallbackDriver;
    }

    throw new IllegalStateException(
        "Database driver class must be declared. Set 'driver' in the jooqCodegen database DSL, "
            + "configure 'flyway { driver = ... }', or provide JDBC driver coordinates in the jOOQ configuration.");
  }

  /**
   * Ensures the supplied driver class can be resolved via either the current context class loader
   * or the provided fallback. Prevents confusing Testcontainers errors by verifying driver
   * availability up front.
   *
   * @param driverClassName fully qualified JDBC driver class name
   * @param fallbackLoader class loader capable of resolving project dependencies
   */
  protected final void ensureDriverAvailable(
      @Nonnull String driverClassName, @Nonnull ClassLoader fallbackLoader) {
    try {
      Class.forName(driverClassName);
      return;
    } catch (ClassNotFoundException ignored) {
      // Attempt to load using provided loader
    }

    try {
      Class.forName(driverClassName, true, fallbackLoader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Database driver '%s' is not available on the project classpath"
              .formatted(driverClassName),
          e);
    }
  }

  /**
   * Creates a dedicated class loader containing the runtime classpath needed for Flyway execution.
   *
   * @return new {@link URLClassLoader} instance
   */
  protected final @Nonnull URLClassLoader createFlywayClassLoader() {
    final var files = collectFlywayClasspathEntries();
    final URL[] urls = files.stream().filter(File::exists).map(this::toUrl).toArray(URL[]::new);
    return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Executes the supplied action with a managed database container and dedicated class loader.
   *
   * @param driverClassName JDBC driver class to resolve
   * @param action callback invoked once the container is ready
   */
  protected final void withDatabase(
      @Nonnull String driverClassName, @Nonnull DatabaseAction action) {
    final ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
    try (URLClassLoader runtimeClassLoader = createFlywayClassLoader()) {
      Thread.currentThread().setContextClassLoader(runtimeClassLoader);
      ensureDriverAvailable(driverClassName, runtimeClassLoader);

      try (AbstractDatabaseContainer database =
          databaseExtension.createDatabaseContainer(driverClassName, runtimeClassLoader)) {
        action.execute(database, runtimeClassLoader);
      }
    } catch (Exception e) {
      if (e instanceof IllegalStateException ise) {
        throw ise;
      }
      throw new IllegalStateException(e.getMessage(), e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextLoader);
    }
  }

  /**
   * Gathers the runtime classpath entries that should back Flyway execution.
   *
   * @return set of files contributing to the Flyway classpath
   */
  protected final @Nonnull Set<File> collectFlywayClasspathEntries() {
    final Set<File> files = new LinkedHashSet<>();
    for (File file : flywayRuntimeClasspath.getFiles()) {
      if (file.exists()) {
        files.add(file);
      }
    }
    return files;
  }

  /** Configures the lazily evaluated Flyway runtime classpath sources. */
  private void configureFlywayClasspathSources() {
    final var project = getProject();
    final var javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
    if (javaPluginExtension != null) {
      final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
      final SourceSet main = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
      if (main != null) {
        flywayRuntimeClasspath.from(main.getRuntimeClasspath());
        flywayClasspathInput.from(
            (Callable<Iterable<File>>) () -> main.getOutput().getClassesDirs().getFiles());
      }
    }
    addConfigurationClasspath("runtimeClasspath");
    addConfigurationClasspath("compileClasspath");
    addConfigurationClasspath("jooqGenerator");
  }

  private void addConfigurationClasspath(@Nonnull String configurationName) {
    final Configuration configuration =
        getProject().getConfigurations().findByName(configurationName);
    if (configuration != null) {
      flywayRuntimeClasspath.from(configuration);
      flywayClasspathInput.from((Callable<Iterable<File>>) configuration::getFiles);
    }
  }

  /** Converts the supplied file into a {@link URL}, raising an exception when conversion fails. */
  private @Nonnull URL toUrl(@Nonnull File file) {
    try {
      return file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Cannot convert path to URL: " + file, e);
    }
  }

  /** Applies all resolved Flyway settings to the fluent configuration instance. */
  private void applyFlywayConfiguration(
      @Nonnull FluentConfiguration configuration,
      @Nonnull AbstractDatabaseContainer db,
      @Nonnull String fallbackSchema) {
    configuration.dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());

    final var flywayDefaultSchema = normalizeSchema(resolveString(FlywayConfig::getDefaultSchema));
    final var fallbackDefaultSchema = normalizeSchema(fallbackSchema);
    final var effectiveDefaultSchema =
        flywayDefaultSchema != null ? flywayDefaultSchema : fallbackDefaultSchema;

    if (effectiveDefaultSchema != null) {
      configuration.defaultSchema(effectiveDefaultSchema);
    }

    final var schemas = resolveList(FlywayConfig::getSchemas);
    if (!schemas.isEmpty()) {
      configuration.schemas(schemas.toArray(String[]::new));
    } else if (effectiveDefaultSchema != null) {
      configuration.schemas(effectiveDefaultSchema);
    }

    final var locations = resolveList(FlywayConfig::getLocations);
    final var effectiveLocations =
        locations.isEmpty() ? List.of(FALLBACK_FLYWAY_LOCATION) : locations;
    configuration.locations(effectiveLocations.toArray(String[]::new));

    final var callbackLocations = resolveList(FlywayConfig::getCallbackLocations);
    if (!callbackLocations.isEmpty()) {
      configuration.callbackLocations(callbackLocations.toArray(String[]::new));
    }

    applyInteger(FlywayConfig::getConnectRetries, configuration::connectRetries);
    applyInteger(FlywayConfig::getConnectRetriesInterval, configuration::connectRetriesInterval);
    applyString(FlywayConfig::getInitSql, configuration::initSql);
    applyString(FlywayConfig::getTable, configuration::table);
    applyString(FlywayConfig::getTablespace, configuration::tablespace);
    applyString(FlywayConfig::getBaselineVersion, configuration::baselineVersion);
    applyString(FlywayConfig::getBaselineDescription, configuration::baselineDescription);
    applyList(
        FlywayConfig::getResolvers,
        values -> configuration.resolvers(values.toArray(String[]::new)));
    applyBoolean(FlywayConfig::getSkipDefaultResolvers, configuration::skipDefaultResolvers);

    applySqlMigrationPrefix(configuration);

    applyString(
        FlywayConfig::getRepeatableSqlMigrationPrefix, configuration::repeatableSqlMigrationPrefix);
    applyString(FlywayConfig::getSqlMigrationSeparator, configuration::sqlMigrationSeparator);
    applyList(
        FlywayConfig::getSqlMigrationSuffixes,
        values -> configuration.sqlMigrationSuffixes(values.toArray(String[]::new)));
    applyString(FlywayConfig::getEncoding, configuration::encoding);
    applyBoolean(FlywayConfig::getDetectEncoding, configuration::detectEncoding);
    applyInteger(FlywayConfig::getLockRetryCount, configuration::lockRetryCount);

    final var placeholders = resolveMap(FlywayConfig::getPlaceholders);
    if (!placeholders.isEmpty()) {
      configuration.placeholders(placeholders);
    }

    final var jdbcProperties = resolveMap(FlywayConfig::getJdbcProperties);
    if (!jdbcProperties.isEmpty()) {
      configuration.jdbcProperties(jdbcProperties);
    }

    applyBoolean(FlywayConfig::getPlaceholderReplacement, configuration::placeholderReplacement);
    applyString(FlywayConfig::getPlaceholderPrefix, configuration::placeholderPrefix);
    applyString(FlywayConfig::getPlaceholderSuffix, configuration::placeholderSuffix);
    applyString(FlywayConfig::getPlaceholderSeparator, configuration::placeholderSeparator);
    applyString(FlywayConfig::getScriptPlaceholderPrefix, configuration::scriptPlaceholderPrefix);
    applyString(FlywayConfig::getScriptPlaceholderSuffix, configuration::scriptPlaceholderSuffix);
    applyString(FlywayConfig::getTarget, configuration::target);
    applyList(
        FlywayConfig::getLoggers, values -> configuration.loggers(values.toArray(String[]::new)));
    applyList(
        FlywayConfig::getCallbacks,
        values -> configuration.callbacks(values.toArray(String[]::new)));
    applyBoolean(FlywayConfig::getSkipDefaultCallbacks, configuration::skipDefaultCallbacks);
    applyBoolean(FlywayConfig::getOutOfOrder, configuration::outOfOrder);
    applyBoolean(FlywayConfig::getSkipExecutingMigrations, configuration::skipExecutingMigrations);
    applyBoolean(FlywayConfig::getOutputQueryResults, configuration::outputQueryResults);
    applyBoolean(FlywayConfig::getValidateOnMigrate, configuration::validateOnMigrate);
    applyBoolean(FlywayConfig::getCleanOnValidationError, configuration::cleanOnValidationError);
    applyList(
        FlywayConfig::getIgnoreMigrationPatterns,
        values -> configuration.ignoreMigrationPatterns(values.toArray(String[]::new)));
    applyBoolean(FlywayConfig::getValidateMigrationNaming, configuration::validateMigrationNaming);
    applyBoolean(FlywayConfig::getCleanDisabled, configuration::cleanDisabled);
    applyBoolean(FlywayConfig::getBaselineOnMigrate, configuration::baselineOnMigrate);
    applyBoolean(FlywayConfig::getMixed, configuration::mixed);
    applyBoolean(FlywayConfig::getGroup, configuration::group);
    applyString(FlywayConfig::getInstalledBy, configuration::installedBy);
    applyList(
        FlywayConfig::getErrorOverrides,
        values -> configuration.errorOverrides(values.toArray(String[]::new)));
    applyString(FlywayConfig::getDryRunOutput, configuration::dryRunOutput);
    applyBoolean(FlywayConfig::getStream, configuration::stream);
    applyBoolean(FlywayConfig::getBatch, configuration::batch);
    applyString(FlywayConfig::getWorkingDirectory, configuration::workingDirectory);
    applyBoolean(FlywayConfig::getCreateSchemas, configuration::createSchemas);
    applyBoolean(FlywayConfig::getFailOnMissingLocations, configuration::failOnMissingLocations);

    final var properties = new LinkedHashMap<String, String>();

    addPropertyOverride(
        properties,
        "undoSqlMigrationPrefix",
        resolveString(FlywayConfig::getUndoSqlMigrationPrefix));
    addPropertyOverride(
        properties,
        "oracleSqlplus",
        booleanToString(resolveBoolean(FlywayConfig::getOracleSqlplus)));
    addPropertyOverride(
        properties,
        "oracleSqlplusWarn",
        booleanToString(resolveBoolean(FlywayConfig::getOracleSqlplusWarn)));
    addPropertyOverride(
        properties, "oracleWalletLocation", resolveString(FlywayConfig::getOracleWalletLocation));
    addPropertyOverride(properties, "licenseKey", resolveString(FlywayConfig::getLicenseKey));
    addPropertyOverride(
        properties, "configFileEncoding", resolveString(FlywayConfig::getConfigFileEncoding));

    final var configFiles = resolveList(FlywayConfig::getConfigFiles);
    if (!configFiles.isEmpty()) {
      addPropertyOverride(properties, "configFiles", String.join(",", configFiles));
    }

    final var cherryPick = resolveList(FlywayConfig::getCherryPick);
    if (!cherryPick.isEmpty()) {
      addPropertyOverride(properties, "cherryPick", String.join(",", cherryPick));
    }

    resolveMap(FlywayConfig::getPluginConfiguration).forEach(properties::putIfAbsent);

    if (!properties.isEmpty()) {
      configuration.configuration(properties);
    }
  }

  /**
   * Resolves a string property from schema overrides or the base Flyway configuration.
   *
   * @param getter accessor to invoke on the configuration
   * @return resolved value or {@code null}
   */
  protected final @Nullable String resolveString(@Nonnull Function<FlywayConfig, String> getter) {
    final var base = getter.apply(flywayConfig);
    return base != null && !base.isBlank() ? base : null;
  }

  /**
   * Resolves a list property from schema overrides or the base Flyway configuration.
   *
   * @param getter accessor to invoke on the configuration
   * @return resolved list (never {@code null})
   */
  protected final @Nonnull List<String> resolveList(
      @Nonnull Function<FlywayConfig, List<String>> getter) {
    final var base = getter.apply(flywayConfig);
    return base != null ? base : Collections.emptyList();
  }

  /**
   * Resolves a map property from schema overrides or the base Flyway configuration.
   *
   * @param getter accessor to invoke on the configuration
   * @return resolved map combining overrides and defaults
   */
  protected final @Nonnull Map<String, String> resolveMap(
      @Nonnull Function<FlywayConfig, Map<String, String>> getter) {
    final var values = getter.apply(flywayConfig);
    if (values == null || values.isEmpty()) {
      return Collections.emptyMap();
    }
    return new LinkedHashMap<>(values);
  }

  /**
   * Resolves an integer property from schema overrides or the base Flyway configuration.
   *
   * @param getter accessor to invoke on the configuration
   * @return resolved integer or {@code null}
   */
  protected final @Nullable Integer resolveInteger(
      @Nonnull Function<FlywayConfig, Integer> getter) {
    return getter.apply(flywayConfig);
  }

  /**
   * Resolves a boolean property from schema overrides or the base Flyway configuration.
   *
   * @param getter accessor to invoke on the configuration
   * @return resolved boolean or {@code null}
   */
  protected final @Nullable Boolean resolveBoolean(
      @Nonnull Function<FlywayConfig, Boolean> getter) {
    return getter.apply(flywayConfig);
  }

  /** Converts the supplied {@link Boolean} into its string representation when non-null. */
  private @Nullable String booleanToString(@Nullable Boolean value) {
    return value == null ? null : Boolean.toString(value);
  }

  /**
   * Adds a Flyway property override to the properties map, including both camel case and dotted
   * variants, when the value is present.
   */
  private void addPropertyOverride(
      @Nonnull Map<String, String> overrides,
      @Nonnull String propertyName,
      @Nullable String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    overrides.put("flyway." + propertyName, value);
    overrides.put("flyway." + camelCaseToDottedLower(propertyName), value);
  }

  /** Converts camel-case property names to their dotted lowercase representation. */
  private @Nonnull String camelCaseToDottedLower(@Nonnull String value) {
    final var builder = new StringBuilder();
    final char[] chars = value.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      final char current = chars[i];
      final boolean isUpper = Character.isUpperCase(current);
      if (i > 0) {
        final char previous = chars[i - 1];
        final boolean previousUpper = Character.isUpperCase(previous);
        final boolean nextLower = i + 1 < chars.length && Character.isLowerCase(chars[i + 1]);
        if ((isUpper && !previousUpper) || (isUpper && nextLower)) {
          builder.append('.');
        }
      }
      builder.append(Character.toLowerCase(current));
    }
    return builder.toString();
  }

  /** Trims schema values and converts blank inputs to {@code null}. */
  private @Nullable String normalizeSchema(@Nullable String value) {
    if (value == null) {
      return null;
    }
    final String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() >= 2) {
      final char first = trimmed.charAt(0);
      final char last = trimmed.charAt(trimmed.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed.substring(1, trimmed.length() - 1);
      }
    }
    return trimmed;
  }

  private @Nonnull String canonicalizeSchema(@Nonnull String value) {
    return value.toLowerCase(java.util.Locale.ROOT);
  }

  /** Produces a human-friendly description of the schema context for error messages. */
  private @Nonnull String describeSchemaContext() {
    if (schemaName != null && databaseName != null) {
      return "schema '%s' in temporary database '%s'".formatted(schemaName, databaseName);
    }
    if (schemaName != null) {
      return "schema '%s'".formatted(schemaName);
    }
    if (databaseName != null) {
      return "temporary database '%s'".formatted(databaseName);
    }
    return "the configured schema";
  }

  /** Applies the sqlMigrationPrefix override if it differs from the defaults and is valid. */
  private void applySqlMigrationPrefix(@Nonnull FluentConfiguration configuration) {
    final var prefix = resolveString(FlywayConfig::getSqlMigrationPrefix);
    if (prefix == null || prefix.isBlank()) {
      return;
    }

    if (DefaultFlywaySettings.SQL_MIGRATION_PREFIX.equals(prefix)) {
      getLogger()
          .warn(
              "Skipping Flyway sqlMigrationPrefix override because value '{}' matches the default prefix. Set a different prefix to customise versioned migration naming.",
              prefix);
      return;
    }

    final var repeatablePrefix =
        Strings.firstNonBlank(
            resolveString(FlywayConfig::getRepeatableSqlMigrationPrefix),
            DefaultFlywaySettings.REPEATABLE_SQL_MIGRATION_PREFIX);

    if (repeatablePrefix.equals(prefix)) {
      getLogger()
          .warn(
              "Skipping Flyway sqlMigrationPrefix override because value '{}' matches the repeatable migration prefix '{}'. Flyway requires distinct prefixes for versioned and repeatable migrations.",
              prefix,
              repeatablePrefix);
      return;
    }

    configuration.sqlMigrationPrefix(prefix);
  }

  /** Applies a string property when a non-null value can be resolved. */
  private void applyString(
      @Nonnull Function<FlywayConfig, String> getter, @Nonnull Consumer<String> setter) {
    final var value = resolveString(getter);
    if (value != null) {
      setter.accept(value);
    }
  }

  /** Applies a boolean property when a non-null value can be resolved. */
  private void applyBoolean(
      @Nonnull Function<FlywayConfig, Boolean> getter, @Nonnull Consumer<Boolean> setter) {
    final var value = resolveBoolean(getter);
    if (value != null) {
      setter.accept(value);
    }
  }

  /** Applies an integer property when a non-null value can be resolved. */
  private void applyInteger(
      @Nonnull Function<FlywayConfig, Integer> getter, @Nonnull Consumer<Integer> setter) {
    final var value = resolveInteger(getter);
    if (value != null) {
      setter.accept(value);
    }
  }

  /** Applies a list property when the resolved list is not empty. */
  private void applyList(
      @Nonnull Function<FlywayConfig, List<String>> getter,
      @Nonnull Consumer<List<String>> setter) {
    final var values = resolveList(getter);
    if (!values.isEmpty()) {
      setter.accept(values);
    }
  }

  /** Callback used to perform work against a managed Testcontainers database instance. */
  @FunctionalInterface
  protected interface DatabaseAction {
    void execute(@Nonnull AbstractDatabaseContainer database, @Nonnull URLClassLoader classLoader)
        throws Exception;
  }
}
