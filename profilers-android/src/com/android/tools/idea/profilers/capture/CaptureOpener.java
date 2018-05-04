/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture;

import com.android.tools.idea.fileTypes.profiler.CpuCaptureFileType;
import com.android.tools.idea.fileTypes.profiler.MemoryCaptureFileType;
import com.android.tools.idea.profilers.AndroidProfilerToolWindow;
import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

/**
 * Opens an .hprof or .trace file in Android Profiler by creating an imported session. This action is responsible for opening the
 * {@link AndroidProfilerToolWindow} and provide it a file so it can create the session.
 */
public class CaptureOpener extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    ToolWindow window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(AndroidProfilerToolWindowFactory.ID);
    if (window != null) {
      window.setShowStripeButton(true);
      // Makes sure the window is visible because opening a file is an explicit indication that the user wants to view the file,
      // and for that we need the profiler window to be open.
      if (!window.isVisible()) {
        window.show(null);
      }
      AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project);
      if (profilerToolWindow != null) {
        // TODO(b/78888720): getting the file from FileChooserUtil works only when using File -> Open flow. We need to support other flows
        // to open file, such as dragging it into studio and double clicking it in the project structure panel.
        VirtualFile captureFile = FileChooserUtil.getLastOpenedFile(project);
        assertValidCaptureFile(captureFile);
        profilerToolWindow.openFile(captureFile);
      }
    }
  }

  private static void assertValidCaptureFile(VirtualFile file) {
    assert file != null && file.getExtension() != null
           && (file.getExtension().equals(CpuCaptureFileType.EXTENSION) || file.getExtension().equals(MemoryCaptureFileType.EXTENSION));
  }
}
