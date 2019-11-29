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

import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.ddms.DeviceNamePropertiesProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import java.util.Objects;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

final class NameGetter implements Function<ConnectedDevice, String> {
  @NotNull
  private final DeviceNamePropertiesProvider myProvider;

  NameGetter(@NotNull Disposable parent) {
    myProvider = new DeviceNamePropertiesFetcher(new DefaultCallback<>(), parent);
  }

  @NotNull
  @Override
  public String apply(@NotNull ConnectedDevice device) {
    assert device.isPhysicalDevice();

    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return "Physical Device";
    }

    return getName(myProvider.get(Objects.requireNonNull(device.getDdmlibDevice())));
  }

  @NotNull
  @VisibleForTesting
  static String getName(@NotNull DeviceNameProperties properties) {
    String manufacturer = properties.getManufacturer();
    String model = properties.getModel();

    if (manufacturer == null && model == null) {
      return "Physical Device";
    }

    if (manufacturer == null) {
      return model;
    }

    if (model == null) {
      return manufacturer + " Device";
    }

    return manufacturer + ' ' + model;
  }
}
