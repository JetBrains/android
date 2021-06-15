/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.logcat;

import com.android.tools.idea.util.xmlb.LogcatHeaderFormatConverter;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent storage for the state of the logcat view UI.
 */
@State(name = "AndroidLogFilters", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class AndroidLogcatPreferences implements PersistentStateComponent<AndroidLogcatPreferences> {
  public String TOOL_WINDOW_CUSTOM_FILTER = "";
  public String TOOL_WINDOW_LOG_LEVEL = "VERBOSE";
  public String TOOL_WINDOW_CONFIGURED_FILTER = "";
  public boolean TOOL_WINDOW_REGEXP_FILTER = true;
  /**
   * Specify the format of a logcat message in the console
   */
  @OptionTag(converter = LogcatHeaderFormatConverter.class)
  public LogcatHeaderFormat LOGCAT_HEADER_FORMAT = new LogcatHeaderFormat();

  public static AndroidLogcatPreferences getInstance(Project project) {
    return ServiceManager.getService(project, AndroidLogcatPreferences.class);
  }

  @Override
  public AndroidLogcatPreferences getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AndroidLogcatPreferences object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
