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
package com.android.tools.idea.run;

import com.android.tools.idea.run.CloudConfiguration.Kind;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CloudConfigurationCoordinator {
  private static Map<Kind, CloudConfigurationCoordinator> ourInstances = Maps.newHashMap();
  private final Set<CloudConfigurationComboBox> myComboBoxes = Sets.newHashSet();

  private CloudConfigurationCoordinator() {
  }

  public static CloudConfigurationCoordinator getInstance(Kind kind) {
    CloudConfigurationCoordinator instance = ourInstances.get(kind);
    if (instance == null) {
      instance = new CloudConfigurationCoordinator();
      ourInstances.put(kind, instance);
    }
    return instance;
  }

  public void addComboBox(CloudConfigurationComboBox comboBox) {
    myComboBoxes.add(comboBox);
  }

  public void removeComboBox(CloudConfigurationComboBox comboBox) {
    myComboBoxes.remove(comboBox);
  }

  public void updateComboBoxesWithNewCloudConfigurations(List<? extends CloudConfiguration> cloudConfigurations, Module module) {
    for (CloudConfigurationComboBox comboBox : myComboBoxes) {
      comboBox.updateCloudConfigurations(cloudConfigurations, module);
    }
  }
}
