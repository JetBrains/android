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
package com.google.idea.blaze.qsync.util;


import com.google.idea.blaze.qsync.java.PackageStatementParser;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;

/**
 * Simple CLI tool to run the logic inside {@link SrcJarInnerPathFinder#findInnerJarPaths} on a jar
 * file provided as a CLI parameter.
 *
 * <p>Example usage:
 *
 * <pre>
 *   bazel run //tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/util:get_jar_roots -- $(pwd)/tools/build_defs/kotlin/release/rules/kotlin-stdlib-sources.jar
 * </pre>
 */
public class GetJarRoots {

  public static void main(String[] args) {
    System.exit(new GetJarRoots(Path.of(args[0])).run());
  }

  private final Path jarFile;

  GetJarRoots(Path jarFile) {
    this.jarFile = jarFile;
  }

  int run() {
    SrcJarInnerPathFinder pathFinder = new SrcJarInnerPathFinder(new PackageStatementParser());
    ProjectPath projectPath = ProjectPath.workspaceRelative(jarFile.getFileName());
    pathFinder
        .findInnerJarPaths(
            jarFile.toFile(),
            AllowPackagePrefixes.EMPTY_PACKAGE_PREFIXES_ONLY,
            jarFile.getFileName().toString())
        .stream()
        .map(p -> projectPath.withInnerJarPath(p.path()))
        .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath()))
        .forEach(System.out::println);
    return 0;
  }
}
