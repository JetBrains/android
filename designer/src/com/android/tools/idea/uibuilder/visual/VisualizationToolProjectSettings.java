/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The project-level settings for visualization (a.k.a. Layout Validation) tool.
 */
@Service(Service.Level.PROJECT)
@State(name = "VisualizationToolProject")
public final class VisualizationToolProjectSettings implements PersistentStateComponent<VisualizationToolProjectSettings.MyState> {
  private ProjectState myProjectState = new ProjectState();

  public static VisualizationToolProjectSettings getInstance(@NotNull Project project) {
    return project.getService(VisualizationToolProjectSettings.class);
  }

  @NotNull
  public ProjectState getProjectState() {
    return myProjectState;
  }

  @Override
  public MyState getState() {
    final MyState state = new MyState();
    state.setState(myProjectState);
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myProjectState = state.getState();
  }

  public static class MyState {
    private ProjectState myProjectState = new ProjectState();

    public ProjectState getState() {
      return myProjectState;
    }

    public void setState(ProjectState state) {
      myProjectState = state;
    }
  }

  public static class ProjectState {
    private double myScale = 0.25;

    public double getScale() {
      return myScale;
    }

    public void setScale(double scale) {
      myScale = scale;
    }
  }
}
