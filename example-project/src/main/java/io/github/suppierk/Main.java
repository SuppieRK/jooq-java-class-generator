package io.github.suppierk;

import io.github.suppierk.example.tables.records.AccountsRecord;

/**
 * Test class to prove that code generation works as expected.
 *
 * <p>If you just checked out the project, here you will get a compilation error until you execute
 * {@code gradle clean build}.
 */
@SuppressWarnings("squid:S106")
public class Main {
  /**
   * Example app entrypoint to consume and display new jOOQ generated content.
   *
   * @param args of the app, not required
   */
  public static void main(String[] args) {
    System.out.println(new AccountsRecord());
  }
}
