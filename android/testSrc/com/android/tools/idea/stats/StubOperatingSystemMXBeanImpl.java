// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.stats;

import com.android.tools.analytics.stubs.StubOperatingSystemMXBean;

public class StubOperatingSystemMXBeanImpl extends StubOperatingSystemMXBean {
  @Override
  public int getAvailableProcessors() {
    return 16;
  }

  public double getCpuLoad() {
    return 0;
  }

  public long getFreeMemorySize() {
    return 1_000_000;
  }

  public long getTotalMemorySize() {
    return 2_000_000;
  }

  @Override
  public long getTotalPhysicalMemorySize() {
    return 16L * 1024 * 1024 * 1024;
  }
}
