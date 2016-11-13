/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class StageView {
  final private Stage myStage;

  public StageView(@NotNull Stage stage) {
    myStage = stage;
  }

  @NotNull
  public Stage getStage() {
    return myStage;
  }

  @NotNull
  abstract public JComponent getComponent();

  abstract public JComponent getToolbar();

  protected void returnToStudioStage() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    StudioMonitorStage monitor = new StudioMonitorStage(profilers);
    profilers.setStage(monitor);
  }

  /**
   * A purely visual concept as to whether this stage wants the "process and devices" selection being shown to the user.
   * It is not possible to assume processes won't change while a stage is running. For example: a process dying.
   */
  public boolean needsProcessSelection() {
    return false;
  }
}
