package com.mythreadcontrollingjar;

public class ThreadCreator {
  public static Thread createIllegalThread() {
    return new Thread();
  }

  public static CustomThread createCustomIllegalThread() {
    return new CustomThread();
  }

  public static Thread createCoroutineThread() {
    return new Thread(() -> {}, "kotlinx.coroutines.DefaultExecutor");
  }
}