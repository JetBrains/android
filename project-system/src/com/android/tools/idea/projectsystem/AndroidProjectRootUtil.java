/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem;

import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;

import com.google.common.base.Strings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

public final class AndroidProjectRootUtil {
  private AndroidProjectRootUtil() {
  }

  @Nullable
  @SystemIndependent
  public static String getModuleDirPath(@NotNull Module module) {
    String linkedProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (!Strings.isNullOrEmpty(linkedProjectPath)) {
      return linkedProjectPath;
    }
    @SystemIndependent String moduleFilePath = module.getModuleFilePath();
    return VfsUtil.getParentDir(moduleFilePath);
  }

  @Nullable
  public static VirtualFile getFileByRelativeModulePath(Module module, String relativePath, boolean lookInContentRoot) {
    if (module.isDisposed() || relativePath == null || relativePath.isEmpty()) {
      return null;
    }

    ProgressManager.checkCanceled();
    String moduleDirPath = getModuleDirPath(module);
    if (moduleDirPath != null) {
      String absPath = toSystemIndependentName(moduleDirPath + relativePath);
      ProgressManager.checkCanceled();
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absPath);
      if (file != null) {
        return file;
      }
    }

    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (lookInContentRoot) {
      for (VirtualFile contentRoot : contentRoots) {
        String absPath = toSystemIndependentName(contentRoot.getPath() + relativePath);
        ProgressManager.checkCanceled();
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absPath);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }

  /**
   * @deprecated Do not use. JPS project specific.
   */
  @Deprecated
  @Nullable
  @SystemIndependent
  public static String getAptGenSourceRootPath(@NotNull AndroidFacet facet) {
    String path = facet.getProperties().GEN_FOLDER_RELATIVE_PATH_APT;
    if (path.isEmpty()) return null;
    @SystemIndependent String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  /**
   * @deprecated Do not use. JPS project specific.
   */
  @Deprecated
  @Nullable
  @SystemIndependent
  public static String getAidlGenSourceRootPath(@NotNull AndroidFacet facet) {
    String path = facet.getProperties().GEN_FOLDER_RELATIVE_PATH_AIDL;
    if (path.isEmpty()) return null;
    @SystemIndependent String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  /**
   * @deprecated Do not use. JPS project specific.
   */
  @Deprecated
  @Nullable
  public static VirtualFile getAidlGenDir(@NotNull AndroidFacet facet) {
    String genPath = getAidlGenSourceRootPath(facet);
    return genPath != null ? LocalFileSystem.getInstance().findFileByPath(genPath) : null;
  }

  /**
   * @deprecated Do not use. JPS project specific.
   */
  @Deprecated
  @Nullable
  public static String getRenderscriptGenSourceRootPath(@NotNull AndroidFacet facet) {
    // todo: return correct path for mavenized module when it'll be supported
    return getAidlGenSourceRootPath(facet);
  }

  /**
   * @deprecated Do not use. JPS project specific.
   */
  @Deprecated
  @Nullable
  public static VirtualFile getRenderscriptGenDir(@NotNull AndroidFacet facet) {
    String path = getRenderscriptGenSourceRootPath(facet);
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  /**
   * @deprecated Do not use. JPS project specific.
   */
  @Deprecated
  @Nullable
  public static VirtualFile getAssetsDir(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getProperties().ASSETS_FOLDER_RELATIVE_PATH, false);
  }
}
