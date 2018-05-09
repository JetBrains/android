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

import com.android.ddmlib.IDevice;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxy;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.*;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.Predicate;

public class AndroidProfilerToolWindow extends AspectObserver implements Disposable {

  @NotNull
  private final StudioProfilersView myView;
  @NotNull
  private final StudioProfilers myProfilers;
  @NotNull
  private final ToolWindow myWindow;
  @NotNull
  private final Project myProject;
  @NotNull
  private final ProfilerLayeredPane myLayeredPane;

  public AndroidProfilerToolWindow(@NotNull ToolWindow window, @NotNull Project project) {
    myWindow = window;
    myProject = project;

    ProfilerService service = ProfilerService.getInstance(myProject);
    ProfilerClient client = service.getProfilerClient();
    myProfilers = new StudioProfilers(client, new IntellijProfilerServices(myProject));

    myView =
      new StudioProfilersView(myProfilers, new IntellijProfilerComponents(myProject, myProfilers.getIdeServices().getFeatureTracker()));
    myLayeredPane = new ProfilerLayeredPane();
    service.getDataStoreService().setNoPiiExceptionHanlder(myProfilers.getIdeServices()::reportNoPiiException);
    initializeUi();
    Disposer.register(this, myView);

    myProfilers.addDependency(this)
               .onChange(ProfilerAspect.MODE, this::modeChanged)
               .onChange(ProfilerAspect.STAGE, this::stageChanged);
    myProfilers.getSessionsManager().addDependency(this)
               .onChange(SessionAspect.SELECTED_SESSION, this::selectedSessionChanged)
               .onChange(SessionAspect.PROFILING_SESSION, this::profilingSessionChanged);
  }

  @NotNull
  StudioProfilers getProfilers() {
    return myProfilers;
  }

  private void initializeUi() {
    JComponent content = myView.getComponent();
    myLayeredPane.add(content, JLayeredPane.DEFAULT_LAYER);
    myLayeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        content.setBounds(0, 0, myLayeredPane.getWidth(), myLayeredPane.getHeight());
        content.revalidate();
        content.repaint();
      }
    });
  }

  /**
   * Sets the profiler's auto-profiling process in case it has been unset.
   *
   * @param module           The module being profiled.
   * @param device           The target {@link IDevice} that the app will launch in.
   * @param processPredicate Additional filter used for choosing the most desirable process. e.g. Process of a particular pid,
   *                         or process that starts after a certain time.
   */
  public void profileModule(@NotNull Module module, @NotNull IDevice device, @Nullable Predicate<Common.Process> processPredicate) {
    myProfilers.setPreferredProcess(getDeviceDisplayName(device), getModuleName(module), processPredicate);
  }

  private void modeChanged() {
    ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
    boolean maximize = myProfilers.getMode() == ProfilerMode.EXPANDED;
    if (maximize != manager.isMaximized(myWindow)) {
      manager.setMaximized(myWindow, maximize);
    }
  }

  private void stageChanged() {
    if (myProfilers.isStopped()) {
      AndroidProfilerToolWindowFactory.removeContent(myWindow);
    }
  }

  private void selectedSessionChanged() {
    Common.SessionMetaData metaData = myProfilers.getSessionsManager().getSelectedSessionMetaData();
    // setTitle appends to the ToolWindow's existing name (i.e. "Profiler"), hence we only
    // need to create and set the string for the session's name.
    myWindow.setTitle(metaData.getSessionName());
  }

  private void profilingSessionChanged() {
    Common.Session profilingSession = myProfilers.getSessionsManager().getProfilingSession();
    if (SessionsManager.isSessionAlive(profilingSession)) {
      myWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER));
    }
    else {
      myWindow.setIcon(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER);
    }
  }

  @Override
  public void dispose() {
    myProfilers.removeDependencies(this);
    myProfilers.stop();
  }

  public JComponent getComponent() {
    return myLayeredPane;
  }

  @Nullable
  private static String getPreferredProcessName(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      String moduleName = getModuleName(module);
      if (moduleName != null) {
        return moduleName;
      }
    }
    return null;
  }

  @Nullable
  private static String getModuleName(@NotNull Module module) {
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
    if (moduleInfo != null) {
      String pkg = moduleInfo.getPackage();
      if (pkg != null) {
        return pkg;
      }
    }
    return null;
  }

  /**
   * Analogous to {@link StudioProfilers#buildDeviceName(Common.Device)} but works with an {@link IDevice} instead.
   *
   * @return A string of the format: {Manufacturer Model}.
   */
  @NotNull
  public static String getDeviceDisplayName(@NotNull IDevice device) {
    StringBuilder deviceNameBuilder = new StringBuilder();
    String manufacturer = ProfilerServiceProxy.getDeviceManufacturer(device);
    String model = ProfilerServiceProxy.getDeviceModel(device);
    String serial = device.getSerialNumber();
    String suffix = String.format("-%s", serial);
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length() - suffix.length());
    }
    if (!StringUtil.isEmpty(manufacturer)) {
      deviceNameBuilder.append(manufacturer);
      deviceNameBuilder.append(" ");
    }
    deviceNameBuilder.append(model);

    return deviceNameBuilder.toString();
  }
}
