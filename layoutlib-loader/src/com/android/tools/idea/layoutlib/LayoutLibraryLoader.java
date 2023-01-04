// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.android.tools.idea.layoutlib;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.io.BufferingFileWrapper;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.CpuArch;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Loads a {@link LayoutLibrary}
 */
public final class LayoutLibraryLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.LayoutLibraryLoader");

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
        LayoutlibBundle.message("android.directory.cannot.be.found.error", FileUtilRt.toSystemDependentName(platformFolderPath)));
    }

    final File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.file.not.exist.error", FileUtilRt.toSystemDependentName(buildProp.getPath())));
    }

    final ILogger logger = new LogWrapper(LOG);
    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    final ILayoutLog layoutLog = new LayoutLogWrapper(LOG);

    String dataPath = FileUtil.toSystemIndependentName(target.getPath(IAndroidTarget.DATA).toString());

    LayoutLibrary library = LayoutLibraryProvider.EP_NAME.computeSafeIfAny(LayoutLibraryProvider::getLibrary);
    if (library == null ||
        !library.init(buildPropMap != null ? buildPropMap : Collections.emptyMap(), new File(fontFolder.getPath()),
                      getNativeLibraryPath(dataPath), dataPath + "/icu/icudt70l.dat", enumMap, layoutLog)) {
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
      library = loadImpl(target, enumMap);
      ourLibraryCache.put(target, library);
    }

    return library;
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
