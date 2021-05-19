/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.layoutlib;

import com.intellij.ide.plugins.PluginManagerCore;
//import com.intellij.internal.statistic.analytics.StudioCrashDetails; FIXME-ank2: missing intellij.platform.bootstrap
//import com.intellij.internal.statistic.analytics.StudioCrashDetection;  FIXME-ank2: missing intellij.platform.bootstrap
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class NativeCrashHandling implements BaseComponent {

  @Override
  public void initComponent() {
    // If the previous run of Studio ended on a JVM crash, disable layoutlib native.
    // FIXME-ank2: use layoutlib-standard by default.
    //List<StudioCrashDetails> crashes = StudioCrashDetection.reapCrashDescriptions();
    //for (StudioCrashDetails crash : crashes) {
    //  if (isCrashCausedByLayoutlib(crash)) {
    //    PluginManagerCore.disablePlugin("com.android.layoutlib.native");
    //    PluginManagerCore.enablePlugin("com.android.layoutlib.standard");
    //    ApplicationManager.getApplication().restart();
    //  }
    //}
  }

  private static boolean isCrashCausedByLayoutlib(@NotNull StudioCrashDetails crash) {
    //return crash.isJvmCrash() &&
    //       (crash.getErrorThread().contains("Layoutlib Render Thread") || crash.getErrorFrame().contains("libandroid_runtime"));
    return false;
  }
}
