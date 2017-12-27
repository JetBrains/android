/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.logcat.output;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Getter;

public class LogcatOutputSettings implements Getter<LogcatOutputSettings> {
  private static final String LOGCAT_RUN_OUTPUT_ENABLED = "logcat.run.output.enabled";
  private static final boolean LOGCAT_RUN_OUTPUT_ENABLED_DEFAULT = true;
  private static final String LOGCAT_DEBUG_OUTPUT_ENABLED = "logcat.debug.output.enabled";
  private static final boolean LOGCAT_DEBUG_OUTPUT_ENABLED_DEFAULT = true;

  public static LogcatOutputSettings getInstance() {
    return ServiceManager.getService(LogcatOutputSettings.class);
  }

  @Override
  public LogcatOutputSettings get() {
    return this;
  }

  public void reset() {
    PropertiesComponent.getInstance().unsetValue(LOGCAT_RUN_OUTPUT_ENABLED);
    PropertiesComponent.getInstance().unsetValue(LOGCAT_DEBUG_OUTPUT_ENABLED);
  }

  public boolean isRunOutputEnabled() {
    return PropertiesComponent.getInstance().getBoolean(LOGCAT_RUN_OUTPUT_ENABLED, LOGCAT_RUN_OUTPUT_ENABLED_DEFAULT);
  }

  public void setRunOutputEnabled(boolean enabled) {
    PropertiesComponent.getInstance().setValue(LOGCAT_RUN_OUTPUT_ENABLED, enabled, LOGCAT_RUN_OUTPUT_ENABLED_DEFAULT);
  }

  public boolean isDebugOutputEnabled() {
    return PropertiesComponent.getInstance().getBoolean(LOGCAT_DEBUG_OUTPUT_ENABLED, LOGCAT_DEBUG_OUTPUT_ENABLED_DEFAULT);
  }

  public void setDebugOutputEnabled(boolean enabled) {
    PropertiesComponent.getInstance().setValue(LOGCAT_DEBUG_OUTPUT_ENABLED, enabled, LOGCAT_DEBUG_OUTPUT_ENABLED_DEFAULT);
  }

  @Override
  public String toString() {
    return String.format("LogcatOutputSettings{isRunOutputEnabled=%s, isDebugOutputEnabled=%s}",
                         isRunOutputEnabled(), isDebugOutputEnabled());
  }
}