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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Base include folder resolver. Resolvers generally work against regular expressions.
 * For this reason, paths are expected to be:
 * (1) Fully qualified
 * (2) Have unix style separators
 * Conversion to File or VirtualFile happens downstream of the resolver logic.
 */
abstract public class IncludeResolver {

  /**
   * Get the complete set of resolvers (where order matters).
   */
  @NotNull
  public static IncludeResolver getGlobalResolver(@Nullable File ndkPath) {
    IncludeResolver[] resolvers =
      new IncludeResolver[]{new NdkIncludeResolver(ndkPath), new CDepIncludeResolver(), new CocosIncludeResolver(),
        thirdParty(), new PlainFolderIncludeResolver()};
    return new IncludeResolver() {
      @Override
      public SimpleIncludeValue resolve(@NotNull File includeFolder) {
        for (IncludeResolver resolver : resolvers) {
          SimpleIncludeValue dependency = resolver.resolve(includeFolder);
          if (dependency != null) {
            return dependency;
          }
        }
        return null;
      }
    };
  }

  /**
   * Generate a Third Party resolver.
   */
  @NotNull
  static IncludeResolver thirdParty() {
    return new IndexedRegularExpressionIncludeResolver(PackageType.ThirdParty, "^(.*)(/third[_-]party/(.*?)/.*)$");
  }

  @Nullable
  public abstract SimpleIncludeValue resolve(@NotNull File includeFolder);
}
