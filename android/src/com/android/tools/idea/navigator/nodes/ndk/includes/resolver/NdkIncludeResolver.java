/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.resolver;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType.NdkComponent;

/**
 * Resolver that matches various well-known NDK folder patterns.
 */
public class NdkIncludeResolver extends IncludeResolver {
  @Nullable
  private final IncludeResolver[] myResolvers;

  @Nullable
  private final String myNdkFolderAbsolutePath;

  NdkIncludeResolver(@Nullable File ndkFolder) {
    if (ndkFolder != null) {
      myNdkFolderAbsolutePath = FilenameUtils.separatorsToUnix(ndkFolder.getAbsolutePath());
      myResolvers = new IncludeResolver[] {
        // Contains NDK platform header files
        leafNamed("^({NDKFOLDER})(/platforms/(android-.*?)/arch-.*?(/.*))$"),
        // Contains STL/runtime header files
        leafNamed("^({NDKFOLDER})(/sources/cxx-stl/(.*?)(/.*))$"),
        // Contains third party header files in the NDK like GoogleTest
        leafNamed("^({NDKFOLDER})(/sources/third_party/(.*?)(/.*))$"),
        // Contains specialize toolchains like Rend Script
        leafNamed("^({NDKFOLDER})(/toolchains/(.*?)(/.*))$"),
        // Contains NDK CPU Features header files
        literalNamed("^({NDKFOLDER})(/sources/android/cpufeatures(/.*))$", "CPU Features"),
        // Contains NDK native app glue header files
        literalNamed("^({NDKFOLDER})(/sources/android/native_app_glue(/.*))$", "Native App Glue"),
        // Contains NDK helper files
        literalNamed("^({NDKFOLDER})(/sources/android/ndk_helper(/.*))$", "NDK Helper")
      };
    }
    else {
      myNdkFolderAbsolutePath = null;
      myResolvers = null;
    }
  }

  @Override
  @Nullable
  public SimpleIncludeValue resolve(@NotNull File includeFolder) {
    if (myNdkFolderAbsolutePath == null || myResolvers == null) {
      return null;
    }
    for (IncludeResolver resolver : myResolvers) {
      SimpleIncludeValue classifiedIncludeExpression = resolver.resolve(includeFolder);
      if (classifiedIncludeExpression != null) {
        return classifiedIncludeExpression;
      }
    }
    return null;
  }

  /**
   * Generate an NDK resolver that has a literal leaf name like "CPU Features"
   */
  @NotNull
  private IncludeResolver literalNamed(@NotNull String pattern, @NotNull String name) {
    return new IndexedRegularExpressionIncludeResolver(NdkComponent, concreteNdkFolder(pattern), name);
  }

  /**
   * Generate an NDK resolver that takes its leaf name from the folder path.
   */
  @NotNull
  private IncludeResolver leafNamed(@NotNull String pattern) {
    return new IndexedRegularExpressionIncludeResolver(NdkComponent, concreteNdkFolder(pattern));
  }

  /**
   * Get the given myPattern with NDK folder made concrete.
   */
  @NotNull
  private String concreteNdkFolder(@NotNull String pattern) {
    assert myNdkFolderAbsolutePath != null;
    //noinspection DynamicRegexReplaceableByCompiledPattern
    return pattern.replace("{NDKFOLDER}", myNdkFolderAbsolutePath);
  }
}
