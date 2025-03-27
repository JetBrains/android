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
package com.google.idea.blaze.qsync.testdata;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.runfiles.Runfiles;
import com.google.idea.blaze.common.Label;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public enum TestData {

  ANDROID_AIDL_SOURCE_QUERY("aidl"),
  ANDROID_LIB_QUERY("android"),
  DOES_DEPENDENCY_PATH_CONTAIN_RULES("deppathkinds"),
  JAVA_EXPORTED_DEP_QUERY("exports"),
  JAVA_LIBRARY_EXTERNAL_DEP_QUERY("externaldep"),
  JAVA_LIBRARY_INTERNAL_DEP_QUERY("internaldep", "nodeps"),
  JAVA_LIBRARY_MULTI_TARGETS("multitarget"),
  JAVA_LIBRARY_NESTED_PACKAGE("nested"),
  JAVA_LIBRARY_NO_DEPS_QUERY("nodeps"),
  JAVA_LIBRARY_PROTO_DEP_QUERY("protodep"),
  JAVA_LIBRARY_TRANSITIVE_DEP_QUERY("transitivedep", "externaldep"),
  JAVA_LIBRARY_TRANSITIVE_INTERNAL_DEP_QUERY("transitiveinternaldep", "internaldep", "nodeps"),
  BUILDINCLUDES_QUERY("buildincludes"),
  FILEGROUP_QUERY("filegroup"),
  CC_LIBRARY_QUERY("cc"),
  CC_EXTERNAL_DEP_QUERY("cc_externaldep"),
  CC_MULTISRC_QUERY("cc_multisrc"),
  PROTO_ONLY_QUERY("protoonly"),
  NESTED_PROTO_QUERY("nestedproto"),
  TAGS_QUERY("tags"),
  EMPTY_QUERY("empty"),
  WORKSPACE_ROOT_INCLUDED_QUERY("workspacerootincluded");

  public final ImmutableList<Path> srcPaths;

  TestData(String... paths) {
    this.srcPaths = stream(paths).map(Path::of).collect(toImmutableList());
  }

  private static final String WORKSPACE_NAME = "_main";

  public static final Path ROOT =
      Path.of(
          "tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata");

  public static final String ROOT_PACKAGE = "//" + ROOT;

  public Path getQueryOutputPath() throws IOException {
    return Path.of(Runfiles.preload().unmapped().rlocation(WORKSPACE_NAME + "/" + ROOT))
        .resolve(name().toLowerCase(Locale.ROOT));
  }

  public ImmutableList<Path> getRelativeSourcePaths() {
    return srcPaths.stream().map(ROOT::resolve).collect(toImmutableList());
  }

  public Path getOnlySourcePath() {
    return Iterables.getOnlyElement(getRelativeSourcePaths());
  }

  /**
   * Gets labels included in this project, making the assumption that each package has a single
   * target who's name matches the directory name.
   */
  public ImmutableList<Label> getAssumedLabels() {
    return srcPaths.stream()
        .map(
            p ->
                Label.fromWorkspacePackageAndName(
                    Label.ROOT_WORKSPACE, ROOT.resolve(p), p.toString()))
        .collect(toImmutableList());
  }

  public Label getAssumedOnlyLabel() {
    return Iterables.getOnlyElement(getAssumedLabels());
  }
}
