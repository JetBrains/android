/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link IAndroidTarget} to render using the layoutlib version and resources shipped with Android Studio.
 */
public class EmbeddedRenderTarget implements IAndroidTarget {
  private static final String ONLY_FOR_RENDERING_ERROR = "This target is only for rendering";
  private static final String FRAMEWORK_RES_JAR = "framework_res.jar";

  @Nullable private final String myBasePath;

  private static EmbeddedRenderTarget ourStudioEmbeddedTarget;

  /**
   * Returns a CompatibilityRenderTarget that will use EmbeddedRenderTarget to do the rendering.
   */
  public static CompatibilityRenderTarget getCompatibilityTarget(
    @NotNull IAndroidTarget target, @NotNull Supplier<String> layoutlibPathSupplier) {
    int api = target.getVersion().getApiLevel();

    if (target instanceof CompatibilityRenderTarget) {
      CompatibilityRenderTarget compatRenderTarget = (CompatibilityRenderTarget)target;
      target = compatRenderTarget.getRealTarget();
    }

    return new CompatibilityRenderTarget(getInstance(layoutlibPathSupplier), api, target);
  }

  private static EmbeddedRenderTarget getInstance(Supplier<String> layoutlibPathSupplier) {
    if (ourStudioEmbeddedTarget == null) {
      ourStudioEmbeddedTarget = new EmbeddedRenderTarget(layoutlibPathSupplier.get());
    }
    return ourStudioEmbeddedTarget;
  }

  private EmbeddedRenderTarget(@NotNull String layoutlibPath) {
    myBasePath = layoutlibPath;
  }

  @Override
  @NotNull
  public String getLocation() {
    Preconditions.checkState(myBasePath != null, "Embedded layoutlib not found");
    return myBasePath;
  }

  @Override
  public String getVendor() {
    return PlatformTarget.PLATFORM_VENDOR;
  }

  @Override
  @NotNull
  public AndroidVersion getVersion() {
    // This method will never be called if this is used as a delegate of CompatibilityRenderTarget
    throw new UnsupportedOperationException("This target can only be used as a CompatibilityRenderTarget delegate");
  }

  @Override
  public String getVersionName() {
    // This method will never be called if this is used as a delegate of CompatibilityRenderTarget
    throw new UnsupportedOperationException("This target can only be used as a CompatibilityRenderTarget delegate");
  }

  @Override
  public int getRevision() {
    return 1;
  }

  @Override
  public boolean isPlatform() {
    return true;
  }

  @Override
  public IAndroidTarget getParent() {
    return null;
  }

  @Override
  @NotNull
  public Path getPath(int pathId) {
    String path;
    // The prebuilt version of layoutlib only includes the layoutlib.jar and the resources.
    switch (pathId) {
      case DATA:
        return Paths.get(getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER);
      case RESOURCES:
        return Paths.get(getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER + FRAMEWORK_RES_JAR);
      case FONTS:
        return Paths.get(getLocation() + SdkConstants.OS_PLATFORM_FONTS_FOLDER);
      default:
        assert false : getClass().getSimpleName() + " does not support path of type " + pathId;
        return Paths.get(getLocation());
    }
  }

  @Override
  public BuildToolInfo getBuildToolInfo() {
    return null;
  }

  @Override
  @NotNull
  public List<String> getBootClasspath() {
    return ImmutableList.of(getPath(IAndroidTarget.ANDROID_JAR).toString());
  }

  @Override
  public boolean hasRenderingLibrary() {
    return true;
  }

  /*
   * All the methods below are not used since this is a target that is only used to render previews in Studio.
   */

  @Override
  public String getName() {
    // This method is only used for non platform targets
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String getFullName() {
    return getName();
  }

  @Override
  public String getClasspathName() {
    return getName();
  }

  @Override
  public String getShortClasspathName() {
    return getName();
  }

  @Override
  @NotNull
  public List<OptionalLibrary> getOptionalLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  @NotNull
  public List<OptionalLibrary> getAdditionalLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  @NotNull
  public Path[] getSkins() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  @Nullable
  public Path getDefaultSkin() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String[] getPlatformLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String getProperty(String name) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public Map<String, String> getProperties() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public boolean canRunOn(IAndroidTarget target) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String hashString() {
    return "studio-embedded-render-target";
  }

  @Override
  public int compareTo(IAndroidTarget o) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }
}
