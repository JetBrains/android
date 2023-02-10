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
package org.jetbrains.android.sdk;

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final String myHashString;

  public CompatibilityRenderTarget(@NotNull IAndroidTarget delegate, int apiLevel, @Nullable IAndroidTarget realTarget) {
    myDelegate = delegate;
    myApiLevel = apiLevel;
    myRealTarget = realTarget;
    myVersion = realTarget != null ? realTarget.getVersion() : new AndroidVersion(apiLevel, null);
    // Don't *just* name it say android-15 since that can clash with the REAL key used for android-15
    // (if the user has it installed) and in particular for maps like AndroidSdkData#getTargetData
    // can end up accidentally using compatibility targets instead of real android platform targets,
    // resulting in bugs like b.android.com/213075
    myHashString = "compat-" + AndroidTargetHash.getPlatformHashString(myVersion);
  }

  /**
   * Copies an existing {@link CompatibilityRenderTarget} but updates the the render delegate. It will keep the API level and
   * the real target.
   */
  public static IAndroidTarget copyWithNewDelegate(@NotNull CompatibilityRenderTarget original, @NotNull IAndroidTarget newDelegate) {
    return new CompatibilityRenderTarget(newDelegate, original.myApiLevel, original.myRealTarget);
  }

  /** The {@link IAndroidTarget} we're using for actual rendering */
  @NotNull
  public IAndroidTarget getRenderTarget() {
    return myDelegate;
  }

  /**
   * The simulated {@link IAndroidTarget} if it happens to be installed; we use this
   * to pick up better assets for example.
   */
  @Nullable
  public IAndroidTarget getRealTarget() {
    return myRealTarget;
  }

  @Override
  @NotNull
  public AndroidVersion getVersion() {
    return myVersion;
  }

  @Override
  public String getVersionName() {
    return AndroidVersionUtils.getFullApiName(myVersion, true, true);
  }

  @Override
  public String hashString() {
    return myHashString;
  }

  @Override
  public int compareTo(@NotNull IAndroidTarget other) {
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
  @NotNull
  public Path getPath(int pathId) {
    return myDelegate.getPath(pathId);
  }

  // Remainder: Just delegate

  @Override
  @NotNull
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

  @Override
  @NotNull
  public List<String> getBootClasspath() {
    return myDelegate.getBootClasspath();
  }

  @Override
  public boolean hasRenderingLibrary() {
    return myDelegate.hasRenderingLibrary();
  }

  @Override
  @NotNull
  public Path[] getSkins() {
    return myDelegate.getSkins();
  }

  @Nullable
  @Override
  public Path getDefaultSkin() {
    return myDelegate.getDefaultSkin();
  }

  @Override
  @NotNull
  public List<OptionalLibrary> getOptionalLibraries() {
    return myDelegate.getOptionalLibraries();
  }

  @Override
  @NotNull
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
