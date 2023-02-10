/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.diagnostics.crash;

import com.android.tools.analytics.Anonymizer;
import com.android.tools.analytics.crash.GoogleCrashReporter;
import com.android.tools.idea.util.StudioPathManager;
import com.android.utils.NullLogger;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PermanentInstallationID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Ide-specific implementation of {@link GoogleCrashReporter} reporter. This implementation uses IDE-specific ambient information to amend
 * the report. Specifically, see {@link #getProductSpecificParams()} method.
 */
public class StudioCrashReporter extends GoogleCrashReporter {

  public static final String PRODUCT_ANDROID_STUDIO = "AndroidStudio"; // must stay in sync with backend registration

  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;
  private static final boolean DEBUG_BUILD = StudioPathManager.isRunningFromSources();

  @Nullable
  private static final String ANONYMIZED_UID = getAnonymizedUid();

  @NotNull
  public static StudioCrashReporter getInstance() {
    return ApplicationManager.getApplication().getService(StudioCrashReporter.class);
  }

  @Nullable
  private static String getAnonymizedUid() {
    if (UNIT_TEST_MODE) {
      return "UnitTest";
    }

    try {
      return Anonymizer.anonymizeUtf8(new NullLogger(), PermanentInstallationID.get());
    }
    catch (IOException e) {
      return null;
    }
  }

  protected StudioCrashReporter() {
    super(UNIT_TEST_MODE, DEBUG_BUILD);
  }

  @NotNull
  @Override
  protected Map<String, String> getProductSpecificParams() {

    Map<String, String> map = new HashMap<>();
    ApplicationInfo applicationInfo = getApplicationInfo();

    if (ANONYMIZED_UID != null) {
      map.put("guid", ANONYMIZED_UID);
    }

    // product specific key value pairs
    map.put(KEY_VERSION, applicationInfo == null ? "0.0.0.0" : applicationInfo.getStrictVersion());
    map.put(KEY_PRODUCT_ID, PRODUCT_ANDROID_STUDIO); // must match registration with Crash
    map.put("fullVersion", applicationInfo == null ? "0.0.0.0" : applicationInfo.getFullVersion());

    return map;
  }

  @Nullable
  private static ApplicationInfo getApplicationInfo() {
    // We obtain the ApplicationInfo only if running with an application instance. Otherwise, a call to a ServiceManager never returns..
    return ApplicationManager.getApplication() == null ? null : ApplicationInfo.getInstance();
  }
}
