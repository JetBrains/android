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

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

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
   * Optional, but if set, used for applying one final format pass on output going to the logcat
   * console.
   *
   * Don't set formatting directly; instead,
   * use {@link AndroidLogcatFormatter#createCustomFormat(boolean, boolean, boolean, boolean)}
   *
   * Or, set to an empty string to disable this extra step of processing
   */
  public String LOGCAT_FORMAT_STRING = "";

  public static AndroidLogcatPreferences getInstance(Project project) {
    return ServiceManager.getService(project, AndroidLogcatPreferences.class);
  }

  @Override
  public AndroidLogcatPreferences getState() {
    return this;
  }

  @Override
  public void loadState(AndroidLogcatPreferences object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
