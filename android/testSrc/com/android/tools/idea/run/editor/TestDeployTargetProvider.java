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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.TargetSelectionMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TestDeployTargetProvider extends DeployTargetProvider {
  @NotNull
  private final TargetSelectionMode myMode;

  @NotNull
  private final String myDisplayName;

  TestDeployTargetProvider(@NotNull TargetSelectionMode mode, @NotNull String displayName) {
    myMode = mode;
    myDisplayName = displayName;
  }

  @NotNull
  @Override
  public final String getId() {
    return myMode.name();
  }

  @NotNull
  @Override
  public final String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  @Override
  public final DeployTargetState createState() {
    return new State();
  }

  private static final class State extends DeployTargetState {
  }

  @NotNull
  @Override
  public final DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                           @NotNull Disposable parent,
                                                           @NotNull DeployTargetConfigurableContext context) {
    return new Configurable();
  }

  private static final class Configurable implements DeployTargetConfigurable {
    @Nullable
    @Override
    public JComponent createComponent() {
      return null;
    }

    @Override
    public void resetFrom(@NotNull Object state, int id) {
    }

    @Override
    public void applyTo(@NotNull Object state, int id) {
    }
  }

  @NotNull
  @Override
  public final DeployTarget getDeployTarget() {
    throw new UnsupportedOperationException();
  }
}
