/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to do so, subject to the following conditions:
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

package io.github.suppierk.codegen.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Captures Flyway settings normally provided via the Flyway Gradle plugin. The structure mirrors
 * {@code org.flywaydb.core.api.configuration.FluentConfiguration} so that we can apply the same
 * properties programmatically inside {@code AbstractDatabaseTask}.
 */
public class FlywayConfig {
  private static final List<String> DEFAULT_LOCATIONS = List.of("classpath:db/migration");

  private List<String> schemas;
  private List<String> locations;
  private List<String> callbackLocations;
  private List<String> resolvers;
  private List<String> sqlMigrationSuffixes;
  private Map<String, String> placeholders;
  private Map<String, String> jdbcProperties;
  private List<String> cherryPick;
  private List<String> loggers;
  private List<String> callbacks;
  private List<String> ignoreMigrationPatterns;
  private List<String> errorOverrides;
  private List<String> configFiles;
  private Map<String, String> pluginConfiguration;
  @Nullable private String driver;
  @Nullable private Integer connectRetries;
  @Nullable private Integer connectRetriesInterval;
  @Nullable private String initSql;
  @Nullable private String table;
  @Nullable private String tablespace;
  @Nullable private String defaultSchema;
  @Nullable private String baselineVersion;
  @Nullable private String baselineDescription;
  @Nullable private Boolean skipDefaultResolvers;
  @Nullable private String sqlMigrationPrefix;
  @Nullable private String undoSqlMigrationPrefix;
  @Nullable private String repeatableSqlMigrationPrefix;
  @Nullable private String sqlMigrationSeparator;
  @Nullable private String encoding;
  @Nullable private Boolean detectEncoding;
  @Nullable private Integer lockRetryCount;
  @Nullable private Boolean placeholderReplacement;
  @Nullable private String placeholderPrefix;
  @Nullable private String placeholderSuffix;
  @Nullable private String placeholderSeparator;
  @Nullable private String scriptPlaceholderPrefix;
  @Nullable private String scriptPlaceholderSuffix;
  @Nullable private String target;
  @Nullable private Boolean skipDefaultCallbacks;
  @Nullable private Boolean outOfOrder;
  @Nullable private Boolean skipExecutingMigrations;
  @Nullable private Boolean outputQueryResults;
  @Nullable private Boolean validateOnMigrate;
  @Nullable private Boolean cleanOnValidationError;
  @Nullable private Boolean validateMigrationNaming;
  @Nullable private Boolean cleanDisabled;
  @Nullable private Boolean baselineOnMigrate;
  @Nullable private Boolean mixed;
  @Nullable private Boolean group;
  @Nullable private String installedBy;
  @Nullable private String dryRunOutput;
  @Nullable private Boolean stream;
  @Nullable private Boolean batch;
  @Nullable private Boolean oracleSqlplus;
  @Nullable private Boolean oracleSqlplusWarn;
  @Nullable private String oracleWalletLocation;
  @Nullable private String kerberosConfigFile;
  @Nullable private String licenseKey;
  @Nullable private String configFileEncoding;
  @Nullable private String workingDirectory;
  @Nullable private Boolean createSchemas;
  @Nullable private Boolean failOnMissingLocations;

  /** Creates default Flyway configuration values. */
  public FlywayConfig() {
    // Intentionally empty
  }

  /**
   * Produces a defensive copy of the provided list while removing {@code null} entries. Used to
   * ensure Gradle task inputs remain stable and predictable.
   *
   * @param values input list
   * @return defensive copy of the list
   */
  private static @Nullable List<String> sanitizeList(@Nullable List<String> values) {
    if (values == null) {
      return null;
    }
    final List<String> copy = new ArrayList<>(values.size());
    for (String value : values) {
      if (value != null) {
        copy.add(value);
      }
    }
    return copy;
  }

  /**
   * Produces a defensive copy of the provided map while removing {@code null} keys and values.
   * Needed because Flyway's configuration uses plain {@link Map} instances yet Gradle's up-to-date
   * checks require deterministic contents.
   *
   * @param values input map
   * @return defensive copy of the map
   */
  private static @Nullable Map<String, String> sanitizeMap(@Nullable Map<String, String> values) {
    if (values == null) {
      return null;
    }
    final Map<String, String> copy = new LinkedHashMap<>();
    values.forEach(
        (key, val) -> {
          if (key != null && val != null) {
            copy.put(key, val);
          }
        });
    return copy;
  }

  /**
   * Returns an unmodifiable copy of the supplied list, or an empty list when the input is null or
   * empty.
   *
   * @param values input list
   * @return unmodifiable copy of the list or empty list
   */
  private static @Nonnull List<String> unmodifiableListOrEmpty(@Nullable List<String> values) {
    return values == null || values.isEmpty()
        ? Collections.emptyList()
        : Collections.unmodifiableList(values);
  }

  /**
   * Returns an unmodifiable copy of the supplied map, or an empty map when the input is null or
   * empty.
   *
   * @param values input map
   * @return unmodifiable copy of the map or empty map
   */
  private static @Nonnull Map<String, String> unmodifiableMapOrEmpty(
      @Nullable Map<String, String> values) {
    return values == null || values.isEmpty()
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(values);
  }

  /**
   * Returns the explicitly configured JDBC driver class name. When unset, the driver is inherited
   * from the database DSL or jOOQ configuration.
   *
   * @return driver class name or {@code null} if it should be inherited from jOOQ
   */
  public @Nullable String getDriver() {
    return driver;
  }

  /**
   * Sets the JDBC driver class name to use when selecting a database container.
   *
   * @param driver fully qualified driver class name, or {@code null} to fall back to jOOQ config
   */
  public void setDriver(@Nullable String driver) {
    this.driver = driver;
  }

  /**
   * Returns configured connection retry count. When {@code null}, Flyway falls back to its default
   * retry strategy.
   *
   * @return retries or {@code null}
   */
  public @Nullable Integer getConnectRetries() {
    return connectRetries;
  }

  /**
   * Sets the number of times Flyway should retry connecting to the database.
   *
   * @param connectRetries retry count
   */
  public void setConnectRetries(@Nullable Integer connectRetries) {
    this.connectRetries = connectRetries;
  }

  /**
   * Returns configured retry interval.
   *
   * @return interval in seconds or {@code null}
   */
  public @Nullable Integer getConnectRetriesInterval() {
    return connectRetriesInterval;
  }

  /**
   * Sets the wait time between connection retries in seconds.
   *
   * @param connectRetriesInterval interval in seconds
   */
  public void setConnectRetriesInterval(@Nullable Integer connectRetriesInterval) {
    this.connectRetriesInterval = connectRetriesInterval;
  }

  /**
   * Returns configured init SQL.
   *
   * @return SQL or {@code null}
   */
  public @Nullable String getInitSql() {
    return initSql;
  }

  /**
   * Sets SQL executed immediately after obtaining a connection.
   *
   * @param initSql SQL statement
   */
  public void setInitSql(@Nullable String initSql) {
    this.initSql = initSql;
  }

  /**
   * Returns Flyway metadata table name.
   *
   * @return metadata table or {@code null}
   */
  public @Nullable String getTable() {
    return table;
  }

  /**
   * Sets Flyway metadata table name.
   *
   * @param table metadata table
   */
  public void setTable(@Nullable String table) {
    this.table = table;
  }

  /**
   * Returns Flyway metadata tablespace.
   *
   * @return tablespace or {@code null}
   */
  public @Nullable String getTablespace() {
    return tablespace;
  }

  /**
   * Sets Flyway metadata tablespace.
   *
   * @param tablespace metadata tablespace
   */
  public void setTablespace(@Nullable String tablespace) {
    this.tablespace = tablespace;
  }

  /**
   * Returns the configured default schema.
   *
   * @return schema name or {@code null} if no override was provided
   */
  public @Nullable String getDefaultSchema() {
    return defaultSchema;
  }

  /**
   * Sets the schema Flyway should treat as default for migrations.
   *
   * @param defaultSchema schema name or {@code null} to fall back to jOOQ/default value
   */
  public void setDefaultSchema(@Nullable String defaultSchema) {
    this.defaultSchema = defaultSchema;
  }

  /**
   * Convenient schema setter mirroring Gradle DSL.
   *
   * @param schemas schema names
   */
  public void schemas(String... schemas) {
    setSchemas(Arrays.asList(schemas));
  }

  /**
   * Returns configured schema list.
   *
   * @return schema list
   */
  public @Nonnull List<String> getSchemas() {
    return unmodifiableListOrEmpty(schemas);
  }

  /**
   * Replaces the list of schemas managed by Flyway.
   *
   * @param schemas schema names
   */
  public void setSchemas(@Nonnull List<String> schemas) {
    this.schemas = sanitizeList(schemas);
  }

  /**
   * Convenient var-args setter mirroring the Gradle DSL behaviour.
   *
   * @param locations locations to apply
   */
  public void locations(String... locations) {
    setLocations(Arrays.asList(locations));
  }

  /**
   * Returns the configured Flyway migration locations. When users omit locations, the method
   * returns Flyway's conventional default {@code classpath:db/migration}.
   *
   * @return configured locations list (never empty)
   */
  public @Nonnull List<String> getLocations() {
    return locations == null || locations.isEmpty()
        ? DEFAULT_LOCATIONS
        : Collections.unmodifiableList(locations);
  }

  /**
   * Replaces Flyway locations used to discover migrations.
   *
   * @param locations list of locations (e.g. {@code classpath:db/migration})
   */
  public void setLocations(@Nonnull List<String> locations) {
    final List<String> copy = sanitizeList(locations);
    this.locations = (copy == null || copy.isEmpty()) ? null : copy;
  }

  /**
   * Convenience setter accepting a single location value.
   *
   * @param location migration location
   */
  public void setLocations(@Nonnull String location) {
    setLocations(Collections.singletonList(location));
  }

  /**
   * Sets callback locations using var-args.
   *
   * @param locations callback locations
   */
  public void callbackLocations(String... locations) {
    setCallbackLocations(Arrays.asList(locations));
  }

  /**
   * Returns configured callback locations.
   *
   * @return callback locations list
   */
  public @Nonnull List<String> getCallbackLocations() {
    return unmodifiableListOrEmpty(callbackLocations);
  }

  /**
   * Replaces Flyway callback locations.
   *
   * @param locations callback discovery locations
   */
  public void setCallbackLocations(@Nonnull List<String> locations) {
    this.callbackLocations = sanitizeList(locations);
  }

  /**
   * Convenience setter accepting a single callback location value.
   *
   * @param location callback location
   */
  public void setCallbackLocations(@Nonnull String location) {
    setCallbackLocations(Collections.singletonList(location));
  }

  /**
   * Returns configured baseline version.
   *
   * @return baseline version or {@code null}
   */
  public @Nullable String getBaselineVersion() {
    return baselineVersion;
  }

  /**
   * Sets baseline version used when baselining an existing schema.
   *
   * @param baselineVersion version string
   */
  public void setBaselineVersion(@Nullable String baselineVersion) {
    this.baselineVersion = baselineVersion;
  }

  /**
   * Returns baseline description.
   *
   * @return description or {@code null}
   */
  public @Nullable String getBaselineDescription() {
    return baselineDescription;
  }

  /**
   * Sets baseline description.
   *
   * @param baselineDescription description text
   */
  public void setBaselineDescription(@Nullable String baselineDescription) {
    this.baselineDescription = baselineDescription;
  }

  /**
   * Sets resolvers using var-args.
   *
   * @param resolvers resolver class names
   */
  public void resolvers(String... resolvers) {
    setResolvers(Arrays.asList(resolvers));
  }

  /**
   * Returns configured resolver class names.
   *
   * @return resolvers list
   */
  public @Nonnull List<String> getResolvers() {
    return unmodifiableListOrEmpty(resolvers);
  }

  /**
   * Replaces migration resolver class names.
   *
   * @param resolvers class names
   */
  public void setResolvers(@Nonnull List<String> resolvers) {
    this.resolvers = sanitizeList(resolvers);
  }

  /**
   * Returns resolver behaviour override.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getSkipDefaultResolvers() {
    return skipDefaultResolvers;
  }

  /**
   * Enables or disables default Flyway resolvers.
   *
   * @param skipDefaultResolvers whether defaults should be skipped
   */
  public void setSkipDefaultResolvers(@Nullable Boolean skipDefaultResolvers) {
    this.skipDefaultResolvers = skipDefaultResolvers;
  }

  /**
   * Returns SQL migration prefix.
   *
   * @return prefix or {@code null}
   */
  public @Nullable String getSqlMigrationPrefix() {
    return sqlMigrationPrefix;
  }

  /**
   * Sets SQL migration filename prefix.
   *
   * @param sqlMigrationPrefix prefix string
   */
  public void setSqlMigrationPrefix(@Nullable String sqlMigrationPrefix) {
    this.sqlMigrationPrefix = sqlMigrationPrefix;
  }

  /**
   * Returns undo migration prefix.
   *
   * @return prefix or {@code null}
   */
  public @Nullable String getUndoSqlMigrationPrefix() {
    return undoSqlMigrationPrefix;
  }

  /**
   * Sets undo SQL migration filename prefix.
   *
   * @param undoSqlMigrationPrefix prefix string
   */
  public void setUndoSqlMigrationPrefix(@Nullable String undoSqlMigrationPrefix) {
    this.undoSqlMigrationPrefix = undoSqlMigrationPrefix;
  }

  /**
   * Returns repeatable migration prefix.
   *
   * @return prefix or {@code null}
   */
  public @Nullable String getRepeatableSqlMigrationPrefix() {
    return repeatableSqlMigrationPrefix;
  }

  /**
   * Sets repeatable SQL migration filename prefix.
   *
   * @param repeatableSqlMigrationPrefix prefix string
   */
  public void setRepeatableSqlMigrationPrefix(@Nullable String repeatableSqlMigrationPrefix) {
    this.repeatableSqlMigrationPrefix = repeatableSqlMigrationPrefix;
  }

  /**
   * Returns SQL migration separator.
   *
   * @return separator or {@code null}
   */
  public @Nullable String getSqlMigrationSeparator() {
    return sqlMigrationSeparator;
  }

  /**
   * Sets SQL migration filename separator.
   *
   * @param sqlMigrationSeparator separator string
   */
  public void setSqlMigrationSeparator(@Nullable String sqlMigrationSeparator) {
    this.sqlMigrationSeparator = sqlMigrationSeparator;
  }

  /**
   * Sets SQL migration suffixes via var-args.
   *
   * @param suffixes suffix entries
   */
  public void sqlMigrationSuffixes(String... suffixes) {
    setSqlMigrationSuffixes(Arrays.asList(suffixes));
  }

  /**
   * Returns SQL migration suffixes.
   *
   * @return suffix list
   */
  public @Nonnull List<String> getSqlMigrationSuffixes() {
    return unmodifiableListOrEmpty(sqlMigrationSuffixes);
  }

  /**
   * Replaces SQL migration suffixes.
   *
   * @param suffixes suffix list
   */
  public void setSqlMigrationSuffixes(@Nonnull List<String> suffixes) {
    this.sqlMigrationSuffixes = sanitizeList(suffixes);
  }

  /**
   * Returns source script encoding or {@code null} when Flyway should use its default.
   *
   * @return encoding or {@code null}
   */
  public @Nullable String getEncoding() {
    return encoding;
  }

  /**
   * Sets source script encoding (e.g. {@code UTF-8}).
   *
   * @param encoding encoding name
   */
  public void setEncoding(@Nullable String encoding) {
    this.encoding = encoding;
  }

  /**
   * Returns encoding detection toggle or {@code null} to defer to Flyway defaults.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getDetectEncoding() {
    return detectEncoding;
  }

  /**
   * Enables encoding detection.
   *
   * @param detectEncoding toggle
   */
  public void setDetectEncoding(@Nullable Boolean detectEncoding) {
    this.detectEncoding = detectEncoding;
  }

  /**
   * Returns lock retry count or {@code null} when the default should be used.
   *
   * @return retry count or {@code null}
   */
  public @Nullable Integer getLockRetryCount() {
    return lockRetryCount;
  }

  /**
   * Sets lock retry count.
   *
   * @param lockRetryCount retry count
   */
  public void setLockRetryCount(@Nullable Integer lockRetryCount) {
    this.lockRetryCount = lockRetryCount;
  }

  /**
   * Adds a single Flyway placeholder entry.
   *
   * @param name placeholder name
   * @param value placeholder value
   */
  public void placeholder(@Nonnull String name, @Nonnull String value) {
    ensurePlaceholders()
        .put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
  }

  /**
   * Returns the configured Flyway placeholders.
   *
   * @return configured placeholders map
   */
  public @Nonnull Map<String, String> getPlaceholders() {
    return unmodifiableMapOrEmpty(placeholders);
  }

  /**
   * Replaces Flyway placeholders map.
   *
   * @param placeholders placeholder values keyed by name
   */
  public void setPlaceholders(@Nonnull Map<String, String> placeholders) {
    this.placeholders = sanitizeMap(placeholders);
  }

  /**
   * Adds single JDBC property entry.
   *
   * @param key property name
   * @param value property value
   */
  public void jdbcProperty(@Nonnull String key, @Nonnull String value) {
    ensureJdbcProperties()
        .put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
  }

  /**
   * Returns JDBC properties map.
   *
   * @return property map
   */
  public @Nonnull Map<String, String> getJdbcProperties() {
    return unmodifiableMapOrEmpty(jdbcProperties);
  }

  /**
   * Replaces Flyway JDBC properties map.
   *
   * @param jdbcProperties JDBC properties
   */
  public void setJdbcProperties(@Nonnull Map<String, String> jdbcProperties) {
    this.jdbcProperties = sanitizeMap(jdbcProperties);
  }

  /**
   * Returns placeholder replacement flag.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getPlaceholderReplacement() {
    return placeholderReplacement;
  }

  /**
   * Enables or disables placeholder replacement.
   *
   * @param placeholderReplacement toggle
   */
  public void setPlaceholderReplacement(@Nullable Boolean placeholderReplacement) {
    this.placeholderReplacement = placeholderReplacement;
  }

  /**
   * Returns placeholder prefix.
   *
   * @return prefix or {@code null}
   */
  public @Nullable String getPlaceholderPrefix() {
    return placeholderPrefix;
  }

  /**
   * Sets placeholder prefix.
   *
   * @param placeholderPrefix placeholder prefix
   */
  public void setPlaceholderPrefix(@Nullable String placeholderPrefix) {
    this.placeholderPrefix = placeholderPrefix;
  }

  /**
   * Returns placeholder suffix.
   *
   * @return suffix or {@code null}
   */
  public @Nullable String getPlaceholderSuffix() {
    return placeholderSuffix;
  }

  /**
   * Sets placeholder suffix.
   *
   * @param placeholderSuffix placeholder suffix
   */
  public void setPlaceholderSuffix(@Nullable String placeholderSuffix) {
    this.placeholderSuffix = placeholderSuffix;
  }

  /**
   * Returns placeholder separator.
   *
   * @return separator or {@code null}
   */
  public @Nullable String getPlaceholderSeparator() {
    return placeholderSeparator;
  }

  /**
   * Sets placeholder separator.
   *
   * @param placeholderSeparator separator value
   */
  public void setPlaceholderSeparator(@Nullable String placeholderSeparator) {
    this.placeholderSeparator = placeholderSeparator;
  }

  /**
   * Returns script placeholder prefix.
   *
   * @return prefix or {@code null}
   */
  public @Nullable String getScriptPlaceholderPrefix() {
    return scriptPlaceholderPrefix;
  }

  /**
   * Sets script placeholder prefix.
   *
   * @param scriptPlaceholderPrefix prefix value
   */
  public void setScriptPlaceholderPrefix(@Nullable String scriptPlaceholderPrefix) {
    this.scriptPlaceholderPrefix = scriptPlaceholderPrefix;
  }

  /**
   * Returns script placeholder suffix.
   *
   * @return suffix or {@code null}
   */
  public @Nullable String getScriptPlaceholderSuffix() {
    return scriptPlaceholderSuffix;
  }

  /**
   * Sets script placeholder suffix.
   *
   * @param scriptPlaceholderSuffix suffix value
   */
  public void setScriptPlaceholderSuffix(@Nullable String scriptPlaceholderSuffix) {
    this.scriptPlaceholderSuffix = scriptPlaceholderSuffix;
  }

  /**
   * Returns migration target version.
   *
   * @return target or {@code null}
   */
  public @Nullable String getTarget() {
    return target;
  }

  /**
   * Sets the target version for migration.
   *
   * @param target migration version string
   */
  public void setTarget(@Nullable String target) {
    this.target = target;
  }

  /**
   * Sets cherry-pick entries via var-args.
   *
   * @param cherryPick migration identifiers
   */
  public void cherryPick(String... cherryPick) {
    setCherryPick(Arrays.asList(cherryPick));
  }

  /**
   * Returns cherry-pick migration list.
   *
   * @return cherry-pick list
   */
  public @Nonnull List<String> getCherryPick() {
    return unmodifiableListOrEmpty(cherryPick);
  }

  /**
   * Replaces cherry-pick migration list.
   *
   * @param cherryPick migration identifiers
   */
  public void setCherryPick(@Nonnull List<String> cherryPick) {
    this.cherryPick = sanitizeList(cherryPick);
  }

  /**
   * Sets loggers using var-args.
   *
   * @param loggers logger identifiers
   */
  public void loggers(String... loggers) {
    setLoggers(Arrays.asList(loggers));
  }

  /**
   * Returns logger configuration.
   *
   * @return logger list
   */
  public @Nonnull List<String> getLoggers() {
    return unmodifiableListOrEmpty(loggers);
  }

  /**
   * Replaces logger configuration.
   *
   * @param loggers logger names
   */
  public void setLoggers(@Nonnull List<String> loggers) {
    this.loggers = sanitizeList(loggers);
  }

  /**
   * Sets callbacks using var-args.
   *
   * @param callbacks callback class names
   */
  public void callbacks(String... callbacks) {
    setCallbacks(Arrays.asList(callbacks));
  }

  /**
   * Returns callback configuration.
   *
   * @return callbacks list
   */
  public @Nonnull List<String> getCallbacks() {
    return unmodifiableListOrEmpty(callbacks);
  }

  /**
   * Replaces callback configuration.
   *
   * @param callbacks callback class names
   */
  public void setCallbacks(@Nonnull List<String> callbacks) {
    this.callbacks = sanitizeList(callbacks);
  }

  /**
   * Returns default callback behaviour.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getSkipDefaultCallbacks() {
    return skipDefaultCallbacks;
  }

  /**
   * Enables or disables default callbacks.
   *
   * @param skipDefaultCallbacks toggle
   */
  public void setSkipDefaultCallbacks(@Nullable Boolean skipDefaultCallbacks) {
    this.skipDefaultCallbacks = skipDefaultCallbacks;
  }

  /**
   * Returns out-of-order behaviour.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getOutOfOrder() {
    return outOfOrder;
  }

  /**
   * Enables out-of-order migration execution.
   *
   * @param outOfOrder toggle
   */
  public void setOutOfOrder(@Nullable Boolean outOfOrder) {
    this.outOfOrder = outOfOrder;
  }

  /**
   * Returns skip-executing behaviour.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getSkipExecutingMigrations() {
    return skipExecutingMigrations;
  }

  /**
   * Enables skipping actual migration execution.
   *
   * @param skipExecutingMigrations toggle
   */
  public void setSkipExecutingMigrations(@Nullable Boolean skipExecutingMigrations) {
    this.skipExecutingMigrations = skipExecutingMigrations;
  }

  /**
   * Returns query output toggle.
   *
   * @return query output toggle
   */
  public @Nullable Boolean getOutputQueryResults() {
    return outputQueryResults;
  }

  /**
   * Enables migration query output.
   *
   * @param outputQueryResults toggle
   */
  public void setOutputQueryResults(@Nullable Boolean outputQueryResults) {
    this.outputQueryResults = outputQueryResults;
  }

  /**
   * Returns validation toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getValidateOnMigrate() {
    return validateOnMigrate;
  }

  /**
   * Enables migration validation on migrate.
   *
   * @param validateOnMigrate toggle
   */
  public void setValidateOnMigrate(@Nullable Boolean validateOnMigrate) {
    this.validateOnMigrate = validateOnMigrate;
  }

  /**
   * Returns clean-on-validation toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getCleanOnValidationError() {
    return cleanOnValidationError;
  }

  /**
   * Enables cleaning when validation fails.
   *
   * @param cleanOnValidationError toggle
   */
  public void setCleanOnValidationError(@Nullable Boolean cleanOnValidationError) {
    this.cleanOnValidationError = cleanOnValidationError;
  }

  /**
   * Sets ignore migration patterns via var-args.
   *
   * @param ignoreMigrationPatterns patterns to ignore
   */
  public void ignoreMigrationPatterns(String... ignoreMigrationPatterns) {
    setIgnoreMigrationPatterns(Arrays.asList(ignoreMigrationPatterns));
  }

  /**
   * Returns ignore migration patterns.
   *
   * @return pattern list
   */
  public @Nonnull List<String> getIgnoreMigrationPatterns() {
    return unmodifiableListOrEmpty(ignoreMigrationPatterns);
  }

  /**
   * Replaces ignore migration patterns.
   *
   * @param ignoreMigrationPatterns patterns to ignore
   */
  public void setIgnoreMigrationPatterns(@Nonnull List<String> ignoreMigrationPatterns) {
    this.ignoreMigrationPatterns = sanitizeList(ignoreMigrationPatterns);
  }

  /**
   * Returns migration naming validation toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getValidateMigrationNaming() {
    return validateMigrationNaming;
  }

  /**
   * Enables migration naming validation.
   *
   * @param validateMigrationNaming toggle
   */
  public void setValidateMigrationNaming(@Nullable Boolean validateMigrationNaming) {
    this.validateMigrationNaming = validateMigrationNaming;
  }

  /**
   * Returns clean disabled flag.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getCleanDisabled() {
    return cleanDisabled;
  }

  /**
   * Disables the clean command.
   *
   * @param cleanDisabled toggle
   */
  public void setCleanDisabled(@Nullable Boolean cleanDisabled) {
    this.cleanDisabled = cleanDisabled;
  }

  /**
   * Returns baselining toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getBaselineOnMigrate() {
    return baselineOnMigrate;
  }

  /**
   * Enables baselining on migrate.
   *
   * @param baselineOnMigrate toggle
   */
  public void setBaselineOnMigrate(@Nullable Boolean baselineOnMigrate) {
    this.baselineOnMigrate = baselineOnMigrate;
  }

  /**
   * Returns mixed migration toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getMixed() {
    return mixed;
  }

  /**
   * Enables mixed migrations.
   *
   * @param mixed toggle
   */
  public void setMixed(@Nullable Boolean mixed) {
    this.mixed = mixed;
  }

  /**
   * Returns grouping toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getGroup() {
    return group;
  }

  /**
   * Enables grouping migrations in a single transaction.
   *
   * @param group toggle
   */
  public void setGroup(@Nullable Boolean group) {
    this.group = group;
  }

  /**
   * Returns installer identifier.
   *
   * @return identifier or {@code null}
   */
  public @Nullable String getInstalledBy() {
    return installedBy;
  }

  /**
   * Sets the user that installed migrations.
   *
   * @param installedBy installer identifier
   */
  public void setInstalledBy(@Nullable String installedBy) {
    this.installedBy = installedBy;
  }

  /**
   * Sets error overrides using var-args.
   *
   * @param errorOverrides overrides
   */
  public void errorOverrides(String... errorOverrides) {
    setErrorOverrides(Arrays.asList(errorOverrides));
  }

  /**
   * Returns error overrides.
   *
   * @return overrides list
   */
  public @Nonnull List<String> getErrorOverrides() {
    return unmodifiableListOrEmpty(errorOverrides);
  }

  /**
   * Replaces error override configuration.
   *
   * @param errorOverrides error overrides
   */
  public void setErrorOverrides(@Nonnull List<String> errorOverrides) {
    this.errorOverrides = sanitizeList(errorOverrides);
  }

  /**
   * Returns dry run output destination.
   *
   * @return path or {@code null}
   */
  public @Nullable String getDryRunOutput() {
    return dryRunOutput;
  }

  /**
   * Sets dry run output destination.
   *
   * @param dryRunOutput output path
   */
  public void setDryRunOutput(@Nullable String dryRunOutput) {
    this.dryRunOutput = dryRunOutput;
  }

  /**
   * Returns streaming flag.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getStream() {
    return stream;
  }

  /**
   * Enables streaming migrations.
   *
   * @param stream toggle
   */
  public void setStream(@Nullable Boolean stream) {
    this.stream = stream;
  }

  /**
   * Returns batching flag.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getBatch() {
    return batch;
  }

  /**
   * Enables batching migrations.
   *
   * @param batch toggle
   */
  public void setBatch(@Nullable Boolean batch) {
    this.batch = batch;
  }

  /**
   * Returns Oracle SQL*Plus toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getOracleSqlplus() {
    return oracleSqlplus;
  }

  /**
   * Enables Oracle SQL*Plus compatibility.
   *
   * @param oracleSqlplus toggle
   */
  public void setOracleSqlplus(@Nullable Boolean oracleSqlplus) {
    this.oracleSqlplus = oracleSqlplus;
  }

  /**
   * Returns Oracle SQL*Plus warnings toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getOracleSqlplusWarn() {
    return oracleSqlplusWarn;
  }

  /**
   * Enables Oracle SQL*Plus warnings.
   *
   * @param oracleSqlplusWarn toggle
   */
  public void setOracleSqlplusWarn(@Nullable Boolean oracleSqlplusWarn) {
    this.oracleSqlplusWarn = oracleSqlplusWarn;
  }

  /**
   * Returns Oracle wallet location.
   *
   * @return location or {@code null}
   */
  public @Nullable String getOracleWalletLocation() {
    return oracleWalletLocation;
  }

  /**
   * Sets Oracle wallet location.
   *
   * @param oracleWalletLocation wallet path
   */
  public void setOracleWalletLocation(@Nullable String oracleWalletLocation) {
    this.oracleWalletLocation = oracleWalletLocation;
  }

  /**
   * Returns Kerberos configuration file path.
   *
   * @return path or {@code null}
   */
  public @Nullable String getKerberosConfigFile() {
    return kerberosConfigFile;
  }

  /**
   * Sets Kerberos configuration file path.
   *
   * @param kerberosConfigFile configuration path
   */
  public void setKerberosConfigFile(@Nullable String kerberosConfigFile) {
    this.kerberosConfigFile = kerberosConfigFile;
  }

  /**
   * Returns configured Flyway license key.
   *
   * @return license key or {@code null}
   */
  public @Nullable String getLicenseKey() {
    return licenseKey;
  }

  /**
   * Sets Flyway license key.
   *
   * @param licenseKey license key
   */
  public void setLicenseKey(@Nullable String licenseKey) {
    this.licenseKey = licenseKey;
  }

  /**
   * Returns configuration file encoding.
   *
   * @return encoding or {@code null}
   */
  public @Nullable String getConfigFileEncoding() {
    return configFileEncoding;
  }

  /**
   * Sets configuration file encoding.
   *
   * @param configFileEncoding encoding value
   */
  public void setConfigFileEncoding(@Nullable String configFileEncoding) {
    this.configFileEncoding = configFileEncoding;
  }

  /**
   * Sets configuration files using var-args.
   *
   * @param configFiles config file paths
   */
  public void configFiles(String... configFiles) {
    setConfigFiles(Arrays.asList(configFiles));
  }

  /**
   * Returns configuration files.
   *
   * @return config file list
   */
  public @Nonnull List<String> getConfigFiles() {
    return unmodifiableListOrEmpty(configFiles);
  }

  /**
   * Replaces additional configuration files.
   *
   * @param configFiles config file paths
   */
  public void setConfigFiles(@Nonnull List<String> configFiles) {
    this.configFiles = sanitizeList(configFiles);
  }

  /**
   * Returns working directory.
   *
   * @return working directory or {@code null}
   */
  public @Nullable String getWorkingDirectory() {
    return workingDirectory;
  }

  /**
   * Sets working directory used by Flyway.
   *
   * @param workingDirectory working directory
   */
  public void setWorkingDirectory(@Nullable String workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  /**
   * Returns schema creation toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getCreateSchemas() {
    return createSchemas;
  }

  /**
   * Enables schema creation.
   *
   * @param createSchemas toggle
   */
  public void setCreateSchemas(@Nullable Boolean createSchemas) {
    this.createSchemas = createSchemas;
  }

  /**
   * Returns fail-on-missing-locations toggle.
   *
   * @return {@code Boolean} flag or {@code null}
   */
  public @Nullable Boolean getFailOnMissingLocations() {
    return failOnMissingLocations;
  }

  /**
   * Enables failure when configured locations are missing.
   *
   * @param failOnMissingLocations toggle
   */
  public void setFailOnMissingLocations(@Nullable Boolean failOnMissingLocations) {
    this.failOnMissingLocations = failOnMissingLocations;
  }

  /**
   * Adds a single plugin configuration entry.
   *
   * @param key property name
   * @param value property value
   */
  public void pluginConfiguration(@Nonnull String key, @Nonnull String value) {
    ensurePluginConfiguration()
        .put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
  }

  /**
   * Returns plugin configuration overrides.
   *
   * @return configuration map
   */
  public @Nonnull Map<String, String> getPluginConfiguration() {
    return unmodifiableMapOrEmpty(pluginConfiguration);
  }

  /**
   * Replaces plugin configuration overrides.
   *
   * @param pluginConfiguration property overrides
   */
  public void setPluginConfiguration(@Nonnull Map<String, String> pluginConfiguration) {
    this.pluginConfiguration = sanitizeMap(pluginConfiguration);
  }

  /**
   * Lazily initialises the placeholders map when it has not been created yet.
   *
   * @return mutable map of placeholder substitutions
   */
  private Map<String, String> ensurePlaceholders() {
    if (placeholders == null) {
      placeholders = new LinkedHashMap<>();
    }
    return placeholders;
  }

  /**
   * Lazily initialises the JDBC properties map when it has not been created yet.
   *
   * @return mutable map of JDBC properties
   */
  private Map<String, String> ensureJdbcProperties() {
    if (jdbcProperties == null) {
      jdbcProperties = new LinkedHashMap<>();
    }
    return jdbcProperties;
  }

  /**
   * Lazily initialises the plugin configuration map when it has not been created yet.
   *
   * @return mutable map of plugin configuration entries
   */
  private Map<String, String> ensurePluginConfiguration() {
    if (pluginConfiguration == null) {
      pluginConfiguration = new LinkedHashMap<>();
    }
    return pluginConfiguration;
  }
}
