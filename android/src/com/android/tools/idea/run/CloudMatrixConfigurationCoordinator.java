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

import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;

import java.util.List;
import java.util.Set;

public class CloudMatrixConfigurationCoordinator {
  private static CloudMatrixConfigurationCoordinator ourInstance;
  private final Set<CloudMatrixConfigurationComboBox> myComboBoxes = Sets.newHashSet();

  private CloudMatrixConfigurationCoordinator() {
  }

  public static CloudMatrixConfigurationCoordinator getInstance() {
    if (ourInstance == null) {
      ourInstance = new CloudMatrixConfigurationCoordinator();
    }
    return ourInstance;
  }

  public void addComboBox(CloudMatrixConfigurationComboBox comboBox) {
    myComboBoxes.add(comboBox);
  }

  public void removeComboBox(CloudMatrixConfigurationComboBox comboBox) {
    myComboBoxes.remove(comboBox);
  }

  public void updateComboBoxesWithNewTestingConfigurations(List<? extends CloudTestConfiguration> testingConfigurations, Module module) {
    for (CloudMatrixConfigurationComboBox comboBox : myComboBoxes) {
      comboBox.updateTestingConfigurations(testingConfigurations, module);
    }
  }
}
