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
import com.android.tools.profilers.*;
import com.android.tools.profilers.sessions.SessionAspect;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class AndroidProfilerToolWindow extends AspectObserver implements Disposable {

  @NotNull
  private final StudioProfilersView myView;
  @NotNull
  private final StudioProfilers myProfilers;
  @NotNull
  private final Project myProject;
  @NotNull
  private final ProfilerLayeredPane myLayeredPane;

  public AndroidProfilerToolWindow(@NotNull final Project project) {
    myProject = project;

    ProfilerService service = ProfilerService.getInstance(myProject);
    ProfilerClient client = service.getProfilerClient();
    myProfilers = new StudioProfilers(client, new IntellijProfilerServices(myProject));

    myView = new StudioProfilersView(myProfilers, new IntellijProfilerComponents(myProject));
    myLayeredPane = new ProfilerLayeredPane();
    service.getDataStoreService().setNoPiiExceptionHanlder(myProfilers.getIdeServices()::reportNoPiiException);
    initializeUi();

    myProfilers.addDependency(this)
      .onChange(ProfilerAspect.MODE, this::updateToolWindow)
      .onChange(ProfilerAspect.STAGE, this::updateToolWindow);
    myProfilers.getSessionsManager().addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, this::updateToolWindow);
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

  public void profileProject(@NotNull Project project) {
    myProfilers.setPreferredProcessName(getPreferredProcessName(project));
  }

  public void updateToolWindow() {
    ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
    ToolWindow window = manager.getToolWindow(AndroidProfilerToolWindowFactory.ID);
    if (window == null) {
      return;
    }

    String sessionName = myProfilers.getSessionDisplayName();
    if (sessionName.isEmpty()) {
      window.setTitle("");
    }
    else {
      // setTitle appends to the ToolWindow's existing name (i.e. "Profiler"), hence we only
      // need to create and set the string for "- SESSION_NAME".
      window.setTitle(String.format("- %s", sessionName));
    }

    boolean maximize = myProfilers.getMode() == ProfilerMode.EXPANDED;
    if (maximize != manager.isMaximized(window)) {
      manager.setMaximized(window, maximize);
    }
    if (myProfilers.isStopped()) {
      AndroidProfilerToolWindowFactory.removeContent(myProject, window);
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
  private static String getPreferredProcessName(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
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
