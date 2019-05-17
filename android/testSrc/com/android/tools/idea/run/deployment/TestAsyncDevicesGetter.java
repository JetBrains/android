/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

final class TestAsyncDevicesGetter implements AsyncDevicesGetter {
  @NotNull
  private List<Device> myDevices = Collections.emptyList();

  @NotNull
  static TestAsyncDevicesGetter getService(@NotNull Project project) {
    return (TestAsyncDevicesGetter)ServiceManager.getService(project, AsyncDevicesGetter.class);
  }

  @NotNull
  @Override
  public List<Device> get() {
    return myDevices;
  }

  void set(@NotNull List<Device> devices) {
    myDevices = devices;
  }
}
