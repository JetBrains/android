// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.testutils;

import org.junit.rules.ExternalResource;

public class MockitoCleanerRule extends ExternalResource {
  private final MockitoThreadLocalsCleaner cleaner = new MockitoThreadLocalsCleaner();

  @Override
  protected void before() throws Throwable {
    cleaner.setup();
  }

  @Override
  protected void after() {
    try {
      cleaner.cleanupAndTearDown();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
