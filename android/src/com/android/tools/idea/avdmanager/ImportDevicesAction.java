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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;


/**
 * Action to import devices from a given file
 */
public class ImportDevicesAction extends DeviceUiAction {
  public ImportDevicesAction(@NotNull DeviceProvider provider) {
    super(provider, "Import Hardware Profiles");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true);
    String homePath = System.getProperty("user.home");
    File parentPath = homePath == null ? new File("/") : new File(homePath);
    VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath);
    VirtualFile[] files =
      FileChooserFactory.getInstance().createFileChooser(descriptor, myProvider.getProject(), null).choose(parent, null);
    List<Device> importedDevices = new ArrayList<>();
    for (VirtualFile vf : files) {
      importedDevices.addAll(DeviceManagerConnection.getDevicesFromFile(VfsUtilCore.virtualToIoFile(vf)));
    }
    if (!importedDevices.isEmpty()) {
      DeviceManagerConnection.getDefaultDeviceManagerConnection().createDevices(importedDevices);
      myProvider.refreshDevices();
    }
  }
}
