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
package com.android.tools.idea.monitor.tool;

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.UncheckedIOException;

public class AndroidMonitorToolWindow implements Disposable {

  @NotNull
  private final JPanel myComponent;

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    try {
      StudioProfilerDeviceManager manager = new StudioProfilerDeviceManager(project);
      StudioProfilers profiler = new StudioProfilers(manager.getClient());
      profiler.setPreferredProcessName(getPreferredProcessName(project));
      StudioProfilersView view = new StudioProfilersView(profiler);
      myComponent = view.getComponent();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void dispose() {
  }

  public JComponent getComponent() {
    return myComponent;
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