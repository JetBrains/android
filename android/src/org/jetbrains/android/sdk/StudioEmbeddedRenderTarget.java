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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@link IAndroidTarget} to render using the layoutlib version and resources shipped with Android Studio.
 */
public class StudioEmbeddedRenderTarget implements IAndroidTarget {
  private static final Logger LOG = Logger.getInstance(StudioEmbeddedRenderTarget.class);
  private static final String ONLY_FOR_RENDERING_ERROR = "This target is only for rendering";

  private static final String[] EMBEDDED_PATHS = {
    // Bundled path
    "/plugins/android/lib/layoutlib/",
    // Development path
    "/../../prebuilts/studio/layoutlib/"
  };
  private final String myBasePath;

  private static StudioEmbeddedRenderTarget ourStudioEmbeddedTarget;
  private static boolean ourDisableEmbeddedTargetForTesting = false;

  /**
   * Method that allows to disable the use of the embedded render target. Only for testing.
   * @param value if true, the embedded layoutlib won't be used
   */
  @VisibleForTesting
  public static void setDisableEmbeddedTarget(boolean value) {
    assert ApplicationManager.getApplication().isUnitTestMode();

    ourDisableEmbeddedTargetForTesting = value;
  }

  /**
   * Returns a CompatibilityRenderTarget that will use StudioEmbeddedRenderTarget to do the rendering
   * @throws IOException if the local layoutlib distribution is not found
   */
  public static CompatibilityRenderTarget getCompatibilityTarget(@NotNull IAndroidTarget target) throws IOException {
    if (ourDisableEmbeddedTargetForTesting) {
      return new CompatibilityRenderTarget(target, target.getVersion().getApiLevel(), target);
    }

    int api = target.getVersion().getApiLevel();

    if (target instanceof CompatibilityRenderTarget) {
      CompatibilityRenderTarget compatRenderTarget = (CompatibilityRenderTarget)target;
      target = compatRenderTarget.getRealTarget();
    }

    return new CompatibilityRenderTarget(getInstance(), api, target);
  }

  private static StudioEmbeddedRenderTarget getInstance() throws IOException {
    if (ourStudioEmbeddedTarget == null) {
      ourStudioEmbeddedTarget = new StudioEmbeddedRenderTarget();
    }
    return ourStudioEmbeddedTarget;
  }

  private StudioEmbeddedRenderTarget() throws IOException {
    myBasePath = getEmbeddedLayoutLibPath();
  }

  /**
   * Returns the URL for the embedded layoutlib distribution.
   */
  @Nullable
  private static String getEmbeddedLayoutLibPath() {
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());

    StringBuilder notFoundPaths = new StringBuilder();
    for (String path : EMBEDDED_PATHS) {
      String jarPath = homePath + path;
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(jarPath));

      if (root != null) {
        File rootFile = VfsUtilCore.virtualToIoFile(root);
        if (rootFile.exists() && rootFile.isDirectory()) {
          LOG.debug("Embedded layoutlib found at " + jarPath);
          return rootFile.getAbsolutePath() + File.separator;
        }
      }
      else {
        notFoundPaths.append(jarPath).append('\n');
      }
    }

    LOG.error("Unable to find embedded layoutlib in paths:\n" + notFoundPaths.toString());
    return null;
  }

  @Override
  public String getLocation() {
    return myBasePath;
  }

  @Override
  public String getVendor() {
    return PlatformTarget.PLATFORM_VENDOR;
  }

  @NonNull
  @Override
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
  public String getPath(int pathId) {
    /*
    The prebuilt version of layoutlib only includes the layoutlib.jar and the resources.
     */
    switch (pathId) {
      case DATA:
        return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER;
      case ATTRIBUTES:
        return getLocation() + SdkConstants.OS_PLATFORM_ATTRS_XML;
      case MANIFEST_ATTRIBUTES:
        return getLocation() + SdkConstants.OS_PLATFORM_ATTRS_MANIFEST_XML;
      case RESOURCES:
        return getLocation() + SdkConstants.OS_PLATFORM_RESOURCES_FOLDER;
      case FONTS:
        return getLocation() + SdkConstants.OS_PLATFORM_FONTS_FOLDER;
      default:
        assert false : getClass().getSimpleName() + " does not support path of type " + pathId;
        return getLocation();
    }
  }

  @Override
  public File getFile(int pathId) {
    return new File(getPath(pathId));
  }

  @Override
  public BuildToolInfo getBuildToolInfo() {
    return null;
  }

  @NonNull
  @Override
  public List<String> getBootClasspath() {
    return ImmutableList.of(getPath(IAndroidTarget.ANDROID_JAR));
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
  public String getDescription() {
    return getName();
  }

  @NonNull
  @Override
  public List<OptionalLibrary> getOptionalLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @NonNull
  @Override
  public List<OptionalLibrary> getAdditionalLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @NonNull
  @Override
  public File[] getSkins() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Nullable
  @Override
  public File getDefaultSkin() {
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
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public int compareTo(IAndroidTarget o) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }
}
