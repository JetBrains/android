/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Device;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.io.File;


/**
 * Action to export a given device to a file
 */
public class ExportDeviceAction extends DeviceUiAction {
  public ExportDeviceAction(@NotNull DeviceProvider provider) {
    super(provider, "Export");
  }

  @Override
  public boolean isEnabled() {
    Device device = myProvider.getDevice();
    return device != null;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    FileSaverDescriptor descriptor = new FileSaverDescriptor("Export Location", "Select a location for the exported device", "xml");
    String homePath = System.getProperty("user.home");
    File parentPath = homePath == null ? new File("/") : new File(homePath);
    VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath);
    VirtualFileWrapper fileWrapper =
      FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProvider.getProject()).save(parent, "device.xml");
    Device device = myProvider.getDevice();
    if (device != null && fileWrapper != null) {
      DeviceManagerConnection.writeDevicesToFile(ImmutableList.of(device), fileWrapper.getFile());
    }
  }
}
