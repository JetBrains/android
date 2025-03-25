/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import java.util.Locale;

/** Additional helpers for {@link PlatformUtils}. */
public final class MorePlatformUtils {
  private MorePlatformUtils() {}

  /**
   * Returns an unambiguous IDE product identifier for logging, e.g. IdeaCommunity, IdeaUltimate,
   * CLion, or AndroidStudio.
   */
  public static String getProductIdForLogs() {
    String platformPrefix = PlatformUtils.getPlatformPrefix();

    // IDEA Community Edition is "Idea", whereas IDEA Ultimate Edition is "idea".
    // Let's make them more useful.
    if (PlatformUtils.isIdeaCommunity()) {
      platformPrefix = "IdeaCommunity";
    } else if (PlatformUtils.isIdeaUltimate()) {
      platformPrefix = "IdeaUltimate";
    }
    return platformPrefix;
  }

  public static boolean isAndroidStudio() {
    return "AndroidStudio".equals(PlatformUtils.getPlatformPrefix());
  }

  /**
   * Returns the channel that the IDE was built from, either {@code stable}, {@code beta} or {@code
   * canary}.
   */

  public static String getIdeVersion() {
    ApplicationInfo app = ApplicationInfo.getInstance();
    return app.getMajorVersion() + "." + app.getMinorVersion() + "." + app.getMicroVersion();
  }

  public static String getIdeAbBuildNumber() {
    String fullVersion = ApplicationInfo.getInstance().getBuild().toString();
    String rawAbBuildNumber = StringUtil.substringAfterLast(fullVersion, ".");
    return rawAbBuildNumber != null ? rawAbBuildNumber.toLowerCase(Locale.ROOT) : null; // SNAPSHOT -> snapshot
  }
}
