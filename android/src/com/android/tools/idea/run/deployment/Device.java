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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.ImmutableCollection;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class Device {
  private final String myName;
  private final IDevice myDdmlibDevice;

  Device(@NotNull String name, @Nullable IDevice ddmlibDevice) {
    myName = name;
    myDdmlibDevice = ddmlibDevice;
  }

  @NotNull
  abstract Icon getIcon();

  @NotNull
  final String getName() {
    return myName;
  }

  @NotNull
  abstract ImmutableCollection<String> getSnapshots();

  @Nullable
  final IDevice getDdmlibDevice() {
    return myDdmlibDevice;
  }

  @NotNull
  abstract AndroidDevice toAndroidDevice();

  @NotNull
  @Override
  public String toString() {
    return myName;
  }
}
