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
import io.github.suppierk.codegen.extensions.DatabaseExtension;
import io.github.suppierk.codegen.util.Strings;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import nu.studer.gradle.jooq.JooqConfig;
import nu.studer.gradle.jooq.JooqGenerate;
import nu.studer.gradle.jooq.JooqPlugin;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle task that orchestrates Testcontainers database startup, Flyway migrations, and jOOQ code
 * generation for a single configuration.
 *
 * <p>The task temporarily rewires the {@link JooqConfig} JDBC configuration to point at the managed
 * container, runs Flyway within the same class loader that resolves user migrations, and finally
 * delegates to the original {@link JooqGenerate} task.
 *
 * @see GeneratorPlugin
 * @see FlywayMigrateTask
 */
@CacheableTask
public class GeneratorTask extends AbstractDatabaseTask {
  private static final String GENERATOR_GROUP_NAME = "codegen";

  private static final String GENERATOR_TASK_NAME_TEMPLATE = "generate%sDatabaseClasses";

  private static final String JOOQ_GENERATOR_TASK_NAME_TEMPLATE = "generate%sJooq";
  private static final String JOOQ_GENERATOR_MAIN_TASK_NAME = "main";
  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final String FILESYSTEM_PREFIX = "filesystem:";
  private static final Logger LOGGER = Logging.getLogger(GeneratorTask.class);
  private static final JooqConfigNameAccessor JOOQ_NAME_ACCESSOR = JooqConfigNameAccessor.detect();

  @Nonnull private final TaskProvider<JooqGenerate> jooqGenerateTask;
  @Nonnull private final DirectoryProperty outputDirectory;
  @Nonnull private final ConfigurableFileCollection migrationInputs;
  @Nonnull private final ConfigurableFileCollection jooqRuntimeClasspath;

  /**
   * Creates a new task instance bound to a specific combination of database, Flyway configuration
   * and jOOQ configuration.
   *
   * @param databaseExtension database specification coming from the {@code jooqCodegen} DSL
   * @param flywayConfig Flyway configuration that should be applied to this schema
   * @param jooqConfig original jOOQ configuration
   * @param jooqGenerateTask provider for the underlying {@link JooqGenerate} task
   */
  @Inject
  public GeneratorTask(
      @Nonnull DatabaseExtension databaseExtension,
      @Nonnull FlywayConfig flywayConfig,
      @Nonnull JooqConfig jooqConfig,
      @Nonnull TaskProvider<JooqGenerate> jooqGenerateTask) {
    super(databaseExtension, flywayConfig, jooqConfig);

    setGroup(GENERATOR_GROUP_NAME);

    this.jooqGenerateTask = jooqGenerateTask;
    this.outputDirectory = getProject().getObjects().directoryProperty();
    this.outputDirectory.set(jooqGenerateTask.flatMap(JooqGenerate::getOutputDir));
    this.migrationInputs = getProject().getObjects().fileCollection();
    this.jooqRuntimeClasspath = getProject().getObjects().fileCollection();
  }

  /**
   * Computes the Gradle task name used to expose this generator task for a given jOOQ
   * configuration.
   *
   * @param jooqConfig configuration to inspect
   * @return Gradle task name (e.g. {@code generateMainDatabaseClasses})
   * @see #assumeJooqGenerateTaskName(JooqConfig)
   */
  public static @Nonnull String createTaskName(@Nonnull JooqConfig jooqConfig)
      throws IllegalStateException {
    final var capitalizedName = Strings.capitalizeFirstLetter(getJooqConfigName(jooqConfig));
    if (capitalizedName.isBlank()) {
      throw new IllegalStateException("jOOQ configuration name must not be blank");
    }
    return String.format(GENERATOR_TASK_NAME_TEMPLATE, capitalizedName);
  }

  /**
   * Derives the original {@link JooqGenerate} task name for the supplied configuration, following
   * the same conventions as {@link JooqPlugin}.
   *
   * @param jooqConfig configuration to inspect
   * @return jOOQ task name (e.g. {@code generateMainJooq})
   */
  public static @Nonnull String assumeJooqGenerateTaskName(@Nonnull JooqConfig jooqConfig)
      throws IllegalStateException {
    final var name = getJooqConfigName(jooqConfig);
    return JOOQ_GENERATOR_MAIN_TASK_NAME.equalsIgnoreCase(name)
        ? String.format(JOOQ_GENERATOR_TASK_NAME_TEMPLATE, Strings.EMPTY)
        : String.format(JOOQ_GENERATOR_TASK_NAME_TEMPLATE, Strings.capitalizeFirstLetter(name));
  }

  /**
   * Resolves the internal name of a {@link JooqConfig}. The gradle-jooq plugin does not expose the
   * name via public API, so we fall back to reflection or the {@link Named} interface depending on
   * the plugin version.
   *
   * @param jooqConfig configuration whose name we need
   * @return configuration name (never blank)
   */
  @SuppressWarnings("squid:S3011")
  static @Nonnull String getJooqConfigName(@Nonnull JooqConfig jooqConfig)
      throws IllegalStateException {
    return JOOQ_NAME_ACCESSOR.getName(jooqConfig);
  }

  /**
   * Task action that:
   *
   * <ol>
   *   <li>Launches the requested Testcontainers database
   *   <li>Runs Flyway migrations against the container
   *   <li>Temporarily rewires the {@link JooqConfig} JDBC properties to point at the container
   *   <li>Executes the original {@link JooqGenerate} task
   *   <li>Restores the jOOQ configuration
   * </ol>
   *
   * @see #withDatabase(String, DatabaseAction)
   * @see
   *     AbstractDatabaseTask#runFlywayMigrations(io.github.suppierk.codegen.docker.AbstractDatabaseContainer,
   *     String, java.net.URLClassLoader)
   */
  @TaskAction
  public void run() {
    final var jdbcConfiguration = jooqConfig.getJooqConfiguration().getJdbc();
    final var generatorDatabase = jooqConfig.getJooqConfiguration().getGenerator().getDatabase();

    final var originalDriver = jdbcConfiguration.getDriver();
    final var originalUrl = jdbcConfiguration.getUrl();
    final var originalUser = jdbcConfiguration.getUser();
    final var originalPassword = jdbcConfiguration.getPassword();
    final var originalSchema = generatorDatabase.getInputSchema();
    final var originalExcludes = generatorDatabase.getExcludes();

    final var dbDriverClassName = resolveDriver(originalDriver);
    if (dbDriverClassName.isBlank()) {
      throw new IllegalStateException(
          "Database driver class must be provided to run Flyway migrations and jOOQ generation");
    }

    final var dbSchema = resolveSchema(originalSchema);
    final List<String> effectiveExcludes = new ArrayList<>();
    if (originalExcludes != null && !originalExcludes.isBlank()) {
      effectiveExcludes.add(originalExcludes);
    }
    effectiveExcludes.add(resolveFlywayTableExclusionPattern());
    final var excludes = String.join("|", effectiveExcludes);

    try {
      withDatabase(
          dbDriverClassName,
          (database, classLoader) -> {
            runFlywayMigrations(database, dbSchema, classLoader);

            jdbcConfiguration.setDriver(database.getDriverClassName());
            jdbcConfiguration.setUrl(database.getJdbcUrl());
            jdbcConfiguration.setUser(database.getUsername());
            jdbcConfiguration.setPassword(database.getPassword());
            generatorDatabase.setInputSchema(dbSchema);
            generatorDatabase.setExcludes(excludes);

            jooqGenerateTask.get().generate();
          });
    } finally {
      jdbcConfiguration.setDriver(originalDriver);
      jdbcConfiguration.setUrl(originalUrl);
      jdbcConfiguration.setUser(originalUser);
      jdbcConfiguration.setPassword(originalPassword);
      generatorDatabase.setInputSchema(originalSchema);
      generatorDatabase.setExcludes(originalExcludes);
    }
  }

  /**
   * Returns the directory Gradle should treat as build output for the generated sources.
   *
   * @return directory containing generated jOOQ sources
   */
  @OutputDirectory
  public @Nonnull DirectoryProperty getOutputDirectory() {
    return outputDirectory;
  }

  /**
   * Returns the Flyway migration locations the task uses to decide whether regeneration is
   * required.
   *
   * @return collection of resolved migration directories
   */
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public @Nonnull ConfigurableFileCollection getMigrationInputs() {
    return migrationInputs;
  }

  /**
   * Classpath Gradle should fingerprint for jOOQ runtime changes.
   *
   * @return file collection with jOOQ runtime dependencies
   */
  @Classpath
  public @Nonnull ConfigurableFileCollection getJooqRuntimeClasspath() {
    return jooqRuntimeClasspath;
  }

  /**
   * Configures task inputs and outputs to make Gradle caching effective. Captures DSL derived data,
   * Flyway fingerprints, normalized jOOQ configuration hashes, and the migration locations so that
   * Gradle can determine when regeneration is necessary.
   *
   * @see #configureJooqClasspathInput()
   * @see #updateMigrationInputs()
   */
  public void configureCaching() {
    final var jdbcConfiguration = jooqConfig.getJooqConfiguration().getJdbc();
    final var generatorDatabase = jooqConfig.getJooqConfiguration().getGenerator().getDatabase();
    final String resolvedDriver = resolveDriver(jdbcConfiguration.getDriver());
    getInputs().property("databaseName", this.databaseName == null ? "" : this.databaseName);
    getInputs().property("schemaName", this.schemaName == null ? "" : this.schemaName);
    getInputs()
        .property(
            "driverClassName",
            this.overrideDriverClassName == null ? "" : this.overrideDriverClassName);
    getInputs().property("containerImage", determineContainerImageFingerprint(resolvedDriver));
    getInputs()
        .property(
            "flywayConfigFingerprint",
            computeFlywayFingerprint(generatorDatabase.getInputSchema()));
    getInputs()
        .property(
            "jooqConfigHash",
            jooqGenerateTask.map(JooqGenerate::getNormalizedJooqConfigurationHash));
    configureJooqClasspathInput();

    updateMigrationInputs();
  }

  /**
   * Adds the jOOQ runtime and generator classpaths to the task inputs so that Gradle cache
   * fingerprints reflect the binaries used during code generation.
   *
   * @see #configureCaching()
   */
  private void configureJooqClasspathInput() {
    final var pluginDependencies = getProject().getConfigurations().findByName("pluginClasspath");
    if (pluginDependencies != null) {
      jooqRuntimeClasspath.from(pluginDependencies);
    }
    jooqRuntimeClasspath.from(
        (Callable<Object>) () -> jooqGenerateTask.get().getRuntimeClasspath());
  }

  /**
   * Refreshes the file collection that Gradle should treat as Flyway migration inputs. The inputs
   * include resources from the project itself as well as external classpath entries referenced via
   * {@code classpath:} locations.
   *
   * @see #getMigrationInputs()
   */
  private void updateMigrationInputs() {
    migrationInputs.setFrom(
        (Callable<Iterable<File>>)
            () -> {
              final Set<File> resolvedLocations = new LinkedHashSet<>();
              for (String location : resolveEffectiveFlywayLocations()) {
                resolvedLocations.addAll(resolveMigrationLocations(location));
              }
              return resolvedLocations;
            });
  }

  /**
   * Resolves the Flyway locations that should be inspected for migrations, falling back to the
   * default directory when none are declared.
   *
   * @return immutable list of configured locations
   */
  private @Nonnull List<String> resolveEffectiveFlywayLocations() {
    final List<String> locations = resolveList(FlywayConfig::getLocations);
    return locations.isEmpty() ? List.of(FALLBACK_FLYWAY_LOCATION) : List.copyOf(locations);
  }

  private @Nonnull String determineContainerImageFingerprint(@Nullable String driverClassName) {
    if (driverClassName == null || driverClassName.isBlank()) {
      return "";
    }
    try {
      return databaseExtension.determineContainerImage(driverClassName).trim();
    } catch (UnsupportedOperationException e) {
      getLogger()
          .warn(
              "Unable to determine container image for driver '{}' while configuring caching: {}",
              driverClassName,
              e.getMessage());
      return "";
    }
  }

  /** Returns the regex pattern that excludes the effective Flyway metadata table. */
  private @Nonnull String resolveFlywayTableExclusionPattern() {
    final String configuredTable =
        Strings.firstNonBlank(
            resolveString(FlywayConfig::getTable), DefaultFlywaySettings.SCHEMA_HISTORY_TABLE);
    final String normalized = configuredTable.trim();
    final String tableName =
        normalized.isEmpty() ? DefaultFlywaySettings.SCHEMA_HISTORY_TABLE : normalized;
    final String escaped = Pattern.quote(tableName);
    return "(?i)(?:^|.*\\.)" + escaped + "$";
  }

  /**
   * Resolves a Flyway location token to one or more {@link File} handles on disk.
   *
   * @param rawLocation Flyway location value (classpath or filesystem)
   * @return set of files contributing to the location, or an empty set when it cannot be resolved
   */
  private @Nonnull Set<File> resolveMigrationLocations(@Nonnull String rawLocation) {
    final String location = rawLocation.trim();
    if (location.isEmpty()) {
      return Collections.emptySet();
    }

    if (location.startsWith(CLASSPATH_PREFIX)) {
      final String relative = trimLeadingSeparators(location.substring(CLASSPATH_PREFIX.length()));
      return resolveClasspathLocations(relative);
    }

    if (location.startsWith(FILESYSTEM_PREFIX)) {
      final String path = location.substring(FILESYSTEM_PREFIX.length());
      return resolveFilesystemLocation(path);
    }

    final Set<File> resolved = new LinkedHashSet<>();
    resolved.add(getProject().file(location));
    return resolved;
  }

  private @Nonnull Set<File> resolveClasspathLocations(@Nonnull String relativePath) {
    final Set<File> resolved = new LinkedHashSet<>();
    final List<File> resourceRoots = locateClasspathResourceRoots(getProject());
    if (!resourceRoots.isEmpty()) {
      for (File resourcesDir : resourceRoots) {
        resolved.add(resolveRelativeResource(resourcesDir, relativePath));
      }
    } else {
      for (File fallbackRoot : defaultResourceRoots(getProject())) {
        resolved.add(resolveRelativeResource(fallbackRoot, relativePath));
      }
    }
    resolved.addAll(resolveExternalClasspathLocations(relativePath));
    return resolved;
  }

  /** Returns classpath resource roots, including processed resources, for the main source set. */
  static @Nonnull List<File> locateClasspathResourceRoots(@Nonnull Project project) {
    final List<File> roots = new ArrayList<>();
    final JavaPluginExtension javaPluginExtension =
        project.getExtensions().findByType(JavaPluginExtension.class);
    if (javaPluginExtension != null) {
      final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
      final SourceSet main = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
      if (main != null) {
        roots.addAll(main.getResources().getSrcDirs());

        final File processedResources = main.getOutput().getResourcesDir();
        if (processedResources != null) {
          roots.add(processedResources);
        }
      }
    }
    return List.copyOf(roots);
  }

  /** Returns default resource directories to inspect when no source sets are configured. */
  static @Nonnull List<File> defaultResourceRoots(@Nonnull Project project) {
    final List<File> roots = new ArrayList<>(2);
    roots.add(project.file("src/main/resources"));
    roots.add(project.getLayout().getBuildDirectory().dir("resources/main").get().getAsFile());
    return List.copyOf(roots);
  }

  /** Returns classpath entries located outside the current project directory. */
  private @Nonnull Set<File> resolveExternalClasspathLocations(@Nonnull String relativePath) {
    final Set<File> resolved = new LinkedHashSet<>();
    final Path projectPath = getProject().getProjectDir().toPath().toAbsolutePath().normalize();
    final String jarEntryPath = toJarEntryPath(relativePath);
    for (File entry : collectFlywayClasspathEntries()) {
      if (!entry.exists() || !isExternalToProject(entry, projectPath)) {
        continue;
      }
      if (entry.isDirectory()) {
        final File candidate = resolveRelativeResource(entry, relativePath);
        if (candidate.exists()) {
          resolved.add(candidate);
        }
        continue;
      }
      if (isArchive(entry) && archiveContainsLocation(entry, jarEntryPath)) {
        resolved.add(entry);
      }
    }
    return resolved;
  }

  /** Returns {@code true} when the supplied entry resides outside the project directory. */
  private boolean isExternalToProject(@Nonnull File entry, @Nonnull Path projectPath) {
    final Path entryPath = entry.toPath().toAbsolutePath().normalize();
    return !entryPath.startsWith(projectPath);
  }

  private boolean isArchive(@Nonnull File entry) {
    final String name = entry.getName().toLowerCase(Locale.ROOT);
    return name.endsWith(".jar") || name.endsWith(".zip");
  }

  private boolean archiveContainsLocation(@Nonnull File archive, @Nonnull String jarEntryPath) {
    if (jarEntryPath.isEmpty()) {
      return true;
    }
    try (ZipFile zipFile = new ZipFile(archive)) {
      final String directoryPrefix = jarEntryPath.endsWith("/") ? jarEntryPath : jarEntryPath + "/";
      return zipFile.stream()
          .anyMatch(
              entry -> {
                final String name = entry.getName();
                return name.equals(jarEntryPath)
                    || name.equals(directoryPrefix)
                    || name.startsWith(directoryPrefix);
              });
    } catch (Exception e) {
      getLogger()
          .warn(
              "Unable to inspect classpath entry '{}' for Flyway location '{}': {}",
              archive,
              jarEntryPath,
              e.getMessage());
      return false;
    }
  }

  private @Nonnull String toJarEntryPath(@Nonnull String relativePath) {
    final String trimmed = trimLeadingSeparators(relativePath);
    return trimmed.replace('\\', '/');
  }

  private @Nonnull File resolveRelativeResource(
      @Nonnull File resourcesRoot, @Nonnull String relativePath) {
    if (relativePath.isEmpty()) {
      return resourcesRoot;
    }
    final String normalized = relativePath.replace('/', File.separatorChar);
    return new File(resourcesRoot, normalized);
  }

  /**
   * Removes leading file separators from the supplied path fragment.
   *
   * @param value candidate path fragment
   * @return fragment without leading separators
   */
  private @Nonnull String trimLeadingSeparators(@Nonnull String value) {
    var result = value;
    while (!result.isEmpty() && (result.charAt(0) == '/' || result.charAt(0) == '\\')) {
      result = result.substring(1);
    }
    return result;
  }

  /**
   * Resolves a filesystem location, supporting absolute and relative paths as defined by Gradle's
   * {@code Project.file(...)} semantics.
   */
  private @Nonnull Set<File> resolveFilesystemLocation(@Nonnull String rawPath) {
    final String trimmed = rawPath.trim();
    if (trimmed.isEmpty()) {
      return Collections.emptySet();
    }
    final File candidate = new File(trimmed);
    if (candidate.isAbsolute()) {
      return Set.of(candidate);
    }
    return Set.of(getProject().file(trimLeadingSeparators(trimmed)));
  }

  /**
   * Builds a deterministic string representing the effective Flyway configuration for caching
   * purposes.
   *
   * @param fallbackSchema schema declared on the jOOQ configuration
   * @return fingerprint string capturing the Flyway configuration state
   */
  private @Nonnull String computeFlywayFingerprint(@Nonnull String fallbackSchema) {
    final Map<String, Object> snapshot = new LinkedHashMap<>();
    final String resolvedSchema = resolveSchema(fallbackSchema);
    final String defaultSchema = resolveString(FlywayConfig::getDefaultSchema);
    final List<String> schemas = resolveList(FlywayConfig::getSchemas);
    final List<String> effectiveSchemas =
        schemas.isEmpty() && !resolvedSchema.isBlank()
            ? List.of(resolvedSchema)
            : List.copyOf(schemas);
    final List<String> locations = resolveEffectiveFlywayLocations();

    snapshot.put("resolvedSchema", resolvedSchema);
    snapshot.put("defaultSchema", defaultSchema == null ? "" : defaultSchema);
    snapshot.put("schemas", effectiveSchemas);
    snapshot.put("locations", locations);

    putList(snapshot, "callbackLocations", resolveList(FlywayConfig::getCallbackLocations));
    putInteger(snapshot, "connectRetries", resolveInteger(FlywayConfig::getConnectRetries));
    putInteger(
        snapshot,
        "connectRetriesInterval",
        resolveInteger(FlywayConfig::getConnectRetriesInterval));
    putString(snapshot, "initSql", resolveString(FlywayConfig::getInitSql));
    putString(snapshot, "table", resolveString(FlywayConfig::getTable));
    putString(snapshot, "tablespace", resolveString(FlywayConfig::getTablespace));
    putString(snapshot, "baselineVersion", resolveString(FlywayConfig::getBaselineVersion));
    putString(snapshot, "baselineDescription", resolveString(FlywayConfig::getBaselineDescription));
    putList(snapshot, "resolvers", resolveList(FlywayConfig::getResolvers));
    putBoolean(
        snapshot, "skipDefaultResolvers", resolveBoolean(FlywayConfig::getSkipDefaultResolvers));
    putString(snapshot, "sqlMigrationPrefix", resolveString(FlywayConfig::getSqlMigrationPrefix));
    putString(
        snapshot,
        "repeatableSqlMigrationPrefix",
        resolveString(FlywayConfig::getRepeatableSqlMigrationPrefix));
    putString(
        snapshot, "sqlMigrationSeparator", resolveString(FlywayConfig::getSqlMigrationSeparator));
    putList(snapshot, "sqlMigrationSuffixes", resolveList(FlywayConfig::getSqlMigrationSuffixes));
    putString(snapshot, "encoding", resolveString(FlywayConfig::getEncoding));
    putBoolean(snapshot, "detectEncoding", resolveBoolean(FlywayConfig::getDetectEncoding));
    putInteger(snapshot, "lockRetryCount", resolveInteger(FlywayConfig::getLockRetryCount));
    putMap(snapshot, "placeholders", resolveMap(FlywayConfig::getPlaceholders));
    putMap(snapshot, "jdbcProperties", resolveMap(FlywayConfig::getJdbcProperties));
    putBoolean(
        snapshot,
        "placeholderReplacement",
        resolveBoolean(FlywayConfig::getPlaceholderReplacement));
    putString(snapshot, "placeholderPrefix", resolveString(FlywayConfig::getPlaceholderPrefix));
    putString(snapshot, "placeholderSuffix", resolveString(FlywayConfig::getPlaceholderSuffix));
    putString(
        snapshot, "placeholderSeparator", resolveString(FlywayConfig::getPlaceholderSeparator));
    putString(
        snapshot,
        "scriptPlaceholderPrefix",
        resolveString(FlywayConfig::getScriptPlaceholderPrefix));
    putString(
        snapshot,
        "scriptPlaceholderSuffix",
        resolveString(FlywayConfig::getScriptPlaceholderSuffix));
    putString(snapshot, "target", resolveString(FlywayConfig::getTarget));
    putList(snapshot, "loggers", resolveList(FlywayConfig::getLoggers));
    putList(snapshot, "callbacks", resolveList(FlywayConfig::getCallbacks));
    putBoolean(
        snapshot, "skipDefaultCallbacks", resolveBoolean(FlywayConfig::getSkipDefaultCallbacks));
    putBoolean(snapshot, "outOfOrder", resolveBoolean(FlywayConfig::getOutOfOrder));
    putBoolean(
        snapshot,
        "skipExecutingMigrations",
        resolveBoolean(FlywayConfig::getSkipExecutingMigrations));
    putBoolean(snapshot, "outputQueryResults", resolveBoolean(FlywayConfig::getOutputQueryResults));
    putBoolean(snapshot, "validateOnMigrate", resolveBoolean(FlywayConfig::getValidateOnMigrate));
    putBoolean(
        snapshot,
        "cleanOnValidationError",
        resolveBoolean(FlywayConfig::getCleanOnValidationError));
    putList(
        snapshot, "ignoreMigrationPatterns", resolveList(FlywayConfig::getIgnoreMigrationPatterns));
    putBoolean(
        snapshot,
        "validateMigrationNaming",
        resolveBoolean(FlywayConfig::getValidateMigrationNaming));
    putBoolean(snapshot, "cleanDisabled", resolveBoolean(FlywayConfig::getCleanDisabled));
    putBoolean(snapshot, "baselineOnMigrate", resolveBoolean(FlywayConfig::getBaselineOnMigrate));
    putBoolean(snapshot, "mixed", resolveBoolean(FlywayConfig::getMixed));
    putBoolean(snapshot, "group", resolveBoolean(FlywayConfig::getGroup));
    putString(snapshot, "installedBy", resolveString(FlywayConfig::getInstalledBy));
    putList(snapshot, "errorOverrides", resolveList(FlywayConfig::getErrorOverrides));
    putString(snapshot, "dryRunOutput", resolveString(FlywayConfig::getDryRunOutput));
    putBoolean(snapshot, "stream", resolveBoolean(FlywayConfig::getStream));
    putBoolean(snapshot, "batch", resolveBoolean(FlywayConfig::getBatch));
    putBoolean(snapshot, "oracleSqlplus", resolveBoolean(FlywayConfig::getOracleSqlplus));
    putBoolean(snapshot, "oracleSqlplusWarn", resolveBoolean(FlywayConfig::getOracleSqlplusWarn));
    putString(
        snapshot, "oracleWalletLocation", resolveString(FlywayConfig::getOracleWalletLocation));
    putString(snapshot, "kerberosConfigFile", resolveString(FlywayConfig::getKerberosConfigFile));
    putString(snapshot, "licenseKey", resolveString(FlywayConfig::getLicenseKey));
    putString(snapshot, "configFileEncoding", resolveString(FlywayConfig::getConfigFileEncoding));
    putString(snapshot, "workingDirectory", resolveString(FlywayConfig::getWorkingDirectory));
    putBoolean(snapshot, "createSchemas", resolveBoolean(FlywayConfig::getCreateSchemas));
    putBoolean(
        snapshot,
        "failOnMissingLocations",
        resolveBoolean(FlywayConfig::getFailOnMissingLocations));
    putString(snapshot, "jdbcDriver", resolveString(FlywayConfig::getDriver));

    return serializeSnapshot(snapshot);
  }

  /** Adds a string entry to the fingerprint snapshot when the value is non-null. */
  private void putString(
      @Nonnull Map<String, Object> snapshot, @Nonnull String key, @Nullable String value) {
    if (value != null) {
      snapshot.put(key, value);
    }
  }

  /** Adds an integer entry to the fingerprint snapshot when the value is non-null. */
  private void putInteger(
      @Nonnull Map<String, Object> snapshot, @Nonnull String key, @Nullable Integer value) {
    if (value != null) {
      snapshot.put(key, value);
    }
  }

  /** Adds a boolean entry to the fingerprint snapshot when the value is non-null. */
  private void putBoolean(
      @Nonnull Map<String, Object> snapshot, @Nonnull String key, @Nullable Boolean value) {
    if (value != null) {
      snapshot.put(key, value);
    }
  }

  /** Adds a list entry to the fingerprint snapshot when the value is non-empty. */
  private void putList(
      @Nonnull Map<String, Object> snapshot, @Nonnull String key, @Nullable List<String> values) {
    if (values != null && !values.isEmpty()) {
      snapshot.put(key, List.copyOf(values));
    }
  }

  /** Adds a map entry to the fingerprint snapshot when the value is non-empty. */
  private void putMap(
      @Nonnull Map<String, Object> snapshot,
      @Nonnull String key,
      @Nullable Map<String, String> values) {
    if (values != null && !values.isEmpty()) {
      snapshot.put(key, new LinkedHashMap<>(values));
    }
  }

  /** Serialises the fingerprint snapshot into a stable delimiter-separated string. */
  private @Nonnull String serializeSnapshot(@Nonnull Map<String, Object> snapshot) {
    final StringBuilder builder = new StringBuilder();
    snapshot.forEach(
        (key, value) -> {
          if (!builder.isEmpty()) {
            builder.append('|');
          }
          builder.append(key).append('=').append(serializeValue(value));
        });
    return builder.toString();
  }

  /** Serialises an arbitrary value into a string, expanding collections and maps recursively. */
  private @Nonnull String serializeValue(@Nullable Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof List<?> list) {
      final List<String> converted = new ArrayList<>(list.size());
      for (Object entry : list) {
        converted.add(serializeValue(entry));
      }
      return "[" + String.join(",", converted) + "]";
    }
    if (value instanceof Map<?, ?> map) {
      final List<String> parts = new ArrayList<>(map.size());
      map.forEach((k, v) -> parts.add(serializeValue(k) + ":" + serializeValue(v)));
      return "{" + String.join(",", parts) + "}";
    }
    return Objects.toString(value);
  }

  /** Encapsulates reflective lookup of the jOOQ configuration name with graceful degradation. */
  private static final class JooqConfigNameAccessor {
    private final Method accessorMethod;
    private final Field accessorField;
    private final boolean supportsNamed;
    private final String jooqPluginVersion;

    private JooqConfigNameAccessor(
        @Nullable Method accessorMethod,
        @Nullable Field accessorField,
        boolean supportsNamed,
        @Nonnull String pluginVersion) {
      this.accessorMethod = accessorMethod;
      this.accessorField = accessorField;
      this.supportsNamed = supportsNamed;
      this.jooqPluginVersion = pluginVersion;
    }

    private static @Nonnull JooqConfigNameAccessor detect() {
      final boolean named = Named.class.isAssignableFrom(JooqConfig.class);
      final String pluginVersion = resolveJooqPluginVersion();

      try {
        final Method method = JooqConfig.class.getMethod("getName");
        if (String.class.equals(method.getReturnType())) {
          return new JooqConfigNameAccessor(method, null, named, pluginVersion);
        }
      } catch (NoSuchMethodException ignored) {
        // fall through to field lookup
      }

      try {
        final Field field = JooqConfig.class.getDeclaredField("name");
        if (!String.class.equals(field.getType())) {
          LOGGER.warn(
              "Encountered unexpected field type for jOOQ configuration name ({}). Falling back to alternative lookups.",
              field.getType().getName());
        } else {
          field.setAccessible(true);
          return new JooqConfigNameAccessor(null, field, named, pluginVersion);
        }
      } catch (NoSuchFieldException ignored) {
        // fall through to Named/support detection
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to access jOOQ configuration name field due to {}. Falling back to alternative lookups.",
            e.getMessage());
      }

      if (named) {
        LOGGER.info(
            "Using org.gradle.api.Named fallback to determine jOOQ configuration names. Detected gradle-jooq plugin version: {}",
            pluginVersion);
        return new JooqConfigNameAccessor(null, null, true, pluginVersion);
      }

      LOGGER.warn(
          "Unable to determine how to access jOOQ configuration names for gradle-jooq plugin version {}. Subsequent task registration will fail with an explanatory exception.",
          pluginVersion);
      return new JooqConfigNameAccessor(null, null, false, pluginVersion);
    }

    private static @Nonnull String resolveJooqPluginVersion() {
      final Package pkg = JooqPlugin.class.getPackage();
      final String version = pkg != null ? pkg.getImplementationVersion() : null;
      return version != null ? version : "unknown";
    }

    @Nonnull
    String getName(@Nonnull JooqConfig config) {
      try {
        if (accessorMethod != null) {
          return (String) accessorMethod.invoke(config);
        }
        if (accessorField != null) {
          return (String) accessorField.get(config);
        }
        if (supportsNamed && config instanceof Named namedConfig) {
          return namedConfig.getName();
        }
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to resolve jOOQ configuration name via inspected accessors. gradle-jooq plugin version: "
                + jooqPluginVersion,
            e);
      }

      throw new IllegalStateException(
          "Unable to resolve jOOQ configuration name using known accessors. gradle-jooq plugin version: "
              + jooqPluginVersion
              + ". Please report this incompatibility.");
    }
  }
}
