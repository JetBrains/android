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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ddms.screenshot.ScreenshotViewer;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class SaveScreenshotAction extends AnAction {
  private final DesignSurface mySurface;

  public SaveScreenshotAction(DesignSurface surface) {
    super("Save Screenshot...", null, AndroidIcons.Ddms.ScreenCapture);
    mySurface = surface;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getImage() != null && e.getProject() != null);
  }

  @Nullable
  private BufferedImage getImage() {
    ScreenView currentScreenView = mySurface.getCurrentScreenView();
    if (currentScreenView != null) {
      RenderResult result = currentScreenView.getResult();
      if (result != null) {
        return result.getRenderedImage();
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    try {
      BufferedImage image = getImage();
      assert image != null && project != null; // enforced by update() above

      // We need to create a temp file since the image preview editor requires a real file
      File backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG, true);
      ImageIO.write(image, SdkConstants.EXT_PNG, backingFile);

      final ScreenshotViewer viewer = new ScreenshotViewer(project, image, backingFile, null, getDeviceName());
      viewer.showAndGetOk().doWhenDone(new Consumer<Boolean>() {
              @Override
              public void consume(Boolean ok) {
                if (ok) {
                  File screenshot = viewer.getScreenshot();
                  VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(screenshot);
                  if (vf != null) {
                    FileEditorManager.getInstance(project).openFile(vf, true);
                  }
                }
              }
            });
    }
    catch (Exception ex) {
      Messages.showErrorDialog(project, AndroidBundle.message("android.ddms.screenshot.generic.error", e),
                               AndroidBundle.message("android.ddms.actions.screenshot"));
    }
  }

  @Nullable
  private String getDeviceName() {
    Configuration config = mySurface.getConfiguration();
    if (config == null) {
      return null;
    }

    Device device = config.getDevice();
    if (device == null) {
      return null;
    }

    return device.getDisplayName();
  }
}
