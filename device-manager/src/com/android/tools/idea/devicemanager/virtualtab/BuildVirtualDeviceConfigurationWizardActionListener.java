/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdOptionsModel;
import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BuildVirtualDeviceConfigurationWizardActionListener implements ActionListener {
  private final @NotNull Component myParent;
  private final @Nullable Project myProject;
  private final @NotNull VirtualDeviceTable myTable;

  BuildVirtualDeviceConfigurationWizardActionListener(@NotNull Component parent,
                                                      @Nullable Project project,
                                                      @NotNull VirtualDeviceTable table) {
    myParent = parent;
    myProject = project;
    myTable = table;
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    AvdOptionsModel model = new AvdOptionsModel(null);

    if (!AvdWizardUtils.createAvdWizard(myParent, myProject, model).showAndGet()) {
      return;
    }

    Futures.addCallback(myTable.addDevice(new VirtualDevicePath(model.getCreatedAvd().getId())),
                        new DeviceManagerFutureCallback<>(BuildVirtualDeviceConfigurationWizardActionListener.class,
                                                          myTable::setSelectedDevice),
                        EdtExecutorService.getInstance());
  }
}
