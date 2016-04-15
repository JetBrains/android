/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NoLaunch extends LaunchOption<NoLaunch.State> {
  public static final NoLaunch INSTANCE = new NoLaunch();

  public static class State extends LaunchOptionState {
    @Nullable
    @Override
    public LaunchTask getLaunchTask(@NotNull String applicationId,
                                    @NotNull AndroidFacet facet,
                                    @NotNull StartActivityFlagsProvider startActivityFlagsProvider,
                                    @NotNull ProfilerState profilerState) {
      return null;
    }
  }

  @NotNull
  @Override
  public String getId() {
    return AndroidRunConfiguration.DO_NOTHING;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Nothing";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @NotNull
  @Override
  public LaunchOptionConfigurable<State> createConfigurable(@NotNull Project project, @NotNull LaunchOptionConfigurableContext context) {
    return new LaunchOptionConfigurable<State>() {
      @Nullable
      @Override
      public JComponent createComponent() {
        return null;
      }

      @Override
      public void resetFrom(@NotNull State state) {
      }

      @Override
      public void applyTo(@NotNull State state) {
      }
    };
  }
}
