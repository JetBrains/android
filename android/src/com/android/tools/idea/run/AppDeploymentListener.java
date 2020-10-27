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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Listener of Android app deployment events.
 */
public interface AppDeploymentListener {
  Topic<AppDeploymentListener> TOPIC = new Topic<>("App deployment events", AppDeploymentListener.class);

  /**
   * Called when an app is deployed to an Android device.
   *
   * @param device the device the app is deployed to
   * @param project the project associated with the deployment
   */
  void appDeployedToDevice(@NotNull IDevice device, @NotNull Project project);
}