/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.idea.blaze.android.run.runner;

import static com.android.tools.idea.run.TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX;

import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.intellij.openapi.project.Project;

/** A service that provides the {@link DeployTarget} selected in the current project. */
public interface BlazeDeployTargetService {
  static BlazeDeployTargetService getInstance(Project project) {
    return project.getService(BlazeDeployTargetService.class);
  }

  DeployTarget getDeployTarget();

  /** A default implementation using {@link DEVICE_AND_SNAPSHOT_COMBO_BOX} target provider. */
  class DefaultService implements BlazeDeployTargetService {
    public static final TargetSelectionMode SELECTION_MODE = DEVICE_AND_SNAPSHOT_COMBO_BOX;
    Project project;

    public DefaultService(Project project) {
      this.project = project;
    }

    @Override
    public DeployTarget getDeployTarget() {
      for (DeployTargetProvider provider : DeployTargetProvider.getProviders()) {
        if (provider.getId().equals(SELECTION_MODE.toString())) {
          return provider.getDeployTarget(project);
        }
      }
      throw new IllegalStateException(
          "No DeployTargetProvider available for selection mode" + SELECTION_MODE);
    }
  }
}
