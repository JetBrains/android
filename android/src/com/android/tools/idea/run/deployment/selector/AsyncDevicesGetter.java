/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class AsyncDevicesGetter implements Disposable {
  @NotNull
  private final Project project;

  @NotNull
  private final KeyToConnectionTimeMap connectionTimes;

  @SuppressWarnings("unused")
  private AsyncDevicesGetter(@NotNull Project project) {
    this(project, new KeyToConnectionTimeMap());
  }

  @NonInjectable
  @VisibleForTesting
  AsyncDevicesGetter(@NotNull Project project, @NotNull KeyToConnectionTimeMap map) {
    this.project = project;
    connectionTimes = map;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  static AsyncDevicesGetter getInstance(@NotNull Project project) {
    return project.getService(AsyncDevicesGetter.class);
  }

  /**
   * @return an optional list of devices including the virtual devices ready to be launched, virtual devices that have been launched, and
   * the connected physical devices
   */
  @NotNull
  Optional<List<Device>> get() {
    return Optional.empty();
  }
}
