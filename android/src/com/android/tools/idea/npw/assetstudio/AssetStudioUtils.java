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
package com.android.tools.idea.npw.assetstudio;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Utility methods helpful for working with and generating Android assets.
 */
public final class AssetStudioUtils {

  /**
   * Returns true if a resource with the same name is already found at a location implied by the
   * input parameters.
   */
  public static boolean resourceExists(@NotNull AndroidProjectPaths paths, @NotNull ResourceFolderType resourceType, @NotNull String name) {
    File resDir = paths.getResDirectory();
    if (resDir == null) {
      return false;
    }

    File[] resTypes = resDir.listFiles();
    if (resTypes == null) {
      return false;
    }

    // The path of a resource looks something like:
    //
    // path/to/res/
    //   drawable/name
    //   drawable-hdpi-v9/name
    //   drawable-hdpi-v11/name
    //   drawable-mdpi-v9/name
    //   ...
    //
    // We don't really care about the "drawable" directory here; we just want to search all folders
    // in res/ and look for the first match in any of them.
    for (File resTypeDir : resTypes) {
      if (resTypeDir.isDirectory() && resourceType.equals(ResourceFolderType.getFolderType(resTypeDir.getName()))) {
        File[] files = resTypeDir.listFiles();
        if (files != null) {
          for (File f : files) {
            if (FileUtil.getNameWithoutExtension(f).equalsIgnoreCase(name)) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  /**
   * Like {@link #resourceExists(AndroidProjectPaths, ResourceFolderType, String)} but a useful
   * fallback if information about the current paths is not known.
   */
  public static boolean resourceExists(@NotNull AndroidFacet facet, @NotNull ResourceType resourceType, @NotNull String name) {
    AppResourceRepository repository = facet.getAppResources(true);
    return repository.hasResourceItem(resourceType, name);
  }

  private AssetStudioUtils() {
  }
}
