// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.layoutlib;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.io.BufferingFileWrapper;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Loads a {@link LayoutLibrary}
 */
public final class LayoutLibraryLoader {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.LayoutLibraryLoader");

  private static final Map<IAndroidTarget, LayoutLibrary> ourLibraryCache =
    ContainerUtil.createWeakKeySoftValueMap();

  private LayoutLibraryLoader() {
  }

  @NotNull
  private static LayoutLibrary loadImpl(@NotNull IAndroidTarget target, @NotNull Map<String, Map<String, Integer>> enumMap)
    throws RenderingException {
    final String fontFolderPath = FileUtil.toSystemIndependentName((target.getPath(IAndroidTarget.FONTS)));
    final VirtualFile fontFolder = LocalFileSystem.getInstance().findFileByPath(fontFolderPath);
    if (fontFolder == null || !fontFolder.isDirectory()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(fontFolderPath)));
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

    if (!SystemInfo.isJavaVersionAtLeast(8, 0, 0) && target.getVersion().getFeatureLevel() >= 24) {
      // From N, we require to be running in Java 8
      throw new UnsupportedJavaRuntimeException(LayoutlibBundle.message("android.layout.preview.unsupported.jdk",
                                                                        SdkVersionInfo.getCodeName(target.getVersion().getFeatureLevel())));
    }

    LayoutLibrary library;
    final ILogger logger = new LogWrapper(LOG);
    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    final LayoutLog layoutLog = new LayoutLogWrapper(LOG);

    String dataPath = FileUtil.toSystemIndependentName(target.getPath(IAndroidTarget.DATA));

    // We instantiate the local Bridge implementation and pass it to the LayoutLibrary instance
    library =
      LayoutLibrary.load(new com.android.layoutlib.bridge.Bridge(), new LayoutlibClassLoader(LayoutLibraryLoader.class.getClassLoader()));
    if (!library.init(buildPropMap, new File(fontFolder.getPath()), getNativeLibraryPath(dataPath), dataPath + "icu/", enumMap, layoutLog)) {
      throw new RenderingException(LayoutlibBundle.message("layoutlib.init.failed"));
    }
    return library;
  }

  @NotNull
  private static String getNativeLibraryPath(@NotNull String dataPath) {
    return dataPath + getPlatformName() + (SystemInfo.is64Bit ? "/lib64/" : "/lib/");
  }

  @NotNull
  private static String getPlatformName() {
    if (SystemInfo.isWindows) return "win";
    else if (SystemInfo.isMac) return "mac";
    else if (SystemInfo.isLinux) return "linux";
    else return "";
  }

  /**
   * Loads and initializes layoutlib.
   */
  @NotNull
  public static synchronized LayoutLibrary load(@NotNull IAndroidTarget target, @NotNull Map<String, Map<String, Integer>> enumMap)
    throws RenderingException {
    LayoutLibrary library = ourLibraryCache.get(target);
    if (library == null || library.isDisposed()) {
      library = loadImpl(target, enumMap);
      ourLibraryCache.put(target, library);
    }

    return library;
  }
}
