/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * A handful of utility methods useful for suggesting package names when creating new files inside
 * an Android project.
 */
public final class AndroidPackageUtils {

  private AndroidPackageUtils() {
  }

  /**
   * Return the top-level package associated with this project.
   */
  @NotNull
  public static String getPackageForApplication(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = androidFacet.getAndroidModel();
    assert androidModel != null;
    return androidModel.getApplicationId();
  }

  /**
   * Return a suggested package given the project's current paths and a particular target directory
   * (which is a likely candidate for where the user wants to add some new code)
   */
  @NotNull
  public static String getPackageForPath(@NotNull AndroidProjectPaths paths, @NotNull VirtualFile targetDirectory) {
    Module module = paths.getModule();
    File srcDirectory = paths.getSrcDirectory();
    if (srcDirectory != null) {
      ProjectRootManager projectManager = ProjectRootManager.getInstance(module.getProject());
      String suggestedPackage = projectManager.getFileIndex().getPackageNameByDirectory(targetDirectory);
      if (suggestedPackage != null && !suggestedPackage.isEmpty()) {
        return suggestedPackage;
      }
    }

    return getPackageForApplication(paths.getAndroidFacet());
  }

  /**
   * Convenience method for calling {@link #getPackageForPath(AndroidProjectPaths, VirtualFile)}
   * when you don't have an {@link AndroidProjectPaths} instance.
   */
  @NotNull
  public static String getPackageForPath(@NotNull AndroidFacet androidFacet,
                                         @NotNull SourceProvider sourceProvider,
                                         @NotNull VirtualFile targetDirectory) {
    return getPackageForPath(new AndroidProjectPaths(androidFacet, sourceProvider), targetDirectory);
  }

  /**
   * Convenience method when we have a list of 0 or more source providers. For example, when
   * querying {@link AndroidProjectPaths#getSourceProviders(AndroidFacet, VirtualFile)}, it's nice
   * to just pass the list along to this method, instead of doing list size checks externally.
   */
  @NotNull
  public static String getPackageForPath(@NotNull AndroidFacet androidFacet,
                                         @NotNull List<SourceProvider> sourceProviders,
                                         @NotNull VirtualFile targetDirectory) {
    if (sourceProviders.size() > 0) {
      return getPackageForPath(androidFacet, sourceProviders.get(0), targetDirectory);
    }
    else {
      return getPackageForApplication(androidFacet);
    }
  }
}
