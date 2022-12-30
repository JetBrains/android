// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.compiler;

import com.android.tools.idea.res.AndroidDependenciesCache;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.List;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

public final class AndroidCompileUtil {

  private static final Key<Boolean> RELEASE_BUILD_KEY = new Key<Boolean>(AndroidBuildCommonUtils.RELEASE_BUILD_OPTION);

  @NonNls public static final String OLD_PROGUARD_CFG_FILE_NAME = "proguard.cfg";
  public static Key<String> PROGUARD_CFG_PATHS_KEY = Key.create(AndroidBuildCommonUtils.PROGUARD_CFG_PATHS_OPTION);

  private AndroidCompileUtil() {
  }

  @Nullable
  public static Pair<VirtualFile, Boolean> getDefaultProguardConfigFile(@NotNull AndroidFacet facet) {
    VirtualFile root = AndroidRootUtil.getMainContentRoot(facet);
    if (root == null) {
      return null;
    }
    final VirtualFile proguardCfg = root.findChild(AndroidBuildCommonUtils.PROGUARD_CFG_FILE_NAME);
    if (proguardCfg != null) {
      return new Pair<VirtualFile, Boolean>(proguardCfg, true);
    }

    final VirtualFile oldProguardCfg = root.findChild(OLD_PROGUARD_CFG_FILE_NAME);
    if (oldProguardCfg != null) {
      return new Pair<VirtualFile, Boolean>(oldProguardCfg, false);
    }
    return null;
  }

  // must be invoked in a read action!

  public static void setReleaseBuild(@NotNull CompileScope compileScope) {
    compileScope.putUserData(RELEASE_BUILD_KEY, Boolean.TRUE);
  }

  public static String getApkName(Module module) {
    return module.getName() + ".apk";
  }

  @Nullable
  public static String getOutputPackage(@NotNull Module module) {
    VirtualFile compilerOutput = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    if (compilerOutput == null) return null;
    return new File(compilerOutput.getPath(), getApkName(module)).getPath();
  }

  @Nullable
  public static Module findCircularDependencyOnLibraryWithSamePackage(@NotNull AndroidFacet facet) {
    final Manifest manifest = Manifest.getMainManifest(facet);
    final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
    if (aPackage == null) {
      return null;
    }

    for (AndroidFacet depFacet : AndroidDependenciesCache.getAllAndroidDependencies(facet.getModule(), true)) {
      final Manifest depManifest = Manifest.getMainManifest(depFacet);
      final String depPackage = depManifest != null ? depManifest.getPackage().getValue() : null;
      if (aPackage.equals(depPackage)) {
        final List<AndroidFacet> depDependencies = AndroidDependenciesCache.getAllAndroidDependencies(depFacet.getModule(), false);

        if (depDependencies.contains(facet)) {
          // circular dependency on library with the same package
          return depFacet.getModule();
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getUnsignedApkPath(@NotNull AndroidFacet facet) {
    String path = facet.getProperties().APK_PATH;
    if (path.isEmpty()) {
      return getOutputPackage(facet.getModule());
    }
    @SystemIndependent String moduleDirPath = AndroidRootUtil.getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? FileUtilRt.toSystemDependentName(moduleDirPath + path) : null;
  }

  @Nullable
  public static String getAaptManifestPackage(@NotNull AndroidFacet facet) {
    if (facet.getProperties().USE_CUSTOM_MANIFEST_PACKAGE) {
      return facet.getProperties().CUSTOM_MANIFEST_PACKAGE;
    }
    final Manifest manifest = Manifest.getMainManifest(facet);

    return manifest != null
           ? manifest.getPackage().getStringValue()
           : null;
  }
}
