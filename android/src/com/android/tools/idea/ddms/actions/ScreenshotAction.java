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

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.screenshot.ScreenshotTask;
import com.android.tools.idea.ddms.screenshot.ScreenshotViewer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.io.File;

public class ScreenshotAction extends AbstractDeviceAction {
  private final Project myProject;

  public ScreenshotAction(@NotNull Project p, @NotNull DeviceContext context) {
    super(context, AndroidBundle.message("android.ddms.actions.screenshot"),
          AndroidBundle.message("android.ddms.actions.screenshot.description"),
          AndroidIcons.Ddms.ScreenCapture); // Alternate: AllIcons.Actions.Dump looks like a camera
    myProject = p;
  }

  @Override
  protected void performAction(@NotNull final IDevice device) {
    final Project project = myProject;

    new ScreenshotTask(project, device) {
      @Override
      public void onSuccess() {
        String msg = getError();
        if (msg != null) {
          Messages.showErrorDialog(project, msg, AndroidBundle.message("android.ddms.actions.screenshot"));
          return;
        }

        try {
          File backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG, true);
          ImageIO.write(getScreenshot(), SdkConstants.EXT_PNG, backingFile);

          final ScreenshotViewer viewer = new ScreenshotViewer(project, getScreenshot(), backingFile, device,
                                                         device.getProperty(IDevice.PROP_DEVICE_MODEL));
          viewer.showAndGetOk().doWhenDone((Consumer<Boolean>)ok -> {
            if (ok) {
              File screenshot = viewer.getScreenshot();
              VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(screenshot);
              if (vf != null) {
                vf.refresh(false, false);
                FileEditorManager.getInstance(project).openFile(vf, true);
              }
            }
          });
        }
        catch (Exception e) {
          Messages.showErrorDialog(project,
                                   AndroidBundle.message("android.ddms.screenshot.generic.error", e),
                                   AndroidBundle.message("android.ddms.actions.screenshot"));
        }
      }
    }.queue();
  }
}
