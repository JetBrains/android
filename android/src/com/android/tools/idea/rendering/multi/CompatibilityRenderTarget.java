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
package com.android.tools.idea.rendering.multi;

import com.android.annotations.NonNull;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.*;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A special {@link IAndroidTarget} which simulates a particular given API level,
 * but uses a more recent real platform and layoutlib instead of the original target.
 * This allows the layout preview to for example render on versions that are not installed
 * on the current system, or to simulate the look of an older render target without the
 * big expense of loading and running multiple simultaneous layoutlib instances.
 * <p>
 * This does have some limitations: the layoutlib code will not actually be the older
 * layoutlib, so for example when rendering the old classic Theme, you are getting that
 * Theme as implemented in newer versions, not the original. Similarly any code running
 * inside the layoutlib.jar will be using a higher version of Build.VERSION.SDK_INT. However,
 * in practice (after all, we only run view code) this does not appear to be a big limitation.
 */
public class CompatibilityRenderTarget implements IAndroidTarget {
  private final int myApiLevel;
  private final IAndroidTarget myDelegate;
  private final AndroidVersion myVersion;
  private final IAndroidTarget myRealTarget;

  public CompatibilityRenderTarget(@NotNull IAndroidTarget delegate, int apiLevel, @Nullable IAndroidTarget realTarget) {
    myDelegate = delegate;
    myApiLevel = apiLevel;
    myRealTarget = realTarget;
    myVersion = realTarget != null ? realTarget.getVersion() : new AndroidVersion(apiLevel, null);
  }

  /** The {@link com.android.sdklib.IAndroidTarget} we're using for actual rendering */
  @NotNull
  public IAndroidTarget getRenderTarget() {
    return myDelegate;
  }

  /**
   * The simulated {@link com.android.sdklib.IAndroidTarget} if it happens to be installed; we use this
   * to pick up better assets for example
   */
  @Nullable
  public IAndroidTarget getRealTarget() {
    return myRealTarget;
  }

  @Override
  public String getDescription() {
    return myDelegate.getDescription();
  }

  @NonNull
  @Override
  public AndroidVersion getVersion() {
    return myVersion;
  }

  @Override
  public String getVersionName() {
    String name = SdkVersionInfo.getAndroidName(myApiLevel);
    if (name == null) {
      name = Integer.toString(myApiLevel);
    }
    return name;
  }

  @Override
  public String hashString() {
    return AndroidTargetHash.getPlatformHashString(myVersion);
  }

  @Override
  public int compareTo(@NonNull IAndroidTarget other) {
    int delta = myApiLevel - other.getVersion().getApiLevel();
    if (delta != 0) {
      return delta;
    }
    return myDelegate.compareTo(other);
  }

  @Override
  public int getRevision() {
    return 1;
  }


  // Resource tricks

  @Override
  public String getPath(int pathId) {
    return myDelegate.getPath(pathId);
  }

  @Override
  public File getFile(int pathId) {
    return myDelegate.getFile(pathId);
  }

  // Remainder: Just delegate

  @Override
  public String getLocation() {
    return myDelegate.getLocation();
  }

  @Override
  public String getVendor() {
    return myDelegate.getVendor();
  }

  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  public String getFullName() {
    return myDelegate.getFullName();
  }

  @Override
  public String getClasspathName() {
    return myDelegate.getClasspathName();
  }

  @Override
  public String getShortClasspathName() {
    return myDelegate.getShortClasspathName();
  }

  @Override
  public boolean isPlatform() {
    return myDelegate.isPlatform();
  }

  @Override
  public IAndroidTarget getParent() {
    return myDelegate.getParent();
  }

  @Override
  public BuildToolInfo getBuildToolInfo() {
    return myDelegate.getBuildToolInfo();
  }

  @NonNull
  @Override
  public List<String> getBootClasspath() {
    return myDelegate.getBootClasspath();
  }

  @Override
  public boolean hasRenderingLibrary() {
    return myDelegate.hasRenderingLibrary();
  }

  @NonNull
  @Override
  public File[] getSkins() {
    return myDelegate.getSkins();
  }

  @Nullable
  @Override
  public File getDefaultSkin() {
    return myDelegate.getDefaultSkin();
  }

  @Override
  public List<OptionalLibrary> getOptionalLibraries() {
    return myDelegate.getOptionalLibraries();
  }

  @Override
  public List<OptionalLibrary> getAdditionalLibraries() {
    return myDelegate.getAdditionalLibraries();
  }

  @Override
  public String[] getPlatformLibraries() {
    return myDelegate.getPlatformLibraries();
  }

  @Override
  public String getProperty(String name) {
    return myDelegate.getProperty(name);
  }

  @Override
  public Map<String, String> getProperties() {
    return myDelegate.getProperties();
  }

  @Override
  public boolean canRunOn(IAndroidTarget target) {
    return myDelegate.canRunOn(target);
  }
}
