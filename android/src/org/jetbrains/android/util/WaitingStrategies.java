// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class WaitingStrategies {
  public static abstract class Strategy {
    private Strategy() {
    }
  }

  public static final class DoNotWait extends Strategy {
    private static final DoNotWait INSTANCE = new DoNotWait();

    private DoNotWait() {
    }

    @NotNull
    public static DoNotWait getInstance() {
      return INSTANCE;
    }
  }

  public static final class WaitForTime extends Strategy {
    private final int myTimeMs;

    private WaitForTime(int timeMs) {
      assert timeMs > 0;
      myTimeMs = timeMs;
    }

    @NotNull
    public static WaitForTime getInstance(int timeMs) {
      return new WaitForTime(timeMs);
    }

    public int getTimeMs() {
      return myTimeMs;
    }
  }

  public static final class WaitForever extends Strategy {
    private static final WaitForever INSTANCE = new WaitForever();

    private WaitForever() {
    }

    @NotNull
    public static WaitForever getInstance() {
      return INSTANCE;
    }
  }
}
