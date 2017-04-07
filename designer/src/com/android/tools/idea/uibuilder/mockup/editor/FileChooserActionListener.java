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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.PixelProbe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Listener for the file chooser
 */
public class FileChooserActionListener implements ActionListener {

  private static final Logger LOGGER = Logger.getInstance(FileChooserActionListener.class);
  private static VirtualFile ourLastOpenedFile = null;

  @Nullable private NlComponent myComponent;
  @Nullable private NlProperty myFilePathProperty;
  private NlProperty myCropProperty;

  @Override
  public void actionPerformed(ActionEvent e) {

    if (myFilePathProperty == null) {
      return;
    }
    final FileChooserDescriptor descriptor = MockupFileHelper.getFileChooserDescriptor();
    VirtualFile selectedFile = myFilePathProperty.getValue() != null
                               ? VfsUtil.findFileByIoFile(new File(FileUtil.toSystemIndependentName(myFilePathProperty.getValue())), false)
                               : ourLastOpenedFile;

    FileChooser.chooseFile(
      descriptor, null, null, selectedFile,
      (virtualFile) -> {
        ourLastOpenedFile = virtualFile;
        if (myComponent != null && myComponent.isRoot()) {
          openDeviceChoiceDialog(virtualFile, myFilePathProperty, myCropProperty);
        }
        else {
          saveMockupFile(virtualFile, myFilePathProperty, myCropProperty);
          if (e == null) {
            return;
          }
          final TextAccessor textAccessor = e.getSource() instanceof TextAccessor ? ((TextAccessor)e.getSource()) : null;
          if (textAccessor != null) {
            textAccessor.setText(virtualFile.getPath());
          }
        }
      });
  }

  /**
   * Open a dialog asking to choose a device whose dimensions match those of the image
   */
  private static void openDeviceChoiceDialog(VirtualFile virtualFile, @NotNull NlProperty fileProperty, @Nullable NlProperty cropProperty) {
    if (virtualFile.exists() && !virtualFile.isDirectory()) {
      try {
        final Image probe = PixelProbe.probe(virtualFile.getInputStream());
        final BufferedImage image = probe.getMergedImage();
        if (image == null) {
          return;
        }
        final NlModel model = fileProperty.getModel();
        final Configuration configuration = model.getConfiguration();
        final Device device = configuration.getDevice();
        if (device == null) {
          return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          final DeviceSelectionPopup deviceSelectionPopup =
            new DeviceSelectionPopup(model.getProject(), configuration, image);
          if (deviceSelectionPopup.showAndGet()) {
            saveMockupFile(virtualFile, fileProperty, cropProperty);
          }
        });
      }
      catch (IOException e1) {
        LOGGER.warn("Unable to open this file\n" + e1.getMessage());
      }
    }
  }

  private static void saveMockupFile(@NotNull VirtualFile virtualFile, @NotNull NlProperty filePath, NlProperty cropProperty) {
    if (cropProperty != null) {
      cropProperty.setValue("");
    }
    filePath.setValue(MockupFileHelper.getXMLFilePath(filePath.getModel().getProject(), virtualFile.getPath()));
  }

  public void setFilePathProperty(@Nullable NlProperty filePathProperty) {
    myFilePathProperty = filePathProperty;
    if (myFilePathProperty == null) {
      myComponent = null;
      return;
    }
    List<NlComponent> components = filePathProperty.getComponents();
    if (!components.isEmpty()) {
      myComponent = components.get(0);
    }
  }

  public void setCropProperty(@Nullable NlProperty cropProperty) {
    myCropProperty = cropProperty;
  }
}
