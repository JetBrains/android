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
import com.android.tools.idea.run.DeviceFutures;
import com.intellij.openapi.project.Project;
import java.time.Instant;
import java.util.Collection;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class Device {
  @NotNull
  private final String myName;

  @NotNull
  private final String myKey;

  @Nullable
  private final Instant myConnectionTime;

  @Nullable
  private final IDevice myDdmlibDevice;

  static abstract class Builder<T extends Builder<T>> {
    @Nullable
    private String myName;

    @Nullable
    private String myKey;

    @Nullable
    private Instant myConnectionTime;

    @Nullable
    private IDevice myDdmlibDevice;

    @NotNull
    final T setName(@NotNull String name) {
      myName = name;
      return self();
    }

    @NotNull
    final T setKey(@NotNull String key) {
      myKey = key;
      return self();
    }

    @NotNull
    final T setConnectionTime(@NotNull Instant connectionTime) {
      myConnectionTime = connectionTime;
      return self();
    }

    @NotNull
    final T setDdmlibDevice(@NotNull IDevice ddmlibDevice) {
      myDdmlibDevice = ddmlibDevice;
      return self();
    }

    @NotNull
    abstract T self();

    @NotNull
    abstract Device build();
  }

  Device(@NotNull Builder builder) {
    assert builder.myName != null;
    myName = builder.myName;

    assert builder.myKey != null;
    myKey = builder.myKey;

    myConnectionTime = builder.myConnectionTime;
    myDdmlibDevice = builder.myDdmlibDevice;
  }

  @NotNull
  abstract Icon getIcon();

  abstract boolean isConnected();

  @NotNull
  final String getName() {
    return myName;
  }

  @NotNull
  abstract Collection<String> getSnapshots();

  @NotNull
  final String getKey() {
    return myKey;
  }

  @Nullable
  final Instant getConnectionTime() {
    return myConnectionTime;
  }

  @Nullable
  final IDevice getDdmlibDevice() {
    return myDdmlibDevice;
  }

  @NotNull
  abstract DeviceFutures newDeviceFutures(@NotNull Project project, @Nullable String snapshot);

  @NotNull
  @Override
  public final String toString() {
    return myName;
  }
}
