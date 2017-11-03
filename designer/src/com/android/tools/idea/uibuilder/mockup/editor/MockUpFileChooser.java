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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.PixelProbe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import static com.android.SdkConstants.ATTR_MOCKUP;
import static com.android.SdkConstants.TOOLS_URI;

/**
 * File chooser for MockUps
 */
public class MockUpFileChooser {
  public static final MockUpFileChooser INSTANCE = new MockUpFileChooser();

  private String myLastOpenedFileName = null;

  private MockUpFileChooser() {}

  public void chooseMockUpFile(@NotNull NlComponent component, @NotNull Consumer<String> callback) {
    String fileName = component.getAttribute(TOOLS_URI, ATTR_MOCKUP);
    VirtualFile file = fileName != null ? getVirtualFile(fileName) : null;
    if (file == null && myLastOpenedFileName != null) {
      file = getVirtualFile(myLastOpenedFileName);
    }
    FileChooser.chooseFile(
      MockupFileHelper.getFileChooserDescriptor(),
      null,
      null,
      file,
      (virtualFile) -> handleFileSelection(component, virtualFile, callback));
  }

  private void handleFileSelection(@NotNull NlComponent component,
                                   @NotNull VirtualFile virtualFile,
                                   @NotNull Consumer<String> callback) {
    if (!virtualFile.exists() || virtualFile.isDirectory()) {
      return;
    }
    myLastOpenedFileName = virtualFile.getPath();
    if (component.isRoot()) {
      openDeviceChoiceDialog(component, virtualFile, callback);
    }
    else {
      accept(component, virtualFile, callback);
    }
  }

  private static void accept(@NotNull NlComponent component, @NotNull VirtualFile virtualFile, @NotNull Consumer<String> callback) {
    Path path = MockupFileHelper.getXMLFilePath(component.getModel().getProject(), virtualFile.getPath());
    if (path != null) {
      callback.accept(path.toString());
    }
  }

  /**
   * Open a dialog asking to choose a device whose dimensions match those of the image
   */
  private static void openDeviceChoiceDialog(@NotNull NlComponent component,
                                             @NotNull VirtualFile virtualFile,
                                             @NotNull Consumer<String> callback) {
    try {
      Image probe = PixelProbe.probe(virtualFile.getInputStream());
      BufferedImage image = probe.getMergedImage();
      if (image == null) {
        return;
      }
      NlModel model = component.getModel();
      Configuration configuration = model.getConfiguration();
      Device device = configuration.getDevice();
      if (device == null) {
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        DeviceSelectionPopup deviceSelectionPopup = new DeviceSelectionPopup(model.getProject(), configuration, image);
        if (deviceSelectionPopup.showAndGet()) {
          accept(component, virtualFile, callback);
        }
      });
    }
    catch (IOException e1) {
      Logger.getInstance(MockUpFileChooser.class).warn("Unable to open this file\n" + e1.getMessage());
    }
  }

  private static VirtualFile getVirtualFile(@NotNull String fileName) {
    return VfsUtil.findFileByIoFile(new File(FileUtil.toSystemIndependentName(fileName)), false);
  }
}
