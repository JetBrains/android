/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.compiler;

import static com.android.tools.idea.gradle.util.GradleBuilds.CONTINUE_BUILD_OPTION;

import com.android.tools.idea.IdeInfo;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AndroidGradleBuildConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class AndroidGradleBuildConfiguration implements PersistentStateComponent<AndroidGradleBuildConfiguration> {
  public String COMMAND_LINE_OPTIONS = "";
  public boolean CONTINUE_FAILED_BUILD = true;
  // Note: This property is only used in IntelliJ Idea. Please do not use it or modify it from a
  public boolean ENABLE_SYNC_WITH_FUTURE_AGP_VERSION = false;

  public static AndroidGradleBuildConfiguration getInstance(Project project) {
    return project.getService(AndroidGradleBuildConfiguration.class);
  }

  @Nullable
  @Override
  public AndroidGradleBuildConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AndroidGradleBuildConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public String[] getCommandLineOptions() {
    List<String> options = new ArrayList<>();
    CommandLineTokenizer tokenizer = new CommandLineTokenizer(COMMAND_LINE_OPTIONS);
    while(tokenizer.hasMoreTokens()) {
      options.add(tokenizer.nextToken());
    }
    if (CONTINUE_FAILED_BUILD && !options.contains(CONTINUE_BUILD_OPTION)) {
      options.add(CONTINUE_BUILD_OPTION);
    }
    return ArrayUtilRt.toStringArray(options);
  }

  /**
   * Determines whether to enable sync of projects with newer Android Gradle Plugin (AGP) version
   * than currently supported AGP version in Android plugin.
   * <p>
   * This setting is intended for use in Intellij IDEA only. For Android Studio, synchronization with
   * future AGP versions is always disabled. For Intellij IDEA, the behavior depends on the value of
   * ENABLE_SYNC_WITH_FUTURE_AGP_VERSION.
   *
   * @return {@code true} if synchronization with future AGP versions is enabled, {@code false} otherwise.
   */
  public boolean isSyncWithFutureAgpVersionEnabled() {
    if (IdeInfo.getInstance().isAndroidStudio()) return false;

    return ENABLE_SYNC_WITH_FUTURE_AGP_VERSION;
  }

  public void setSyncWithFutureAgpVersionIsEnabled(boolean isEnabled) {
    this.ENABLE_SYNC_WITH_FUTURE_AGP_VERSION = isEnabled;
  }
}
