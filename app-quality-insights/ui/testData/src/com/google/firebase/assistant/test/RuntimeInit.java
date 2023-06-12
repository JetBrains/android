package com.google.firebase.assistant.test;

import java.util.function.Consumer;

public class RuntimeInit {
  public static void foo() {
    String s = null;
    throw createNpe();
  }

  private static NullPointerException createNpe() {
    return new NullPointerException();
  }

  private static void consumeLambda(Consumer<String> consumer) {
    consumer.accept("Test String");
  }
}
