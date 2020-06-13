// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.npw.template;

import static org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt.bundledRuntimeVersion;

public class KotlinVersionProvider {
  private final String baseVersion;

  public static KotlinVersionProvider getInstance() {
    return new KotlinVersionProvider(bundledRuntimeVersion());
  }

  public KotlinVersionProvider(String baseVersion) {
    this.baseVersion = baseVersion;
  }

  public String getKotlinVersionForGradle() {
    String version = baseVersion;
    if (version.contains("-release-")) {
      version = version.substring(0, version.indexOf("-release-"));
    }
    return version;
  }
}
