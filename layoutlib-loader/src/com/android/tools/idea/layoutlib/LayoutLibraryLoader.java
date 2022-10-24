/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.layoutlib;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.io.BufferingFileWrapper;
import com.android.utils.ILogger;
import com.intellij.internal.statistic.analytics.StudioCrashDetails;
import com.intellij.internal.statistic.analytics.StudioCrashDetection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.CpuArch;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Loads a {@link LayoutLibrary}
 */
public class LayoutLibraryLoader {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.LayoutLibraryLoader");

  private static final Map<IAndroidTarget, LayoutLibrary> ourLibraryCache =
    ContainerUtil.createWeakKeySoftValueMap();

  private LayoutLibraryLoader() {
  }

  @NotNull
  private static LayoutLibrary loadImpl(@NotNull IAndroidTarget target, @NotNull Map<String, Map<String, Integer>> enumMap)
    throws RenderingException {
    final Path fontFolderPath = (target.getPath(IAndroidTarget.FONTS));
    final VirtualFile fontFolder = LocalFileSystem.getInstance().findFileByNioFile(fontFolderPath);
    if (fontFolder == null || !fontFolder.isDirectory()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", fontFolderPath));
    }

    final String platformFolderPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    final File platformFolder = new File(platformFolderPath);
    if (!platformFolder.isDirectory()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(platformFolderPath)));
    }

    final File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(buildProp.getPath())));
    }

    final ILogger logger = new LogWrapper(LOG);
    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    final ILayoutLog layoutLog = new LayoutLogWrapper(LOG);

    String dataPath = FileUtil.toSystemIndependentName(target.getPath(IAndroidTarget.DATA).toString());
    String[] keyboardPaths = new String[] { dataPath + "/keyboards/Generic.kcm" };

    LayoutLibrary library = LayoutLibraryProvider.EP_NAME.computeSafeIfAny(LayoutLibraryProvider::getLibrary);
    if (library == null ||
        !library.init(buildPropMap != null ? buildPropMap : Collections.emptyMap(), new File(fontFolder.getPath()),
                      getNativeLibraryPath(dataPath), dataPath + "/icu/icudt70l.dat", keyboardPaths, enumMap, layoutLog)) {
      throw new RenderingException(LayoutlibBundle.message("layoutlib.init.failed"));
    }
    return library;
  }

  @NotNull
  private static String getNativeLibraryPath(@NotNull String dataPath) {
    return dataPath + "/" + getPlatformName() + "/lib64/";
  }

  @NotNull
  private static String getPlatformName() {
    if (SystemInfo.isWindows) return "win";
    else if (SystemInfo.isMac) return CpuArch.isArm64() ? "mac-arm" : "mac";
    else if (SystemInfo.isLinux) return "linux";
    else return "";
  }

  /**
   * Loads and initializes layoutlib.
   */
  @NotNull
  public static synchronized LayoutLibrary load(@NotNull IAndroidTarget target, @NotNull Map<String, Map<String, Integer>> enumMap)
    throws RenderingException {
    if (Bridge.hasNativeCrash()) {
      throw new RenderingException("Rendering disabled following a crash");
    }
    LayoutLibrary library = ourLibraryCache.get(target);
    if (library == null || library.isDisposed()) {
      List<StudioCrashDetails> crashes = StudioCrashDetection.reapCrashDescriptions();
      for (StudioCrashDetails crash : crashes) {
        if (isCrashCausedByLayoutlib(crash)) {
          Bridge.setNativeCrash(true);
          throw new RenderingException("Rendering disabled following a crash");
        }
      }
      library = loadImpl(target, enumMap);
      ourLibraryCache.put(target, library);
    }

    return library;
  }

  private static boolean isCrashCausedByLayoutlib(@NotNull StudioCrashDetails crash) {
    return crash.isJvmCrash() &&
           (crash.getErrorThread().contains("Layoutlib Render Thread") || crash.getErrorFrame().contains("libandroid_runtime"));
  }

  /**
   * Extension point for the Android plugin to have access to layoutlib in a separate plugin.
   */
  public static abstract class LayoutLibraryProvider {
    public static final ExtensionPointName<LayoutLibraryProvider> EP_NAME =
      new ExtensionPointName<>("com.android.tools.idea.layoutlib.layoutLibraryProvider");

    @NotNull
    public abstract LayoutLibrary getLibrary();

    @NotNull
    public abstract Class<?> getFrameworkRClass();
  }
}
