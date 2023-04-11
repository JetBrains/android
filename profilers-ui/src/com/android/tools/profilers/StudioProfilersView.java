/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.intellij.openapi.Disposable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StudioProfilersView implements Disposable {

  @NotNull
  protected final StudioProfilers myProfiler;

  @NotNull
  protected final IdeProfilerComponents myIdeProfilerComponents;

  protected StudioProfilersView(@NotNull StudioProfilers profilers, @NotNull IdeProfilerComponents profilerComponents) {
    myProfiler = profilers;
    myIdeProfilerComponents = profilerComponents;
  }

  @NotNull
  public abstract JComponent getComponent();
  public abstract void installCommonMenuItems(@NotNull JComponent component);
  @NotNull
  public abstract StageWithToolbarView getStageWithToolbarView();
  @NotNull
  public abstract JPanel getStageComponent();
  @Nullable
  public abstract StageView getStageView();

  @NotNull
  public StudioProfilers getStudioProfilers() {
    return myProfiler;
  }

  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }
}
