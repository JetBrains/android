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

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Listener of events indicating that a device requires user attention, for example, an app deployment or launch.
 */
public interface DeviceHeadsUpListener {
  Topic<DeviceHeadsUpListener> TOPIC = new Topic<>("Device attention or Device heads-up events", DeviceHeadsUpListener.class);

  /**
   * Called when a device requires user attention.
   *
   * @param deviceSerialNumber the serial number of the device
   * @param project the project associated with the event
   */
  void userInvolvementRequired(@NotNull String deviceSerialNumber, @NotNull Project project);

  /**
   * Called when an app is being launched on a device.
   *
   * @param deviceSerialNumber the serial number of the device
   * @param project the project associated with the event
   */
  void launchingApp(@NotNull String deviceSerialNumber, @NotNull Project project);

  /**
   * Called when a test is being launched on a device.
   *
   * @param deviceSerialNumber the serial number of the device
   * @param project the project associated with the event
   */
  void launchingTest(@NotNull String deviceSerialNumber, @NotNull Project project);
}