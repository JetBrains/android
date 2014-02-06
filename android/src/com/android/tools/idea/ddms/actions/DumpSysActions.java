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

package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DumpSysAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

public class DumpSysActions {
  public static DefaultActionGroup create(@NotNull Project p, @NotNull final DeviceContext context) {
    DefaultActionGroup group = new DefaultActionGroup("System Information", true) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setText("System Information");
        e.getPresentation().setIcon(AndroidIcons.Ddms.SysInfo);
        e.getPresentation().setEnabled(context.getSelectedDevice() != null);
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    group.add(new MyDumpSysAction(p, context, "activity", "Activity Manager State"));
    group.add(new MyDumpSysAction(p, context, "package", "Package Information"));
    group.add(new MyDumpSysAction(p, context, "meminfo", "Memory Usage"));
    group.add(new MyDumpProcStatsAction(p, context, "procstats", "Memory use over time"));
    group.add(new MyDumpSysAction(p, context, "gfxinfo", "Graphics State"));
    return group;
  }

  private static class MyDumpSysAction extends AbstractDeviceAction {
    private final String myService;
    private final Project myProject;

    public MyDumpSysAction(@NotNull Project p, @NotNull DeviceContext context, @NotNull String service, @NotNull String description) {
      super(context, description, null, null);

      myProject = p;
      myService = service;
    }

    @Override
    protected void performAction(@NotNull IDevice device) {
      new DumpSysAction(myProject, myDeviceContext.getSelectedDevice(), myService, myDeviceContext.getSelectedClient()).performAction();
    }
  }

  private static class MyDumpProcStatsAction extends MyDumpSysAction {
    public MyDumpProcStatsAction(@NotNull Project p, @NotNull DeviceContext context, @NotNull String service, @NotNull String description) {
      super(p, context, service, description);
    }

    @Override
    protected boolean isEnabled() {
      return super.isEnabled() &&
             myDeviceContext.getSelectedDevice() != null &&
             myDeviceContext.getSelectedDevice().supportsFeature(IDevice.Feature.PROCSTATS);
    }
  }
}
