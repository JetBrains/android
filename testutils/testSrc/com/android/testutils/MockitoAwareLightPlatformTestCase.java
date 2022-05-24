// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.testutils;

import com.intellij.testFramework.LightPlatformTestCase;

public class MockitoAwareLightPlatformTestCase extends LightPlatformTestCase {
  private final MockitoThreadLocalsCleaner cleaner = new MockitoThreadLocalsCleaner();

  @Override
  protected void setUp() throws Exception {
    cleaner.setup();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      super.tearDown();
    } finally {
      cleaner.cleanupAndTearDown();
    }
  }
}
