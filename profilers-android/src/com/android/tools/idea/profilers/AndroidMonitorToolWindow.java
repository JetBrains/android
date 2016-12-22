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
package com.android.tools.idea.profilers;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.UncheckedIOException;

public class AndroidMonitorToolWindow extends AspectObserver implements Disposable {

  @NotNull
  private final StudioProfilersView myView;
  @NotNull
  private final StudioProfilers myProfilers;
  @NotNull
  private final Project myProject;

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    try {
      myProject = project;
      Disposer.register(project, this);
      StudioProfilerDeviceManager manager = new StudioProfilerDeviceManager(project);
      myProfilers = new StudioProfilers(manager.getClient(), new IntellijProfilerServices(myProject));

      myProfilers.setPreferredProcessName(getPreferredProcessName(project));
      myView = new StudioProfilersView(myProfilers, new IntellijProfilerComponents(myProject));

      myProfilers.addDependency(this)
        .onChange(ProfilerAspect.MODE, this::updateToolWindow)
        .onChange(ProfilerAspect.STAGE, this::updateToolWindow);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void updateToolWindow() {
    ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
    ToolWindow window = manager.getToolWindow(AndroidMonitorToolWindowFactory.ID);
    boolean maximize = myProfilers.getMode() == ProfilerMode.EXPANDED;
    if (maximize != manager.isMaximized(window)) {
      manager.setMaximized(window, maximize);
    }
  }

  @Override
  public void dispose() {
  }

  public JComponent getComponent() {
    return myView.getComponent();
  }

  @Nullable
  private String getPreferredProcessName(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(module);
      if (moduleInfo != null) {
        String pkg = moduleInfo.getPackage();
        if (pkg != null) {
          return pkg;
        }
      }
    }
    return null;
  }
}