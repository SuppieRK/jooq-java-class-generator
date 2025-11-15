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

package io.github.suppierk.codegen.extensions;

import static io.github.suppierk.codegen.util.Extensions.configureWithClosure;

import groovy.lang.Closure;
import io.github.suppierk.codegen.config.FlywayConfig;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

/**
 * Describes schema-scoped configuration: Flyway overrides and the list of jOOQ configurations that
 * should target the schema. Exposed via {@link DatabaseExtension#schema(String, Action)}.
 *
 * @see FlywayConfig
 */
public class SchemaExtension implements Named {
  private final String name;
  private final FlywayConfig flyway = new FlywayConfig();
  private final ListProperty<String> jooqConfigurationsDelegate;
  private final ListProperty<String> jooqConfigurations;

  /** Registered listeners notified whenever the schema's jOOQ configuration list changes. */
  private final List<Action<? super List<String>>> jooqConfigurationListeners = new ArrayList<>();

  /**
   * Tracks whether the list property was mutated at least once to support eager listener firing.
   */
  private boolean jooqConfigurationsConfigured;

  /**
   * Default constructor.
   *
   * @param name unique schema name
   * @param objects Gradle object factory used to instantiate properties
   */
  public SchemaExtension(@Nonnull String name, @Nonnull ObjectFactory objects) {
    this.name = Objects.requireNonNull(name, "name");
    final ListProperty<String> delegate = objects.listProperty(String.class);
    delegate.convention(Collections.emptyList());
    this.jooqConfigurationsDelegate = delegate;
    this.jooqConfigurations = createNotifyingListProperty(delegate);
  }

  /**
   * Creates an immutable list while filtering {@code null} entries. Ensures Gradle properties never
   * observe {@code null} collection elements.
   *
   * @param values incoming values
   * @return immutable snapshot containing only non-null entries
   */
  private static @Nonnull List<String> copyValues(@Nullable Iterable<String> values) {
    if (values == null) {
      return Collections.emptyList();
    }
    final List<String> copy = new ArrayList<>();
    for (String value : values) {
      if (value != null) {
        copy.add(value);
      }
    }
    return List.copyOf(copy);
  }

  /**
   * Returns the schema-specific Flyway configuration that will override any defaults coming from
   * the database entry. Users can mutate the returned object directly in their build script.
   *
   * @return mutable Flyway configuration
   * @see FlywayConfig
   */
  public @Nonnull FlywayConfig getFlyway() {
    return flyway;
  }

  /**
   * Applies configuration to the schema-specific Flyway settings.
   *
   * @param action configuration block
   */
  public void flyway(@Nonnull Action<? super FlywayConfig> action) {
    action.execute(flyway);
  }

  /**
   * Groovy-friendly variant accepting a {@link Closure}.
   *
   * @param closure configuration block
   */
  public void flyway(@Nonnull Closure<?> closure) {
    configureWithClosure(closure, flyway);
  }

  @Override
  public @Nonnull String getName() {
    return name;
  }

  /**
   * Names of jOOQ configurations that should target this schema. The returned {@link ListProperty}
   * participates in Gradle's up-to-date checking and will trigger task registration updates when
   * mutated.
   *
   * @return list property containing configuration names
   */
  public @Nonnull ListProperty<String> getJooqConfigurations() {
    return jooqConfigurations;
  }

  /**
   * Registers an action that will be notified whenever the schema's jOOQ configuration list
   * changes. Used by {@code GeneratorPlugin} to dynamically register or tear down tasks as users
   * mutate the DSL.
   *
   * @param action callback receiving the latest configuration names
   */
  public void whenJooqConfigurationsUpdated(@Nonnull Action<? super List<String>> action) {
    final var callback = Objects.requireNonNull(action, "action");
    jooqConfigurationListeners.add(callback);
    if (jooqConfigurationsConfigured) {
      callback.execute(List.copyOf(jooqConfigurationsDelegate.getOrElse(Collections.emptyList())));
    }
  }

  /**
   * Sets jOOQ configurations targeting the current schema.
   *
   * @param configurations configuration names
   * @see #jooqConfigurations(String...)
   * @see #jooqConfigurations(Iterable)
   */
  public void setJooqConfigurations(@Nullable List<String> configurations) {
    jooqConfigurations.set(copyValues(configurations));
  }

  /**
   * Groovy-friendly varargs setter for jOOQ configurations.
   *
   * @param configurations configuration names
   */
  public void jooqConfigurations(@Nullable String... configurations) {
    jooqConfigurations.set(
        copyValues(configurations == null ? null : Arrays.asList(configurations)));
  }

  /**
   * Groovy-friendly setter accepting {@link Iterable} of configuration names.
   *
   * @param configurations configuration names
   */
  public void jooqConfigurations(@Nullable Iterable<String> configurations) {
    jooqConfigurations.set(copyValues(configurations));
  }

  /**
   * Wraps the provided {@link ListProperty} in a proxy that detects mutations and dispatches change
   * notifications. This allows the DSL to behave like a regular Gradle property while still
   * triggering task reconfiguration.
   *
   * @param delegate original list property
   * @return proxy that mirrors the delegate while emitting events
   */
  @SuppressWarnings("unchecked")
  private @Nonnull ListProperty<String> createNotifyingListProperty(
      @Nonnull ListProperty<String> delegate) {
    final InvocationHandler handler =
        (proxy, method, args) -> {
          final Object result = method.invoke(delegate, args);
          if (isMutationMethod(method)) {
            jooqConfigurationsConfigured = true;
            notifyJooqConfigurationListeners(delegate.getOrElse(Collections.emptyList()));
          }
          return result == delegate ? proxy : result;
        };
    return (ListProperty<String>)
        Proxy.newProxyInstance(
            ListProperty.class.getClassLoader(), new Class[] {ListProperty.class}, handler);
  }

  /**
   * Notifies all registered listeners that the set of jOOQ configurations has changed. Used
   * internally to signal {@code GeneratorPlugin} whenever DSL updates occur.
   *
   * @param configurations latest configuration names
   */
  private void notifyJooqConfigurationListeners(@Nonnull List<String> configurations) {
    if (jooqConfigurationListeners.isEmpty()) {
      return;
    }
    final List<String> snapshot = List.copyOf(configurations);
    for (Action<? super List<String>> listener : jooqConfigurationListeners) {
      listener.execute(snapshot);
    }
  }

  /**
   * Returns {@code true} when the intercepted {@link Method} mutates the {@link ListProperty}.
   * Allows the proxy to determine when listener notifications should be fired.
   *
   * @param method invoked method
   * @return {@code true} if the method mutates the list
   */
  private static boolean isMutationMethod(@Nonnull Method method) {
    final String name = method.getName();
    return name.startsWith("set")
        || name.startsWith("value")
        || name.startsWith("add")
        || name.startsWith("remove")
        || name.startsWith("clear");
  }
}
