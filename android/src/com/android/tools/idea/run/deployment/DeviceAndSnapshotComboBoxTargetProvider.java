/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.intellij.openapi.project.Project;

public abstract class DeviceAndSnapshotComboBoxTargetProvider extends DeployTargetProvider {

  // This class is written in Java because the kotlin equivalent results in an error much like https://youtrack.jetbrains.com/issue/KT-12993:
  // "java: getInstance() in com.android.tools.idea.run.deployment.selector.DeviceAndSnapshotComboBoxTargetProvider cannot override
  //    getInstance() in com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
  //    overridden method is static,final"
  public static DeviceAndSnapshotComboBoxTargetProvider getInstance() {
      if (StudioFlags.DEPLOYMENT_TARGET_DEVICE_PROVISIONER_MIGRATION.get()) {
        return com.android.tools.idea.run.deployment.selector.DeviceAndSnapshotComboBoxTargetProvider.getInstance();
      } else {
        return com.android.tools.idea.run.deployment.legacyselector.DeviceAndSnapshotComboBoxTargetProvider.getInstance();
      }
  }

  public abstract int getNumberOfSelectedDevices(Project project);
}
