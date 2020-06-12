// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.mockito;

import java.util.concurrent.Callable;

/**
 * Allows to control which {@linkplain org.mockito.plugins.MockMaker} will be used by the {@linkplain SwitchingMockMaker} to create mocks.
 *
 * @see SwitchingMockMaker
 */
public class MockitoEx {
  static boolean forceInlineMockMaker = false;

  /**
   * Enforce inline mock maker for all the mocks created during {@linkplain Runnable} execution.
   * <p>
   * Not thread safe
   *
   * @param r {@linkplain Runnable} that creates some mocks.
   */
  public static void forceInlineMockMaker(Runnable r) {
    try {
      forceInlineMockMaker = true;
      r.run();
    }
    finally {
      forceInlineMockMaker = false;
    }
  }

  /**
   * Enforce inline mock maker for all the mocks created during {@linkplain Callable} execution.
   * <p>
   * Not thread safe
   *
   * @param c {@linkplain Callable} that creates some mocks.
   */
  public static <T> T forceInlineMockMaker(Callable<T> c) throws Exception {
    try {
      forceInlineMockMaker = true;
      return c.call();
    }
    finally {
      forceInlineMockMaker = false;
    }
  }
}
