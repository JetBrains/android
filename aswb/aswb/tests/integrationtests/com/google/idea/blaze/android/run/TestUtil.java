/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.base.bazel.BepUtils.FileArtifact;
import java.nio.file.Path;

public final class TestUtil {

  private TestUtil() {}

  public static Artifact toArtifact(FileArtifact fileArtifact) {
    Path artifactPath = Path.of(fileArtifact.getArtifactPath());
    String blazeOutRelativePath = artifactPath.subpath(1, artifactPath.getNameCount()).toString();
    // TODO: solodkyy - JavaDoc on Artifact says that this path includes bazel-out.
    return Artifact.newBuilder().setExecRootPath(blazeOutRelativePath).build();
  }
}
