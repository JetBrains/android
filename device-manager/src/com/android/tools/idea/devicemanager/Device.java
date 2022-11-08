/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.device.Resolution;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Device {
  protected final @NotNull Key myKey;
  protected final @NotNull DeviceType myType;
  protected final @NotNull String myName;
  protected final @NotNull String myTarget;
  protected final @NotNull AndroidVersion myAndroidVersion;
  protected final @Nullable Resolution myResolution;
  protected final int myDensity;
  protected final @NotNull ImmutableCollection<@NotNull String> myAbis;
  protected final @Nullable StorageDevice myStorageDevice;

  protected static abstract class Builder {
    protected @Nullable Key myKey;
    protected @NotNull DeviceType myType = DeviceType.PHONE;
    protected @Nullable String myName;
    protected @Nullable String myTarget;
    protected @NotNull AndroidVersion myAndroidVersion = AndroidVersion.DEFAULT;
    protected @Nullable Resolution myResolution;
    protected int myDensity = -1;
    protected final @NotNull Collection<@NotNull String> myAbis = new ArrayList<>();
    protected @Nullable StorageDevice myStorageDevice;

    protected abstract @NotNull Device build();
  }

  protected Device(@NotNull Builder builder) {
    assert builder.myKey != null;
    myKey = builder.myKey;

    myType = builder.myType;

    assert builder.myName != null;
    myName = builder.myName;

    assert builder.myTarget != null;
    myTarget = builder.myTarget;

    myAndroidVersion = builder.myAndroidVersion;
    myResolution = builder.myResolution;
    myDensity = builder.myDensity;
    myAbis = ImmutableList.copyOf(builder.myAbis);
    myStorageDevice = builder.myStorageDevice;
  }

  public final @NotNull Key getKey() {
    return myKey;
  }

  public final @NotNull DeviceType getType() {
    return myType;
  }

  public abstract @NotNull Icon getIcon();

  public final @NotNull String getName() {
    return myName;
  }

  public abstract boolean isOnline();

  public final @NotNull String getTarget() {
    return myTarget;
  }

  public final @NotNull AndroidVersion getAndroidVersion() {
    return myAndroidVersion;
  }

  public final @Nullable Resolution getResolution() {
    return myResolution;
  }

  public final @Nullable Resolution getDp() {
    if (myDensity == -1) {
      return null;
    }

    if (myResolution == null) {
      return null;
    }

    int width = (int)Math.ceil((double)Density.DEFAULT_DENSITY * myResolution.getWidth() / myDensity);
    int height = (int)Math.ceil((double)Density.DEFAULT_DENSITY * myResolution.getHeight() / myDensity);

    return new Resolution(width, height);
  }

  public final @NotNull Iterable<@NotNull String> getAbis() {
    return myAbis;
  }

  public final @Nullable StorageDevice getStorageDevice() {
    return myStorageDevice;
  }

  @Override
  public final @NotNull String toString() {
    return myName;
  }
}
