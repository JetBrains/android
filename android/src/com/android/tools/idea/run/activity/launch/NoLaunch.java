/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ApkProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NoLaunch extends LaunchOption<NoLaunch.State> {
  public static final NoLaunch INSTANCE = new NoLaunch();

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

  public static class State extends LaunchOptionState {

    @NotNull
    @Override
    public String getId() {
      return "NO_LAUNCH";
    }

    @Override
    protected boolean doLaunch(@NotNull IDevice device,
                            @NotNull App app,
                            @NotNull ApkProvider apkProvider,
                            boolean isDebug,
                            @NotNull String extraFlags,
                            @NotNull ConsoleView console) throws ExecutionException {
      return false;
    }
  }
}
