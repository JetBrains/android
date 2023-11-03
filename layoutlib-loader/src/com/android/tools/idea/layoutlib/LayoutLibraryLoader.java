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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.environment.Logger;
import com.android.utils.ComputerArchUtilsKt;
import com.android.utils.CpuArchitecture;
import com.android.utils.ILogger;
import java.io.File;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Loads a {@link LayoutLibrary}
 */
public class LayoutLibraryLoader {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.LayoutLibraryLoader");

  private static final Map<IAndroidTarget, SoftReference<LayoutLibrary>> ourLibraryCache = new WeakHashMap<>();

  private LayoutLibraryLoader() {
  }

  @NonNull
  private static LayoutLibrary loadImpl(@NonNull IAndroidTarget target, @NonNull Map<String, Map<String, Integer>> enumMap)
    throws RenderingException {
    final Path fontFolderPath = (target.getPath(IAndroidTarget.FONTS));
    if (!Files.exists(fontFolderPath) || !Files.isDirectory(fontFolderPath)) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", fontFolderPath));
    }

    final String platformFolderPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    final File platformFolder = new File(platformFolderPath);
    if (!platformFolder.isDirectory()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", platformFolderPath));
    }

    final File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.file.not.exist.error", buildProp.getPath()));
    }

    final ILogger logger = new LogWrapper(LOG);
    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    final ILayoutLog layoutLog = new LayoutLogWrapper(LOG);

    String dataPath = target.getPath(IAndroidTarget.DATA).toString().replace('\\', '/');
    String[] keyboardPaths = new String[] { dataPath + "/keyboards/Generic.kcm" };

    LayoutLibrary library = LayoutLibraryLoader.getLayoutLibraryProvider().map(LayoutLibraryProvider::getLibrary).orElse(null);
    if (library == null ||
        !library.init(buildPropMap != null ? buildPropMap : Collections.emptyMap(), fontFolderPath.toFile(),
                      getNativeLibraryPath(dataPath), dataPath + "/icu/icudt72l.dat", keyboardPaths, enumMap, layoutLog)) {
      throw new RenderingException(LayoutlibBundle.message("layoutlib.init.failed"));
    }
    return library;
  }

  @NonNull
  private static String getNativeLibraryPath(@NonNull String dataPath) {
    return dataPath + "/" + getPlatformName() + "/lib64/";
  }

  @NonNull
  private static String getPlatformName() {
    return switch(SdkConstants.currentPlatform()) {
      case SdkConstants.PLATFORM_WINDOWS -> "win";
      case SdkConstants.PLATFORM_DARWIN -> ComputerArchUtilsKt.getJvmArchitecture() == CpuArchitecture.ARM ? "mac-arm" : "mac";
      case SdkConstants.PLATFORM_LINUX -> "linux";
      default -> "";
    };
  }

  /**
   * Loads and initializes layoutlib.
   */
  @NonNull
  public static synchronized LayoutLibrary load(
    @NonNull IAndroidTarget target,
    @NonNull Map<String, Map<String, Integer>> enumMap,
    @NonNull Supplier<Boolean> hasExternalCrash)
    throws RenderingException {
    if (Bridge.hasNativeCrash()) {
      throw new RenderingException("Rendering disabled following a crash");
    }
    SoftReference<LayoutLibrary> libraryRef = ourLibraryCache.get(target);
    LayoutLibrary library = libraryRef != null ? libraryRef.get() : null;
    if (library == null || library.isDisposed()) {
      if (hasExternalCrash.get()) {
        Bridge.setNativeCrash(true);
        throw new RenderingException("Rendering disabled following a crash");
      }
      library = loadImpl(target, enumMap);
      ourLibraryCache.put(target, new SoftReference<>(library));
    }

    return library;
  }

  @NonNull
  public static Optional<LayoutLibraryProvider> getLayoutLibraryProvider() {
    return ServiceLoader.load(LayoutLibraryProvider.class, LayoutLibraryProvider.class.getClassLoader()).findFirst();
  }

  /**
   * Extension point for the Android plugin to have access to layoutlib in a separate plugin.
   */
  public interface LayoutLibraryProvider {

    @Nullable
    LayoutLibrary getLibrary();

    @Nullable
    Class<?> getFrameworkRClass();
  }
}
