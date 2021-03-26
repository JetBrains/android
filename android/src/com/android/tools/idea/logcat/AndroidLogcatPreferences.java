// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.logcat;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
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
   * Optional, but if set, used for applying one final format pass on output going to the logcat
   * console.
   *
   * Don't set formatting directly; instead,
   * use {@link AndroidLogcatFormatter#createCustomFormat(boolean, boolean, boolean, boolean)}
   *
   * Or, set to an empty string to disable this extra step of processing
   */
  public String LOGCAT_FORMAT_STRING = "";

  public boolean SHOW_AS_SECONDS_SINCE_EPOCH;

  public static AndroidLogcatPreferences getInstance(Project project) {
    return project.getService(AndroidLogcatPreferences.class);
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
