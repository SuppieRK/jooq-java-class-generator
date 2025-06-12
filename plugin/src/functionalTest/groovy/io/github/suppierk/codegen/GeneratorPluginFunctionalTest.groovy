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
import spock.util.io.FileSystemFixture

import java.nio.file.Files

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GeneratorPluginFunctionalTest extends Specification {
	@TempDir
	FileSystemFixture fileSystemFixture

	def "generator does not perform any operations when there are no Flyway migration scripts"() {
		given:
		fileSystemFixture.create {
			file('settings.gradle').text = "rootProject.name = 'test-project'"

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
				implementation 'org.postgresql:postgresql:42.7.7'
				jooqGenerator 'org.postgresql:postgresql:42.7.7'
			}

			flyway {
				driver = 'org.postgresql.Driver'
				defaultSchema = 'public'
			}

			jooq {
				version = '3.20.3'

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
			'''.stripIndent()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.output.contains('No migrations found. Are your locations set up correctly?')
	}

	def 'generator successfully generates jOOQ classes'() {
		given:
		fileSystemFixture.create {
			dir('src') {
				dir('main') {
					dir('resources') {
						dir('db') {
							dir('migration') {
								file('V001__test_accounts_table.sql').text = '''\
								CREATE TABLE accounts
								(
									user_id    SERIAL PRIMARY KEY,
									username   VARCHAR(50) UNIQUE  NOT NULL,
									password   VARCHAR(50)         NOT NULL,
									email      VARCHAR(255) UNIQUE NOT NULL,
									created_at TIMESTAMP           NOT NULL,
									last_login TIMESTAMP
								)
								'''.stripIndent()
							}
						}
					}
				}
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"

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
				implementation 'org.postgresql:postgresql:42.7.7'
				jooqGenerator 'org.postgresql:postgresql:42.7.7'
			}

			flyway {
				driver = 'org.postgresql.Driver'
				defaultSchema = 'public'
			}

			jooq {
				version = '3.20.3'

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
			'''.stripIndent()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Public.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/records/AccountsRecord.java'))
	}

	def 'generator successfully generates jOOQ classes with overridden PostgreSQL Docker image'() {
		given:
		fileSystemFixture.create {
			dir('src') {
				dir('main') {
					dir('resources') {
						dir('db') {
							dir('migration') {
								file('V001__test_accounts_table.sql').text = '''\
								CREATE TABLE accounts
								(
									user_id    SERIAL PRIMARY KEY,
									username   VARCHAR(50) UNIQUE  NOT NULL,
									password   VARCHAR(50)         NOT NULL,
									email      VARCHAR(255) UNIQUE NOT NULL,
									created_at TIMESTAMP           NOT NULL,
									last_login TIMESTAMP
								)
								'''.stripIndent()
							}
						}
					}
				}
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"

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
				implementation 'org.postgresql:postgresql:42.7.7'
				jooqGenerator 'org.postgresql:postgresql:42.7.7'
			}

			flyway {
				driver = 'org.postgresql.Driver'
				defaultSchema = 'public'
			}

			jooq {
				version = '3.20.3'

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

			jooqDockerImages {
				postgres = 'postgres:15-alpine'
			}
			'''.stripIndent()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Public.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/records/AccountsRecord.java'))
	}

	def 'generator successfully generates jOOQ classes with database schema from Flyway'() {
		given:
		fileSystemFixture.create {
			dir('src') {
				dir('main') {
					dir('resources') {
						dir('db') {
							dir('migration') {
								file('V001__test_accounts_table.sql').text = '''\
								CREATE TABLE accounts
								(
									user_id    SERIAL PRIMARY KEY,
									username   VARCHAR(50) UNIQUE  NOT NULL,
									password   VARCHAR(50)         NOT NULL,
									email      VARCHAR(255) UNIQUE NOT NULL,
									created_at TIMESTAMP           NOT NULL,
									last_login TIMESTAMP
								)
								'''.stripIndent()
							}
						}
					}
				}
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"

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
				implementation 'org.postgresql:postgresql:42.7.7'
				jooqGenerator 'org.postgresql:postgresql:42.7.7'
			}

			flyway {
				driver = 'org.postgresql.Driver'
				defaultSchema = 'custom'
			}

			jooq {
				version = '3.20.3'

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
			'''.stripIndent()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		!Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Public.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Custom.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/records/AccountsRecord.java'))
	}

	def 'generator successfully generates jOOQ classes for multiple jOOQ configurations'() {
		given:
		fileSystemFixture.create {
			dir('src') {
				dir('main') {
					dir('resources') {
						dir('db') {
							dir('migration') {
								file('V001__test_accounts_table.sql').text = '''\
								CREATE TABLE accounts
								(
									user_id    SERIAL PRIMARY KEY,
									username   VARCHAR(50) UNIQUE  NOT NULL,
									password   VARCHAR(50)         NOT NULL,
									email      VARCHAR(255) UNIQUE NOT NULL,
									created_at TIMESTAMP           NOT NULL,
									last_login TIMESTAMP
								)
								'''.stripIndent()
							}
						}
					}
				}
			}

			file('settings.gradle').text = "rootProject.name = 'test-project'"

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
				implementation 'org.postgresql:postgresql:42.7.7'
				jooqGenerator 'org.postgresql:postgresql:42.7.7'
			}

			flyway {
				driver = 'org.postgresql.Driver'
				defaultSchema = 'public'
			}

			jooq {
				version = '3.20.3'

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
					
					test {
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
			'''.stripIndent()
		}

		when:
		def result = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(fileSystemFixture.currentPath.toFile())
				.withArguments('generateDatabaseClasses', 'generateTestDatabaseClasses')
				.build()

		then:
		result.task(':generateDatabaseClasses').outcome == SUCCESS
		result.task(':generateTestDatabaseClasses').outcome == SUCCESS
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Public.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/main/test/tables/records/AccountsRecord.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/test/test/Public.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/test/test/Tables.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/test/test/tables/Accounts.java'))
		Files.exists(fileSystemFixture.resolve('build/generated-src/jooq/test/test/tables/records/AccountsRecord.java'))
	}
}
