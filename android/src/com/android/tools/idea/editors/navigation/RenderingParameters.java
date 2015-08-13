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
import com.android.tools.idea.editors.navigation.model.ModelDimension;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.editors.navigation.NavigationEditorUtils.ZERO_SIZE;
import static com.android.tools.idea.editors.navigation.NavigationEditorUtils.notNull;

public class RenderingParameters {
  @NotNull
  public final Project project;
  @NotNull
  public final AndroidFacet facet;
  @NotNull
  public final Configuration configuration;

  public RenderingParameters(@NotNull AndroidFacet facet, @NotNull Configuration configuration) {
    this.project = facet.getModule().getProject();
    this.facet = facet;
    this.configuration = configuration;
  }

  public RenderingParameters withConfiguration(Configuration configuration) {
    return new RenderingParameters(facet, configuration);
  }

  ModelDimension getDeviceScreenSize() {
    return ModelDimension.create(getDeviceScreenSize1());
  }

  private Dimension getDeviceScreenSize1() {
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
