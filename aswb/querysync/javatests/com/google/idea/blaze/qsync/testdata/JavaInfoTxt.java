/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.testdata;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.runfiles.Runfiles;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Enumerates and provides access to {@code .java-info.txt} files written at build time using the
 * test support methods {@code collect_dependencies_aspect_for_tests} and {@code
 * write_java_info_txt_rule_for_tests} inside {@code build_dependencies.bzl}.
 */
public enum JavaInfoTxt {
  /** A simple java project with a single external dependency */
  EXTERNAL_DEP("externaldep", "java_info", "externaldep"),
  /** A simple java project with a single internal dependency */
  INTERNAL_DEP("internaldep", "java_info", "internaldep"),
  /** A simple java project with a single external dependency, which in turn exports another external target. */
  EXTERNAL_EXPORTS("externalexports", "java_info", "externalexports"),
  /** A simple java project that loads the workspace root. */
  WORKSPACE_ROOT_INCLUDED("workspacerootincluded", "java_info", "workspacerootincluded");

  public final ImmutableList<String> paths;

  /**
   * @param testdataDirName The directory with {@code testdata} that this test case is in
   * @param targetName The name of the {@code write_java_info_txt_rule_for_tests} rule target within
   *     {@code testdataDirName}
   * @param depsNames The label names of all of the {@code deps} of rule {@code targetName}
   */
  JavaInfoTxt(String testdataDirName, String targetName, String... depsNames) {
    this.paths =
        Arrays.stream(depsNames)
            .map(dep -> String.format("%s/%s.%s.java-info.txt", testdataDirName, targetName, dep))
            .collect(ImmutableList.toImmutableList());
  }

  private static final String WORKSPACE_NAME = "_main";

  public static final Path ROOT =
      Path.of(
          "tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata");

  public ImmutableList<Path> getPaths() throws IOException {
    Path runfilePath =
        Path.of(Runfiles.preload().unmapped().rlocation(WORKSPACE_NAME + "/" + ROOT));
    return paths.stream()
        .map(path -> runfilePath.resolve(path))
        .collect(ImmutableList.toImmutableList());
  }

  public ImmutableList<JavaArtifacts> readProtos() throws IOException {
    ImmutableList.Builder<JavaArtifacts> list = ImmutableList.builder();
    for (Path p : getPaths()) {
      JavaArtifacts.Builder builder = JavaArtifacts.newBuilder();
      try (InputStream inputStream = Files.newInputStream(p)) {
        TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
        parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
        list.add(builder.build());
      }
    }
    return list.build();
  }

  public JavaArtifacts readOnlyProto() throws IOException {
    return Iterables.getOnlyElement(readProtos());
  }
}
