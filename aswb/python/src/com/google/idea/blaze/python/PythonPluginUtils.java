/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.util.PlatformUtils;
import java.util.Map;
import javax.annotation.Nullable;

/** Utilities class related to the JetBrains python plugin. */
public final class PythonPluginUtils {

  private PythonPluginUtils() {}

  // Order is important, the first matching one will be used.
  private static final ImmutableMap<IDEVersion, String> PRODUCT_TO_PLUGIN_ID =
      ImmutableMap.<IDEVersion, String>builder()
          .put(
              new IDEVersion(PlatformUtils.CLION_PREFIX, "2019", "2.5"),
              "com.intellij.clion-python")
          .put(new IDEVersion(PlatformUtils.CLION_PREFIX), "PythonCore")
          .put(new IDEVersion(PlatformUtils.IDEA_PREFIX), "Pythonid")
          .put(new IDEVersion(PlatformUtils.IDEA_CE_PREFIX), "PythonCore")
          .put(new IDEVersion(PlatformUtils.GOIDE_PREFIX), "PythonCore")
          .put(new IDEVersion(PlatformUtils.RIDER_PREFIX), "PythonCore")
          .put(new IDEVersion(PlatformUtils.DBE_PREFIX), "PythonCore")
          .put(new IDEVersion("AndroidStudio"), "PythonCore")
          .build();

  /** The python plugin ID for this IDE, or null if not available/relevant. */
  @Nullable
  public static String getPythonPluginId() {
    for (Map.Entry<IDEVersion, String> pythonPluginId : PRODUCT_TO_PLUGIN_ID.entrySet()) {
      if (pythonPluginId.getKey().matchesCurrent()) {
        return pythonPluginId.getValue();
      }
    }
    return null;
  }

  private static class IDEVersion {
    private final String prefix;

    @Nullable private final String majorVersion;
    @Nullable private final String minorVersion;

    public IDEVersion(String prefix) {
      this(prefix, null, null);
    }

    public IDEVersion(String prefix, @Nullable String majorVersion, @Nullable String minorVersion) {
      this.prefix = prefix;
      this.majorVersion = majorVersion;
      this.minorVersion = minorVersion;
    }

    public boolean matchesCurrent() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      return PlatformUtils.getPlatformPrefix().equals(prefix)
          && matchesVersion(majorVersion, appInfo.getMajorVersion())
          && matchesVersion(minorVersion, appInfo.getMinorVersion());
    }

    private static boolean matchesVersion(@Nullable String v1, @Nullable String v2) {
      return v1 == null || v2 == null || v1.equals(v2);
    }
  }
}
