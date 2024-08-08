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
package com.google.idea.blaze.qsync.artifacts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.idea.blaze.qsync.java.artifacts.AspectProto;
import com.google.idea.blaze.qsync.java.artifacts.AspectProto.OutputArtifact;
import java.util.Arrays;
import java.util.Optional;

/** Test helpers for working with {@link AspectProto.OutputArtifact} instances. */
public class AspectProtos {

  private AspectProtos() {}

  public static AspectProto.OutputArtifact fileArtifact(String path) {
    return AspectProto.OutputArtifact.newBuilder().setFile(path).build();
  }

  public static AspectProto.OutputArtifact directoryArtifact(String path) {
    return AspectProto.OutputArtifact.newBuilder().setDirectory(path).build();
  }

  public static ImmutableList<OutputArtifact> fileArtifacts(String... paths) {
    return Arrays.stream(paths)
        .map(AspectProtos::fileArtifact)
        .collect(ImmutableList.toImmutableList());
  }

  public static Optional<String> getPathString(OutputArtifact artifact) {
    return switch (artifact.getPathCase()) {
      case FILE -> Optional.of(artifact.getFile());
      case DIRECTORY -> Optional.of(artifact.getDirectory());
      case PATH_NOT_SET -> Optional.empty();
    };
  }

  public static ImmutableList<String> getPathStrings(Iterable<OutputArtifact> artifacts) {
    return Streams.stream(artifacts)
        .map(AspectProtos::getPathString)
        .flatMap(Optional::stream)
        .collect(ImmutableList.toImmutableList());
  }
}
