/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker.messages;

import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "GradleBuildTreeViewConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GradleBuildTreeViewConfiguration implements PersistentStateComponent<GradleBuildTreeViewConfiguration> {
  public boolean SHOW_ERROR_MESSAGES = true;
  public boolean SHOW_WARNING_MESSAGES = true;
  public boolean SHOW_INFO_MESSAGES = true;
  public boolean SHOW_NOTE_MESSAGES = true;
  public boolean SHOW_GENERIC_MESSAGES = true;

  @NotNull
  public static GradleBuildTreeViewConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, GradleBuildTreeViewConfiguration.class);
  }

  @Override
  @NotNull
  public GradleBuildTreeViewConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(GradleBuildTreeViewConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void update(@NotNull ErrorTreeElementKind messageType, boolean show) {
    switch (messageType) {
      case ERROR:
        SHOW_ERROR_MESSAGES = show;
        break;
      case WARNING:
        SHOW_WARNING_MESSAGES = show;
        break;
      case INFO:
        SHOW_INFO_MESSAGES = show;
        break;
      case NOTE:
        SHOW_NOTE_MESSAGES = show;
        break;
      case GENERIC:
        SHOW_GENERIC_MESSAGES = show;
    }
  }

  public boolean canShow(@NotNull ErrorTreeElementKind messageType) {
    switch (messageType) {
      case ERROR:
        return SHOW_ERROR_MESSAGES;
      case WARNING:
        return SHOW_WARNING_MESSAGES;
      case INFO:
        return SHOW_INFO_MESSAGES;
      case NOTE:
        return SHOW_NOTE_MESSAGES;
      case GENERIC:
        return SHOW_GENERIC_MESSAGES;
      default:
        return false;
    }
  }
}
