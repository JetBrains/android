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
import com.android.io.Images;
import com.android.resources.ScreenOrientation;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.device.DeviceArtDescriptor;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.screenshot.DeviceArtFramingOption;
import com.android.tools.idea.ddms.screenshot.DeviceArtScreenshotPostprocessor;
import com.android.tools.idea.ddms.screenshot.DeviceScreenshotSupplier;
import com.android.tools.idea.ddms.screenshot.FramingOption;
import com.android.tools.idea.ddms.screenshot.ScreenshotImage;
import com.android.tools.idea.ddms.screenshot.ScreenshotPostprocessor;
import com.android.tools.idea.ddms.screenshot.ScreenshotSupplier;
import com.android.tools.idea.ddms.screenshot.ScreenshotTask;
import com.android.tools.idea.ddms.screenshot.ScreenshotViewer;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Captures a screenshot of the device display.
 */
public class DeviceScreenshotAction extends AbstractDeviceAction {
  private final Project myProject;

  public DeviceScreenshotAction(@NotNull Project project, @NotNull DeviceContext context) {
    super(context, AndroidBundle.message("android.ddms.actions.screenshot"),
          AndroidBundle.message("android.ddms.actions.screenshot.description"),
          StudioIcons.Logcat.SNAPSHOT);
    myProject = project;
  }

  @Override
  protected void performAction(@NotNull AnActionEvent event, @NotNull IDevice device) {
    Project project = myProject;

    new ScreenshotTask(project, new DeviceScreenshotSupplier(device)) {
      @Override
      public void onSuccess() {
        String msg = getError();
        if (msg != null) {
          Messages.showErrorDialog(project, msg, AndroidBundle.message("android.ddms.actions.screenshot.title"));
          return;
        }

        try {
          Path backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath();
          ScreenshotImage screenshotImage = getScreenshot();
          assert screenshotImage != null;
          BufferedImage image = screenshotImage.getImage();
          Images.writeImage(image, SdkConstants.EXT_PNG, backingFile);

          ScreenshotSupplier screenshotSupplier = new DeviceScreenshotSupplier(device);
          ScreenshotPostprocessor screenshotPostprocessor = new DeviceArtScreenshotPostprocessor();
          List<FramingOption> framingOptions = getFramingOptions(screenshotImage);
          int defaultFrame = getDefaultFrame(framingOptions, screenshotImage, device.getProperty(IDevice.PROP_DEVICE_MODEL));

          ScreenshotViewer viewer = new ScreenshotViewer(project, screenshotImage, backingFile, screenshotSupplier,
                                                         screenshotPostprocessor, framingOptions, defaultFrame,
                                                         EnumSet.of(ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION)) {
            @Override
            protected void doOKAction() {
              super.doOKAction();
              Path screenshotFile = getScreenshot();
              if (screenshotFile != null) {
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(screenshotFile);
                if (virtualFile != null) {
                  virtualFile.refresh(false, false);
                  FileEditorManager.getInstance(project).openFile(virtualFile, true);
                }
              }
            }
          };

          viewer.show();
        }
        catch (Exception e) {
          Logger.getInstance(DeviceScreenshotAction.class).warn("Error while displaying screenshot viewer: ", e);
          Messages.showErrorDialog(project,
                                   AndroidBundle.message("android.ddms.screenshot.generic.error", e),
                                   AndroidBundle.message("android.ddms.actions.screenshot.title"));
        }
      }
    }.queue();
  }

  /** Returns the list of available frames for the given image. */
  private static @NotNull List<FramingOption> getFramingOptions(@NotNull ScreenshotImage screenshotImage) {
    double imgAspectRatio = screenshotImage.getWidth() / (double)screenshotImage.getHeight();
    ScreenOrientation orientation = imgAspectRatio >= (1 - ImageUtils.EPSILON) ? ScreenOrientation.LANDSCAPE : ScreenOrientation.PORTRAIT;

    List<DeviceArtDescriptor> allDescriptors = DeviceArtDescriptor.getDescriptors(null);
    ImmutableList.Builder<FramingOption> framingOptions = ImmutableList.builder();
    for (DeviceArtDescriptor descriptor : allDescriptors) {
      if (descriptor.canFrameImage(screenshotImage.getImage(), orientation)) {
        framingOptions.add(new DeviceArtFramingOption(descriptor));
      }
    }
    return framingOptions.build();
  }

  private static int getDefaultFrame(@NotNull List<@NotNull FramingOption> frames, @NotNull ScreenshotImage screenshotImage,
                                     @Nullable String deviceModel) {
    int index = -1;

    if (deviceModel != null) {
      index = findFrameIndexForDeviceModel(frames, deviceModel);
    }

    if (index < 0) {
      // Assume that if the min resolution is > 1280, then we are on a tablet.
      String defaultDevice = Math.min(screenshotImage.getWidth(), screenshotImage.getHeight()) > 1280 ? "Generic Tablet" : "Generic Phone";
      index = findFrameIndexForDeviceModel(frames, defaultDevice);
    }

    // If we can't find anything (which shouldn't happen since we should get the Generic Phone/Tablet),
    // default to the first one.
    if (index < 0) {
      index = 0;
    }

    return index;
  }

  private static int findFrameIndexForDeviceModel(@NotNull List<FramingOption> frames, @NotNull String deviceModel) {
    for (int i = 0; i < frames.size(); i++) {
      FramingOption frame = frames.get(i);
      if (frame.getDisplayName().equalsIgnoreCase(deviceModel)) {
        return i;
      }
    }
    return -1;
  }
}
