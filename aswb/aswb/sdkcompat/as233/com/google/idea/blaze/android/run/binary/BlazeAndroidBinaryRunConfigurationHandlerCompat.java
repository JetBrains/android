/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.execution.common.DeployableToDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;

/** Compat class for {@link BlazeAndroidBinaryRunConfigurationHandlerCompat}. */
public class BlazeAndroidBinaryRunConfigurationHandlerCompat {
  protected final Project project;
  protected final BlazeAndroidBinaryRunConfigurationState configState;

  @VisibleForTesting
  protected BlazeAndroidBinaryRunConfigurationHandlerCompat(
      BlazeCommandRunConfiguration configuration) {
    project = configuration.getProject();
    configState =
        new BlazeAndroidBinaryRunConfigurationState(
            Blaze.buildSystemName(configuration.getProject()));
    configuration.putUserData(DeployableToDevice.getKEY(), true);
  }
}
