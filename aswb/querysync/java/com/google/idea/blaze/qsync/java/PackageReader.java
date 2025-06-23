/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.java;

import com.google.idea.blaze.common.Context;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Calculates the package for a java source file. */
public interface PackageReader {

  /** Calculates packages for java source files. */
  interface ParallelReader {
    Map<Path, String> readPackages(Context<?> context, PackageReader reader, List<Path> paths);

    @TestOnly
    class SingleThreadedForTests implements ParallelReader {
      @Override
      public Map<Path, String> readPackages(Context<?> context, PackageReader reader, List<Path> paths) {
        Map<Path, String> pathToPkgMap = new LinkedHashMap<>(paths.size());
        for (Path path : paths) {
          String pkg = reader.readPackage(context, path);
          if (pkg != null) {
            pathToPkgMap.put(path, pkg);
          }
        }
        return pathToPkgMap;
      }

    }
  }

  @Nullable
  String readPackage(Context<?> context, Path path);
}
