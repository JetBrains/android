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
package com.android.tools.idea.editors.navigation;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.editors.navigation.Utilities.ZERO_SIZE;
import static com.android.tools.idea.editors.navigation.Utilities.notNull;

public class RenderingParameters {
  @NotNull final Project myProject;
  @NotNull final Configuration myConfiguration;
  @NotNull final AndroidFacet myFacet;

  public RenderingParameters(@NotNull Project project, @NotNull Configuration configuration, @NotNull AndroidFacet facet) {
    this.myProject = project;
    this.myConfiguration = configuration;
    this.myFacet = facet;
  }

  public RenderingParameters withConfiguration(Configuration configuration) {
    return new RenderingParameters(myProject, configuration, myFacet);
  }

  com.android.navigation.Dimension getDeviceScreenSize() {
    return com.android.navigation.Dimension.create(getDeviceScreenSize1());
  }

  private Dimension getDeviceScreenSize1() {
    Configuration configuration = myConfiguration;
    Device device = configuration.getDevice();
    if (device == null) {
      return ZERO_SIZE;
    }
    State deviceState = configuration.getDeviceState();
    if (deviceState == null) {
      deviceState = device.getDefaultState();
    }
    return notNull(device.getScreenSize(deviceState.getOrientation()));
  }

  Dimension getDeviceScreenSizeFor(Transform transform) {
    return transform.modelToView(getDeviceScreenSize());
  }
}
