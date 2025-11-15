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

package io.github.suppierk.codegen

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import spock.util.io.FileSystemFixture

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class GeneratorPluginFunctionalTest extends Specification {
	private static final String POSTGRES_VERSION = '42.7.7'
	private static final String MYSQL_VERSION = '8.4.0'
	private static final String H2_VERSION = '2.2.224'
	private static final String JOOQ_VERSION = '3.20.5'

	@TempDir
	FileSystemFixture fileSystemFixture

	def "dsl project without migrations logs absence"() {
		given:
		fileSystemFixture.create {
			file('settings.gradle').text = "rootProject.name = 'test-project'"
			file('build.gradle').text = singleSchemaBuildScript()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		result.output.contains('No migrations found. Are your locations set up correctly?')
	}

	def "plugin configuration is reusable with Gradle configuration cache"() {
		given:
		fileSystemFixture.create {
			file('settings.gradle').text = "rootProject.name = 'configuration-cache'"
			file('build.gradle').text = singleSchemaBuildScript()
		}

		when:
		def firstRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('help', '--configuration-cache', '--stacktrace')
				.build()

		then:
		firstRun.output.contains('Configuration cache entry stored')

		when:
		def secondRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('help', '--configuration-cache', '--stacktrace')
				.build()

		then:
		secondRun.output.contains('Reusing configuration cache')
	}

	def "jooq generate tasks are not realized when codegen is not requested"() {
		given:
		fileSystemFixture.create {
			file('settings.gradle').text = "rootProject.name = 'lazy-jooq-task'"
			file('build.gradle').text = singleSchemaBuildScript() + '''

tasks.withType(nu.studer.gradle.jooq.JooqGenerate).configureEach { task ->
	if (task.name == 'generateMainJooq') {
		throw new GradleException('generateMainJooq should not be realized when running help')
	}
}
'''.stripIndent()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('help', '--stacktrace')
				.build()

		then:
		result.task(':help').outcome == SUCCESS
	}

	def "dsl generates jOOQ classes"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE  NOT NULL,
					email      VARCHAR(255) UNIQUE NOT NULL,
					created_at TIMESTAMP           NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"
			file('build.gradle').text = singleSchemaBuildScript()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "dsl honors custom Flyway sql migration prefix"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('X001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"
			file('build.gradle').text =
					singleSchemaBuildScript("sqlMigrationPrefix = 'X'")
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "dsl preserves custom excludes and filters Flyway metadata tables"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
				file('V002__create_metrics.sql').text = '''\
				CREATE TABLE metrics
				(
					id    SERIAL PRIMARY KEY,
					value INTEGER NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'custom-excludes-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
									excludes = 'metrics.*'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		def basePath = fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables')
		Files.exists(basePath)
		Files.exists(basePath.resolve('Accounts.java'))
		!Files.exists(basePath.resolve('Metrics.java'))
		!Files.exists(basePath.resolve('FlywaySchemaHistory.java'))
	}

	def "flyway metadata exclusion does not filter similarly named tables"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()

				file('V002__create_history_metrics.sql').text = '''\
				CREATE TABLE flyway_schema_history_metrics
				(
					id         SERIAL PRIMARY KEY,
					message    VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'flyway-table-regex'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		def basePath = fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables')
		Files.exists(basePath.resolve('FlywaySchemaHistoryMetrics.java'))
		!Files.exists(basePath.resolve('FlywaySchemaHistory.java'))
	}

	def "dsl respects custom Flyway metadata table overrides"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'custom-flyway-table-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
							table = 'schema_version_custom'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		def basePath = fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables')
		Files.exists(basePath.resolve('Accounts.java'))
		!Files.exists(basePath.resolve('SchemaVersionCustom.java'))
	}

	def "dsl allows missing database driver when Flyway config declares driver"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'flyway-driver-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
							driver = 'org.postgresql.Driver'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "dsl resolves schema from flyway schemas list when default schema is absent"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'flyway-schemas-list'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = ''
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							schemas = ['public']
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "dsl compares schemas case-insensitively"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'schema-case-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'PUBLIC'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "migration inputs only include jars containing Flyway locations"() {
		given:
		def externalLibDir = Files.createTempDirectory('flyway-libs')
		def migrationJarPath = externalLibDir.resolve('with-migrations.jar')
		def emptyJarPath = externalLibDir.resolve('without-migrations.jar')

		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			dir('libs') {
				file('placeholder.txt')
			}

			file('settings.gradle').text = "rootProject.name = 'migration-inputs-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator files('%s', '%s')
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}

			tasks.register('printMigrationInputs') {
				doLast {
					def generator = tasks.named('generateMainDatabaseClasses').get()
					def jarNames = generator.migrationInputs.files
							.findAll { it.name.endsWith('.jar') }
							.collect { it.name }
							.sort()
							.join(',')
					println "MIGRATION_INPUTS=${jarNames}"
				}
			}
			'''.stripIndent().formatted(
					POSTGRES_VERSION,
					migrationJarPath.toAbsolutePath().toString().replace('\\', '\\\\'),
					emptyJarPath.toAbsolutePath().toString().replace('\\', '\\\\'),
					JOOQ_VERSION)
		}

		writeMigrationJar(migrationJarPath.toFile(), 'SELECT 1;')
		writeEmptyJar(emptyJarPath.toFile())

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('printMigrationInputs', '--stacktrace', '--info')
				.build()

		then:
		result.task(':printMigrationInputs').outcome == SUCCESS
		result.output.contains('MIGRATION_INPUTS=with-migrations.jar')
		!result.output.contains('without-migrations.jar')
	}

	def "dsl skips Flyway sql migration prefix override when value matches default"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"
			file('build.gradle').text =
					singleSchemaBuildScript("sqlMigrationPrefix = 'V'")
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.output.contains('Skipping Flyway sqlMigrationPrefix override')
	}

	def "dsl respects custom PostgreSQL Docker image override"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"
			file('build.gradle').text =
					singleSchemaBuildScript(
					'',
					'''\n\t\t\tcontainer {\n\t\t\t\timage = 'postgres:16-alpine'\n\t\t\t}\n'''
					)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "dsl supports absolute filesystem Flyway locations"() {
		given:
		fileSystemFixture.create {
			dir('external-migrations') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'filesystem-absolute-project'"
			def absolutePath = resolve('external-migrations').toAbsolutePath().toString().replace('\\', '\\\\')
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'filesystem:%s'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION, absolutePath)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
	}

	def "dsl updates schema configuration when jOOQ mapping changes"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'config-reassignment'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.main'
								}
							}
						}
					}

					reporting {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.reporting'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}

					schema('public') {
						jooqConfigurations(['reporting'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateReportingDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses')?.outcome == SKIPPED
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/reporting/test/reporting/Tables.java'))
		!Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/main/Tables.java'))
	}



	def "schema-level flyway overrides merge and apply to per-schema configuration"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/analytics') {
				file('A001__create_tables.sql').text = '''\
					CREATE TABLE ${tableName} (
						id SERIAL PRIMARY KEY,
						username VARCHAR(50) NOT NULL
					)
					'''.stripIndent()

				file('A002__create_tables.sql').text = '''\
					CREATE TABLE ${auditTable} (
						id SERIAL PRIMARY KEY,
						change_log TEXT NOT NULL
					)
					'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'schema-overrides-project'"
			file('build.gradle').text = '''\
		plugins {
			id 'java'
			id 'io.github.suppierk.jooq-java-class-generator'
		}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'analytics'
								}

								target {
									packageName = 'test.analytics'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('analytics') {
						flyway {
							defaultSchema = 'analytics'
							createSchemas = true
							sqlMigrationPrefix = 'A'
							locations = 'classpath:db/analytics'
							placeholderReplacement = true
							placeholders = [tableName: 'accounts', auditTable: 'audit_log']
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/analytics/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/analytics/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/analytics/tables/AuditLog.java'))
		!Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/analytics/tables/LegacyAccounts.java'))
	}

	def "dsl fails when JDBC driver dependency is missing"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'missing-driver-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.buildAndFail()

		then:
		result.output.contains("Database driver 'org.postgresql.Driver' is not available on the project classpath")
	}

	@Unroll
	def "schema requirement scenario - #scenarioDescription"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__noop.sql').text = '''\
				CREATE TABLE noop (
					id SERIAL PRIMARY KEY
				)
				'''.stripIndent()
			}
			file('settings.gradle').text = "rootProject.name = 'missing-schema-project'"
			file('build.gradle').text =
					schemaRequirementBuildScript(
					includeFlywaySchema,
					includeJooqSchema,
					flywaySchemaValue,
					jooqSchemaValue)
		}

		when:
		def runner = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
		def result = expectSuccess ? runner.build() : runner.buildAndFail()

		then:
		if (expectSuccess) {
			result.task(':generateDatabaseClasses').outcome == SUCCESS
		} else {
			result.output.contains(expectedMessage)
		}

		where:
		scenarioDescription         | includeFlywaySchema | includeJooqSchema | flywaySchemaValue | jooqSchemaValue || expectSuccess | expectedMessage
		'Flyway and jOOQ schemas'   | false               | false             | null              | null            || false         | "Database schema must be declared via Flyway 'defaultSchema' or jOOQ 'inputSchema'"
		'Flyway default schema'     | false               | true              | null              | 'public'        || true          | null
		'jOOQ input schema'         | true                | false             | 'public'         | null            || true          | null
		'Mismatched schemas'        | true                | true              | 'audit'          | 'public'        || false         | "Flyway default schema 'audit' does not match jOOQ input schema 'public'"
	}

	def "dsl fails when driver is not yet supported"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_metrics.sql').text = '''\
				CREATE TABLE metrics (
					id INTEGER PRIMARY KEY,
					value INTEGER NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'unsupported-driver-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'com.h2database:h2:%s'
				jooqGenerator 'com.h2database:h2:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.h2.H2Database'
									inputSchema = 'PUBLIC'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('h2Db') {
					driver = 'org.h2.Driver'

					schema('PUBLIC') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'PUBLIC'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(H2_VERSION, H2_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.buildAndFail()

		then:
		result.output.contains("Driver 'org.h2.Driver' is not yet supported")
	}

	def "kotlin dsl handles schema overrides and multi-database orchestration"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/analytics') {
				file('A001__create_tables.sql').text = '''\
CREATE TABLE ${tableName} (
	id SERIAL PRIMARY KEY,
	username VARCHAR(50) NOT NULL
)
'''.stripIndent()

				file('A002__create_tables.sql').text = '''\
CREATE TABLE ${auditTable} (
	id SERIAL PRIMARY KEY,
	change_log TEXT NOT NULL
)
'''.stripIndent()
			}
			dir('src/main/resources/db/migration/postgres/public') {
				file('V001__create_accounts.sql').text = '''\
CREATE TABLE accounts (
	id SERIAL PRIMARY KEY,
	username VARCHAR(50) NOT NULL
)
'''.stripIndent()
			}
			dir('src/main/resources/db/migration/postgres/audit') {
				file('V001__create_audit.sql').text = '''\
CREATE TABLE audit_log (
	id SERIAL PRIMARY KEY,
	message VARCHAR(255) NOT NULL
)
'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/core') {
				file('V001__create_customers.sql').text = '''\
CREATE TABLE customers (
	id INT AUTO_INCREMENT PRIMARY KEY,
	email VARCHAR(120) NOT NULL
)
'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/reporting') {
				file('V001__create_metrics.sql').text = '''\
CREATE TABLE metrics (
	id INT AUTO_INCREMENT PRIMARY KEY,
	value DOUBLE NOT NULL
)
'''.stripIndent()
			}

			file('settings.gradle.kts').text = 'rootProject.name = "kotlin-multi-project"'
			file('build.gradle.kts').text = '''\
		plugins {
			java
			id("io.github.suppierk.jooq-java-class-generator")
		}

group = "io.github.suppierk"
version = "1.0.0-SNAPSHOT"

repositories {
	mavenCentral()
}

	dependencies {
		implementation("org.postgresql:postgresql:%s")
		implementation("com.mysql:mysql-connector-j:%s")
		jooqGenerator("org.postgresql:postgresql:%s")
		jooqGenerator("com.mysql:mysql-connector-j:%s")
	}

jooq {
	version.set("%s")

	configurations {
		create("analytics") {
			jooqConfiguration.apply {
				logging = org.jooq.meta.jaxb.Logging.WARN

				generator.apply {
					database.apply {
						name = "org.jooq.meta.postgres.PostgresDatabase"
						inputSchema = "analytics"
					}

					target.apply {
						packageName = "test.analytics"
					}
				}
			}
		}

		create("pgmain") {
			jooqConfiguration.apply {
				logging = org.jooq.meta.jaxb.Logging.WARN

				generator.apply {
					database.apply {
						name = "org.jooq.meta.postgres.PostgresDatabase"
						inputSchema = "public"
					}

					target.apply {
						packageName = "test.pgmain"
					}
				}
			}
		}

		create("pgaudit") {
			jooqConfiguration.apply {
				logging = org.jooq.meta.jaxb.Logging.WARN

				generator.apply {
					database.apply {
						name = "org.jooq.meta.postgres.PostgresDatabase"
						inputSchema = "audit"
					}

					target.apply {
						packageName = "test.pgaudit"
					}
				}
			}
		}

		create("mysqlcore") {
			jooqConfiguration.apply {
				logging = org.jooq.meta.jaxb.Logging.WARN

				generator.apply {
					database.apply {
						name = "org.jooq.meta.mysql.MySQLDatabase"
						inputSchema = "core"
					}

					target.apply {
						packageName = "test.mysqlcore"
					}
				}
			}
		}

		create("mysqlreporting") {
			jooqConfiguration.apply {
				logging = org.jooq.meta.jaxb.Logging.WARN

				generator.apply {
					database.apply {
						name = "org.jooq.meta.mysql.MySQLDatabase"
						inputSchema = "reporting"
					}

					target.apply {
						packageName = "test.mysqlreporting"
					}
				}
			}
		}
	}
}

jooqCodegen {
	database("postgresDb") {
		driver("org.postgresql.Driver")

		schema("analytics") {
			flyway {
				defaultSchema = "analytics"
				sqlMigrationPrefix = "A"
				locations = listOf("classpath:db/analytics")
				placeholderReplacement = true
				placeholders = mapOf(
					"tableName" to "accounts",
					"auditTable" to "audit_log"
				)
			}
			jooqConfigurations("analytics")
		}

		schema("public") {
			flyway {
				locations = listOf("classpath:db/migration/postgres/public")
				defaultSchema = "public"
			}
			jooqConfigurations("pgmain")
		}

		schema("audit") {
			flyway {
				locations = listOf("classpath:db/migration/postgres/audit")
				defaultSchema = "audit"
			}
			jooqConfigurations("pgaudit")
		}
	}

	database("mysqlDb") {
		driver("com.mysql.cj.jdbc.Driver")

		schema("core") {
			flyway {
				locations = listOf("classpath:db/migration/mysql/core")
				defaultSchema = "core"
			}
			jooqConfigurations("mysqlcore")
		}

		schema("reporting") {
			flyway {
				locations = listOf("classpath:db/migration/mysql/reporting")
				defaultSchema = "reporting"
			}
			jooqConfigurations("mysqlreporting")
		}
	}
}
'''.stripIndent().formatted(POSTGRES_VERSION, MYSQL_VERSION, POSTGRES_VERSION, MYSQL_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateAnalyticsDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgmainDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgauditDatabaseClasses').outcome == SUCCESS
		result.task(':generateMysqlcoreDatabaseClasses').outcome == SUCCESS
		result.task(':generateMysqlreportingDatabaseClasses').outcome == SUCCESS

		and:
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/analytics/test/analytics/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/analytics/test/analytics/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/analytics/test/analytics/tables/AuditLog.java'))
		!Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/analytics/test/analytics/tables/LegacyAccounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgmain/test/pgmain/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgaudit/test/pgaudit/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/mysqlcore/test/mysqlcore/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/mysqlreporting/test/mysqlreporting/Tables.java'))
	}

	def "dsl supports multiple jOOQ configurations mapping a single database"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/public') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/reporting') {
				file('V001__create_audit.sql').text = '''\
				CREATE TABLE audit_log
				(
					id      SERIAL PRIMARY KEY,
					message VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'multi-schema-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.main'
								}
							}
						}
					}

					reporting {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'reporting'
								}

								target {
									packageName = 'test.reporting'
								}
							}
						}
					}
				}
			}

		jooqCodegen {
			database('postgresDb') {
				driver = 'org.postgresql.Driver'

				schema('public') {
					flyway {
						locations = 'classpath:db/migration/public'
						defaultSchema = 'public'
					}
					jooqConfigurations(['main'])
				}

				schema('reporting') {
					flyway {
						locations = 'classpath:db/migration/reporting'
						defaultSchema = 'reporting'
					}
					jooqConfigurations(['reporting'])
				}
			}
		}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		result.task(':generateReportingDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/main/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/reporting/test/reporting/tables/AuditLog.java'))
	}

	def "jooqCodegen DSL orchestrates PostgreSQL and MySQL schemas"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/postgres') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql') {
				file('V001__create_customers.sql').text = '''\
				CREATE TABLE customers (
					id INT AUTO_INCREMENT PRIMARY KEY,
					email VARCHAR(120) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'multi-db-project'"

			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				implementation 'com.mysql:mysql-connector-j:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
				jooqGenerator 'com.mysql:mysql-connector-j:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.postgres'
								}
							}
						}
					}

					mysql {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'test'
								}

								target {
									packageName = 'test.mysql'
								}
							}
						}
					}
				}
			}

		jooqCodegen {
			database('postgresDb') {
				driver = 'org.postgresql.Driver'
				schema('public') {
					flyway {
						locations = 'classpath:db/migration/postgres'
						defaultSchema = 'public'
					}
					jooqConfigurations(['main'])
				}
			}

			database('mysqlDb') {
				driver = 'com.mysql.cj.jdbc.Driver'
				schema('test') {
					flyway {
						locations = 'classpath:db/migration/mysql'
						defaultSchema = 'test'
					}
					jooqConfigurations(['mysql'])
				}
			}
		}
			'''.stripIndent().formatted(POSTGRES_VERSION, MYSQL_VERSION, POSTGRES_VERSION, MYSQL_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateMainDatabaseClasses').outcome == SUCCESS
		result.task(':generateMysqlDatabaseClasses').outcome == SUCCESS

		and:
		def postgresTables = fileSystemFixture.resolve('build/generated-src/jooq/main/test/postgres/Tables.java')
		def postgresAccounts = fileSystemFixture.resolve('build/generated-src/jooq/main/test/postgres/tables/Accounts.java')
		def mysqlTables = fileSystemFixture.resolve('build/generated-src/jooq/mysql/test/mysql/Tables.java')
		def mysqlCustomers = fileSystemFixture.resolve('build/generated-src/jooq/mysql/test/mysql/tables/Customers.java')

		Files.exists(postgresTables)
		Files.exists(postgresAccounts)
		Files.exists(mysqlTables)
		Files.exists(mysqlCustomers)

		and:
		Files.readString(postgresTables).contains('class Tables')
		Files.readString(postgresAccounts).contains('class Accounts')
		Files.readString(mysqlTables).contains('class Tables')
		Files.readString(mysqlCustomers).contains('class Customers')
	}

	def "migration cache tracks processed resources output"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					version_tag VARCHAR(20) DEFAULT '@VERSION@'
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'processed-resources-project'"
			file('build.gradle').text = """\
			import org.apache.tools.ant.filters.ReplaceTokens

			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			def schemaVersion = project.findProperty('schemaVersion') ?: 'v1'

			processResources {
				inputs.property('schemaVersion', schemaVersion)
				filesMatching('db/migration/*.sql') {
					filter ReplaceTokens, tokens: [VERSION: schemaVersion]
				}
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			""".stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def firstRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info', '-PschemaVersion=v1')
				.build()

		and:
		def secondRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info', '-PschemaVersion=v2')
				.build()

		then:
		firstRun.task(':generateMainDatabaseClasses').outcome == SUCCESS
		secondRun.task(':generateMainDatabaseClasses').outcome == SUCCESS
		secondRun.task(':generateMainDatabaseClasses').outcome != UP_TO_DATE

		and:
		def processedMigration = fileSystemFixture.resolve('build/resources/main/db/migration/V001__create_accounts.sql')
		Files.readString(processedMigration).contains("DEFAULT 'v2'")
	}

	def "migration cache tracks classpath-provided migrations"() {
		given:
		def externalJarDir = Files.createTempDirectory("migrations")
		def externalJar = externalJarDir.resolve('migrations.jar').toFile()
		writeMigrationJar(externalJar, "-- version v1")
		def escapedJarPath = externalJar.absolutePath.replace('\\', '\\\\')
		fileSystemFixture.create {

			file('settings.gradle').text = "rootProject.name = 'classpath-migrations-project'"
			file('build.gradle').text = """\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
				implementation files('%s')
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			""".stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, escapedJarPath, JOOQ_VERSION)
		}

		when:
		def firstRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		and:
		writeMigrationJar(externalJar, "-- version v2")
		def secondRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		firstRun.task(':generateMainDatabaseClasses').outcome == SUCCESS
		secondRun.task(':generateMainDatabaseClasses').outcome == SUCCESS
		secondRun.task(':generateMainDatabaseClasses').outcome != UP_TO_DATE
	}

	def "container fingerprint tracks resolved image defaults"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts
				(
					id         SERIAL PRIMARY KEY,
					username   VARCHAR(50) UNIQUE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'container-cache-project'"
			file('build.gradle').text = """\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/migration'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}

					if (project.hasProperty('containerImageOverride')) {
						container {
							image = project.property('containerImageOverride')
						}
					}
				}
			}
			""".stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def firstRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		and:
		def secondRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments(
				'generateDatabaseClasses',
				'--stacktrace',
				'--info',
				'-PcontainerImageOverride=postgres:16-alpine')
				.build()

		then:
		firstRun.task(':generateMainDatabaseClasses').outcome == SUCCESS
		secondRun.task(':generateMainDatabaseClasses').outcome == SUCCESS
		secondRun.task(':generateMainDatabaseClasses').outcome != UP_TO_DATE
	}

	def "dsl supports identical schema names across databases"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/postgres') {
				file('V001__create_orders.sql').text = '''\
				CREATE TABLE orders (
					id SERIAL PRIMARY KEY,
					total NUMERIC NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql') {
				file('V001__create_orders.sql').text = '''\
				CREATE TABLE orders (
					id INT AUTO_INCREMENT PRIMARY KEY,
					total DECIMAL(10,2) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'same-schema-project'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				implementation 'com.mysql:mysql-connector-j:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
				jooqGenerator 'com.mysql:mysql-connector-j:%s'
			}

			jooq {
				version = '%s'

				configurations {
					postgres {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.postgres'
								}
							}
						}
					}

					mysql {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.mysql'
								}
							}
						}
					}
				}
			}

				jooqCodegen {
					database('postgresDb') {
						driver = 'org.postgresql.Driver'
						schema('public') {
							flyway {
								locations = 'classpath:db/migration/postgres'
								defaultSchema = 'public'
							}
							jooqConfigurations = ['postgres']
						}
					}

					database('mysqlDb') {
						driver = 'com.mysql.cj.jdbc.Driver'
						schema('public') {
							flyway {
								locations = 'classpath:db/migration/mysql'
								defaultSchema = 'public'
							}
							jooqConfigurations = ['mysql']
						}
					}
				}
			'''.stripIndent().formatted(POSTGRES_VERSION, MYSQL_VERSION, POSTGRES_VERSION, MYSQL_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generatePostgresDatabaseClasses').outcome == SUCCESS
		result.task(':generateMysqlDatabaseClasses').outcome == SUCCESS

		and:
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/postgres/test/postgres/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/mysql/test/mysql/Tables.java'))
	}

	def "dsl orchestrates multiple schemas across multiple databases"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/postgres/public') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/postgres/audit') {
				file('V001__create_audit.sql').text = '''\
				CREATE TABLE audit_log (
					id SERIAL PRIMARY KEY,
					message VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/core') {
				file('V001__create_customers.sql').text = '''\
				CREATE TABLE customers (
					id INT AUTO_INCREMENT PRIMARY KEY,
					email VARCHAR(120) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/reporting') {
				file('V001__create_metrics.sql').text = '''\
				CREATE TABLE metrics (
					id INT AUTO_INCREMENT PRIMARY KEY,
					value DOUBLE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'multi-db-multi-schema'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				implementation 'com.mysql:mysql-connector-j:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
				jooqGenerator 'com.mysql:mysql-connector-j:%s'
			}

			jooq {
				version = '%s'

				configurations {
					pgmain {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.pgmain'
								}
							}
						}
					}

					pgaudit {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'audit'
								}

								target {
									packageName = 'test.pgaudit'
								}
							}
						}
					}

					mysqlcore {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'core'
								}

								target {
									packageName = 'test.mysqlcore'
								}
							}
						}
					}

					mysqlreporting {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'reporting'
								}

								target {
									packageName = 'test.mysqlreporting'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'
					schema('public') {
						flyway {
							locations = 'classpath:db/migration/postgres/public'
							defaultSchema = 'public'
						}
						jooqConfigurations = ['pgmain']
					}

					schema('audit') {
						flyway {
							locations = 'classpath:db/migration/postgres/audit'
							defaultSchema = 'audit'
						}
						jooqConfigurations = ['pgaudit']
					}
				}

				database('mysqlDb') {
					driver = 'com.mysql.cj.jdbc.Driver'
					schema('core') {
						flyway {
							locations = 'classpath:db/migration/mysql/core'
							defaultSchema = 'core'
						}
						jooqConfigurations = ['mysqlcore']
					}

					schema('reporting') {
						flyway {
							locations = 'classpath:db/migration/mysql/reporting'
							defaultSchema = 'reporting'
						}
						jooqConfigurations = ['mysqlreporting']
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, MYSQL_VERSION, POSTGRES_VERSION, MYSQL_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgmainDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgauditDatabaseClasses').outcome == SUCCESS
		result.task(':generateMysqlcoreDatabaseClasses').outcome == SUCCESS
		result.task(':generateMysqlreportingDatabaseClasses').outcome == SUCCESS

		and:
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgmain/test/pgmain/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgaudit/test/pgaudit/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/mysqlcore/test/mysqlcore/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/mysqlreporting/test/mysqlreporting/Tables.java'))
	}

	def "readme groovy DSL sample generates code"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/public') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/audit') {
				file('V001__create_audit.sql').text = '''\
				CREATE TABLE audit_log (
					id SERIAL PRIMARY KEY,
					message VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'readme-groovy-sample'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

		jooq {
			version = '%s'

			configurations {
				pgPublic {
					generationTool {
						logging = org.jooq.meta.jaxb.Logging.WARN

						generator {
							database {
								name = 'org.jooq.meta.postgres.PostgresDatabase'
								inputSchema = 'public'
							}

							target {
								packageName = 'example.pgpublic'
							}
						}
					}
				}

				pgAudit {
					generationTool {
						logging = org.jooq.meta.jaxb.Logging.WARN

						generator {
							database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'audit'
								}

								target {
									packageName = 'example.pgaudit'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('analyticsDb') {
					driver = 'org.postgresql.Driver'

					container {
						image = 'postgres:17-alpine'
					}

					schema('public') {
						flyway {
							locations = 'classpath:db/migration/public'
							defaultSchema = 'public'
							cleanDisabled = true
							placeholder 'foo', 'bar'
						}
						jooqConfigurations = ['pgPublic']
					}

					schema('audit') {
						flyway {
							locations = 'classpath:db/migration/audit'
							defaultSchema = 'audit'
						}
						jooqConfigurations = ['pgAudit']
					}
				}
			}
		'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgPublicDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgAuditDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgPublic/example/pgpublic/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgAudit/example/pgaudit/Tables.java'))
	}

	def "readme kotlin DSL sample generates code"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/public') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/audit') {
				file('V001__create_audit.sql').text = '''\
				CREATE TABLE audit_log (
					id SERIAL PRIMARY KEY,
					message VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle.kts').text = "rootProject.name = \"readme-kotlin-sample\""
			file('build.gradle.kts').text = '''\
			plugins {
				java
				id("io.github.suppierk.jooq-java-class-generator")
			}

			group = "io.github.suppierk"
			version = "1.0.0-SNAPSHOT"

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation("org.postgresql:postgresql:%s")
				jooqGenerator("org.postgresql:postgresql:%s")
			}

		jooq {
			version.set("%s")

			configurations {
				create("pgpublic") {
					jooqConfiguration.apply {
						logging = org.jooq.meta.jaxb.Logging.WARN

						generator.apply {
							database.apply {
									name = "org.jooq.meta.postgres.PostgresDatabase"
									inputSchema = "public"
								}

								target.apply {
									packageName = "example.pgpublic"
								}
							}
						}
					}

				create("pgaudit") {
					jooqConfiguration.apply {
						logging = org.jooq.meta.jaxb.Logging.WARN

						generator.apply {
							database.apply {
									name = "org.jooq.meta.postgres.PostgresDatabase"
									inputSchema = "audit"
								}

								target.apply {
									packageName = "example.pgaudit"
								}
							}
						}
					}
				}
			}

		jooqCodegen {
			database("analyticsDb") {
				driver("org.postgresql.Driver")

				container {
					image().set("postgres:17-alpine")
				}

				schemas.create("public") {
					flyway {
						locations = listOf("classpath:db/migration/public")
						defaultSchema = "public"
						cleanDisabled = true
						placeholders = mapOf("foo" to "bar")
					}
					jooqConfigurations = listOf("pgpublic")
				}

				schemas.create("audit") {
					flyway {
						locations = listOf("classpath:db/migration/audit")
						defaultSchema = "audit"
					}
					jooqConfigurations = listOf("pgaudit")
				}
			}
		}
	'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgpublicDatabaseClasses').outcome == SUCCESS
		result.task(':generatePgauditDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgpublic/example/pgpublic/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/pgaudit/example/pgaudit/Tables.java'))
	}

	def "codegen tasks are up-to-date on subsequent runs"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/postgres/public') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/postgres/audit') {
				file('V001__create_audit.sql').text = '''\
				CREATE TABLE audit_log (
					id SERIAL PRIMARY KEY,
					message VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/core') {
				file('V001__create_customers.sql').text = '''\
				CREATE TABLE customers (
					id INT AUTO_INCREMENT PRIMARY KEY,
					email VARCHAR(120) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/reporting') {
				file('V001__create_metrics.sql').text = '''\
				CREATE TABLE metrics (
					id INT AUTO_INCREMENT PRIMARY KEY,
					value DOUBLE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'multi-db-multi-schema'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				implementation 'com.mysql:mysql-connector-j:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
				jooqGenerator 'com.mysql:mysql-connector-j:%s'
			}

			jooq {
				version = '%s'

				configurations {
					pgmain {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.pgmain'
								}
							}
						}
					}

					pgaudit {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'audit'
								}

								target {
									packageName = 'test.pgaudit'
								}
							}
						}
					}

					mysqlcore {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'core'
								}

								target {
									packageName = 'test.mysqlcore'
								}
							}
						}
					}

					mysqlreporting {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'reporting'
								}

								target {
									packageName = 'test.mysqlreporting'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'
					schema('public') {
						flyway {
							locations = 'classpath:db/migration/postgres/public'
							defaultSchema = 'public'
						}
						jooqConfigurations(['pgmain'])
					}

					schema('audit') {
						flyway {
							locations = 'classpath:db/migration/postgres/audit'
							defaultSchema = 'audit'
						}
						jooqConfigurations(['pgaudit'])
					}
				}

				database('mysqlDb') {
					driver = 'com.mysql.cj.jdbc.Driver'

					schema('core') {
						flyway {
							locations = 'classpath:db/migration/mysql/core'
							defaultSchema = 'core'
						}
						jooqConfigurations(['mysqlcore'])
					}

					schema('reporting') {
						flyway {
							locations = 'classpath:db/migration/mysql/reporting'
							defaultSchema = 'reporting'
						}
						jooqConfigurations(['mysqlreporting'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, MYSQL_VERSION, POSTGRES_VERSION, MYSQL_VERSION, JOOQ_VERSION)
		}

		when:
		def firstRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		firstRun.task(':generateDatabaseClasses').outcome == SUCCESS
		firstRun.task(':generatePgmainDatabaseClasses').outcome == SUCCESS
		firstRun.task(':generatePgauditDatabaseClasses').outcome == SUCCESS
		firstRun.task(':generateMysqlcoreDatabaseClasses').outcome == SUCCESS
		firstRun.task(':generateMysqlreportingDatabaseClasses').outcome == SUCCESS

		when:
		def secondRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		secondRun.task(':generateDatabaseClasses').outcome == UP_TO_DATE
		secondRun.task(':generatePgmainDatabaseClasses').outcome == UP_TO_DATE
		secondRun.task(':generatePgauditDatabaseClasses').outcome == UP_TO_DATE
		secondRun.task(':generateMysqlcoreDatabaseClasses').outcome == UP_TO_DATE
		secondRun.task(':generateMysqlreportingDatabaseClasses').outcome == UP_TO_DATE
	}

	def "codegen tasks rerun when migrations change"() {
		given:
		fileSystemFixture.create {
			dir('src/main/resources/db/migration/postgres/public') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/postgres/audit') {
				file('V001__create_audit.sql').text = '''\
				CREATE TABLE audit_log (
					id SERIAL PRIMARY KEY,
					message VARCHAR(255) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/core') {
				file('V001__create_customers.sql').text = '''\
				CREATE TABLE customers (
					id INT AUTO_INCREMENT PRIMARY KEY,
					email VARCHAR(120) NOT NULL
				)
				'''.stripIndent()
			}
			dir('src/main/resources/db/migration/mysql/reporting') {
				file('V001__create_metrics.sql').text = '''\
				CREATE TABLE metrics (
					id INT AUTO_INCREMENT PRIMARY KEY,
					value DOUBLE NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'multi-db-multi-schema'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				implementation 'com.mysql:mysql-connector-j:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
				jooqGenerator 'com.mysql:mysql-connector-j:%s'
			}

			jooq {
				version = '%s'

				configurations {
					pgmain {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.pgmain'
								}
							}
						}
					}

					pgaudit {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'audit'
								}

								target {
									packageName = 'test.pgaudit'
								}
							}
						}
					}

					mysqlcore {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'core'
								}

								target {
									packageName = 'test.mysqlcore'
								}
							}
						}
					}

					mysqlreporting {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.mysql.MySQLDatabase'
									inputSchema = 'reporting'
								}

								target {
									packageName = 'test.mysqlreporting'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'
					schema('public') {
						flyway {
							locations = 'classpath:db/migration/postgres/public'
							defaultSchema = 'public'
						}
						jooqConfigurations(['pgmain'])
					}

					schema('audit') {
						flyway {
							locations = 'classpath:db/migration/postgres/audit'
							defaultSchema = 'audit'
						}
						jooqConfigurations(['pgaudit'])
					}
				}

				database('mysqlDb') {
					driver = 'com.mysql.cj.jdbc.Driver'

					schema('core') {
						flyway {
							locations = 'classpath:db/migration/mysql/core'
							defaultSchema = 'core'
						}
						jooqConfigurations(['mysqlcore'])
					}

					schema('reporting') {
						flyway {
							locations = 'classpath:db/migration/mysql/reporting'
							defaultSchema = 'reporting'
						}
						jooqConfigurations(['mysqlreporting'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, MYSQL_VERSION, POSTGRES_VERSION, MYSQL_VERSION, JOOQ_VERSION)
		}

		when:
		def initialRun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		initialRun.task(':generateDatabaseClasses').outcome == SUCCESS

		when:
		def migrationPath = fileSystemFixture.resolve('src/main/resources/db/migration/postgres/public/V001__create_accounts.sql')
		Files.writeString(
				migrationPath,
				'''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL,
					email VARCHAR(120) NOT NULL
				)
				'''.stripIndent())

		def rerun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		rerun.task(':generateDatabaseClasses').outcome == SUCCESS
		rerun.task(':generatePgmainDatabaseClasses').outcome == SUCCESS
		rerun.task(':generatePgauditDatabaseClasses').outcome == UP_TO_DATE
		rerun.task(':generateMysqlcoreDatabaseClasses').outcome == UP_TO_DATE
		rerun.task(':generateMysqlreportingDatabaseClasses').outcome == UP_TO_DATE
		Files.readString(fileSystemFixture.resolve('build/generated-src/jooq/pgmain/test/pgmain/tables/Accounts.java')).contains('EMAIL')
	}

	def "codegen tasks rerun when migrations change in custom resource directories"() {
		given:
		fileSystemFixture.create {
			dir('src/custom/resources/db/custom') {
				file('V001__create_accounts.sql').text = '''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL
				)
				'''.stripIndent()
			}

			file('settings.gradle').text = "rootProject.name = 'custom-resources'"
			file('build.gradle').text = '''\
			plugins {
				id 'java'
				id 'io.github.suppierk.jooq-java-class-generator'
			}

			group = 'io.github.suppierk'
			version = '1.0.0-SNAPSHOT'

			repositories {
				mavenCentral()
			}

			dependencies {
				implementation 'org.postgresql:postgresql:%s'
				jooqGenerator 'org.postgresql:postgresql:%s'
			}

			sourceSets {
				main {
					resources {
						setSrcDirs(['src/custom/resources'])
					}
				}
			}

			jooq {
				version = '%s'

				configurations {
					main {
						generationTool {
							logging = org.jooq.meta.jaxb.Logging.WARN

							generator {
								database {
									name = 'org.jooq.meta.postgres.PostgresDatabase'
									inputSchema = 'public'
								}

								target {
									packageName = 'test.custom'
								}
							}
						}
					}
				}
			}

			jooqCodegen {
				database('postgresDb') {
					driver = 'org.postgresql.Driver'

					schema('public') {
						flyway {
							locations = 'classpath:db/custom'
							defaultSchema = 'public'
						}
						jooqConfigurations(['main'])
					}
				}
			}
			'''.stripIndent().formatted(POSTGRES_VERSION, POSTGRES_VERSION, JOOQ_VERSION)
		}

		when:
		def initial = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		initial.task(':generateDatabaseClasses').outcome == SUCCESS
		initial.task(':generateMainDatabaseClasses').outcome == SUCCESS

		when:
		def migrationPath = fileSystemFixture.resolve('src/custom/resources/db/custom/V001__create_accounts.sql')
		Files.writeString(
				migrationPath,
				'''\
				CREATE TABLE accounts (
					id SERIAL PRIMARY KEY,
					username VARCHAR(50) NOT NULL,
					email VARCHAR(255) NOT NULL
				)
				'''.stripIndent())

		def rerun = GradleRunner.create()
				.withPluginClasspath()
				.forwardOutput()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', '--stacktrace', '--info')
				.build()

		then:
		rerun.task(':generateDatabaseClasses').outcome == SUCCESS
		rerun.task(':generateMainDatabaseClasses').outcome == SUCCESS
	}
	private static String schemaRequirementBuildScript(
			boolean includeFlywaySchema,
			boolean includeJooqSchema,
			String flywaySchema = 'public',
			String jooqSchema = 'public'
	) {
		def resolvedFlywaySchema = flywaySchema ?: 'public'
		def resolvedJooqSchema = jooqSchema ?: 'public'
		def flywaySchemaLine =
				includeFlywaySchema ? "\n\t\t\t\t\tdefaultSchema = '${resolvedFlywaySchema}'" : ''
		def jooqSchemaLine =
				includeJooqSchema ? "\n\t\t\t\t\tinputSchema = '${resolvedJooqSchema}'" : ''

		return """\
		plugins {
			id 'java'
			id 'io.github.suppierk.jooq-java-class-generator'
		}

		group = 'io.github.suppierk'
		version = '1.0.0-SNAPSHOT'

		repositories {
			mavenCentral()
		}

		dependencies {
			implementation 'org.postgresql:postgresql:${POSTGRES_VERSION}'
			jooqGenerator 'org.postgresql:postgresql:${POSTGRES_VERSION}'
		}

		jooq {
			version = '${JOOQ_VERSION}'

			configurations {
				main {
					generationTool {
						logging = org.jooq.meta.jaxb.Logging.WARN

						generator {
							database {
								name = 'org.jooq.meta.postgres.PostgresDatabase'${jooqSchemaLine}
							}

							target {
								packageName = 'test'
							}
						}
					}
				}
			}
		}

		jooqCodegen {
			database('postgresDb') {
				driver = 'org.postgresql.Driver'

				schema('public') {
					flyway {
						locations = 'classpath:db/migration'${flywaySchemaLine}
					}
					jooqConfigurations(['main'])
				}
			}
		}
		""".stripIndent()
	}

	private static String singleSchemaBuildScript(
			String flywayCustomisations = '',
			String extraBlocks = ''
	) {
		def trimmedExtras = extraBlocks?.stripIndent() ?: ''
		def extraSuffix = trimmedExtras.isBlank() ? '' : '\n' + indentLines(trimmedExtras, '\t\t') + '\n'
		def flywaySuffix = renderFlywayExtras(flywayCustomisations)

		return """\
		plugins {
			id 'java'
			id 'io.github.suppierk.jooq-java-class-generator'
		}

		group = 'io.github.suppierk'
		version = '1.0.0-SNAPSHOT'

		repositories {
			mavenCentral()
		}

		dependencies {
			implementation 'org.postgresql:postgresql:${POSTGRES_VERSION}'
			jooqGenerator 'org.postgresql:postgresql:${POSTGRES_VERSION}'
		}

		jooq {
			version = '${JOOQ_VERSION}'

			configurations {
				main {
					generationTool {
						logging = org.jooq.meta.jaxb.Logging.WARN

						generator {
							database {
								name = 'org.jooq.meta.postgres.PostgresDatabase'
								inputSchema = 'public'
							}

							target {
								packageName = 'test'
							}
						}
					}
				}
			}
		}

		jooqCodegen {
			database('postgresDb') {
				driver = 'org.postgresql.Driver'${extraSuffix}

				schema('public') {
					flyway {
						locations = 'classpath:db/migration'
						defaultSchema = 'public'${flywaySuffix}
					}
					jooqConfigurations = ['main']
				}
			}
		}
		""".stripIndent()
	}

	private static String renderFlywayExtras(String content) {
		if (!content || content.trim().isEmpty()) {
			return ''
		}
		return '\n' + indentLines(content.trim(), '\t\t\t\t')
	}

	private static String indentLines(String text, String indent) {
		text.readLines()
				.collect { indent + it }
				.join('\n')
	}

	private static void writeMigrationJar(File jarFile, String sqlContent) {
		jarFile.parentFile.mkdirs()
		jarFile.withOutputStream { os ->
			def jarStream = new JarOutputStream(os)
			jarStream.putNextEntry(new JarEntry('db/migration/V001__classpath.sql'))
			jarStream.write(sqlContent.getBytes(StandardCharsets.UTF_8))
			jarStream.closeEntry()
			jarStream.close()
		}
	}

	private static void writeEmptyJar(File jarFile) {
		jarFile.parentFile.mkdirs()
		jarFile.withOutputStream { os ->
			def jarStream = new JarOutputStream(os)
			jarStream.close()
		}
	}
}
