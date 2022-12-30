/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.navigator.nodes.ndk.includes.RealWorldExamples;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class ResolverTests {
  static final String PATH_TO_NDK = "/path/to/ndk-bundle";
  static final String PATH_TO_SIDE_BY_SIDE_NDK = "/path/to/ndk/19.0.5232133";
  static final File ROOT_OF_RELATIVE_INCLUDE_PATHS = new File("/a/b/c/d/e/f/g/h/i");

  /**
   * Utility method that resolves the given sourceIncludes against the given IncludeResolver.
   * The result is a list of SimpleIncludeValue which represents the resolvers understanding of the given paths.
   */
  @NotNull
  static List<SimpleIncludeValue> resolvedIncludes(@NotNull IncludeResolver resolver, @NotNull String... sourceIncludes) {
    List<File> seen = new ArrayList<>();
    List<SimpleIncludeValue> resolutions = new ArrayList<>();
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(PATH_TO_NDK, sourceIncludes);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    for (File include : set.getIncludesInOrder()) {
      if (seen.contains(include)) {
        continue;
      }
      seen.add(include);
      resolutions.add(resolver.resolve(include));
    }
    return resolutions;
  }

  /**
   * Utility method that  resolves all distinct include path examples from RealWorldExamples and returns the resolved
   * SimpleIncludeValue for them.
   */
  @NotNull
  static List<ResolutionResult> resolveAllRealWorldExamples(@NotNull IncludeResolver resolver) {
    Set<File> seen = new HashSet<>();
    List<ResolutionResult> result = new ArrayList<>();
    for (List<String> includes : RealWorldExamples.getConcreteCompilerIncludeFlags(PATH_TO_NDK)) {
      IncludeSet set = new IncludeSet();
      set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
      for (File include : set.getIncludesInOrder()) {
        if (seen.contains(include)) {
          continue;
        }
        seen.add(include);
        result.add(new ResolutionResult(include.getPath(), resolver.resolve(include)));
      }
    }
    return result;
  }

  static class ResolutionResult {
    final String myOriginalPath;
    final SimpleIncludeValue myResolution;

    ResolutionResult(String originalPath, SimpleIncludeValue resolution) {
      myOriginalPath = originalPath;
      myResolution = resolution;
    }
  }
}
