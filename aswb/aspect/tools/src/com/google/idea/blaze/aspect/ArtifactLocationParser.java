/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.aspect.Common.ArtifactLocation;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Parses an {@link ArtifactLocation} from a comma-separate list of string-encoded fields. */
@VisibleForTesting
public final class ArtifactLocationParser {
  private ArtifactLocationParser() {}

  private static final Splitter SPLITTER = Splitter.on(',');

  @VisibleForTesting
  static final String INVALID_FORMAT =
      "Expected format rootExecutionPathFragment,relPath,isExternal";

  private static Path getPath(String pathString) {
    return FileSystems.getDefault().getPath(pathString);
  }

  /** Parse a colon-separated list of string-encoded {@link ArtifactLocation}s. */
  static List<ArtifactLocation> parseList(String input) {
    ImmutableList.Builder<ArtifactLocation> builder = ImmutableList.builder();
    for (String piece : input.split(":")) {
      if (!piece.isEmpty()) {
        builder.add(parse(piece));
      }
    }
    return builder.build();
  }

  /** Parse an {@link ArtifactLocation} from a comma-separate list of string-encoded fields. */
  static ArtifactLocation parse(String input) {
    Iterator<String> values = SPLITTER.split(input).iterator();
    try {
      Path rootExecutionPathFragment = getPath(values.next());
      Path relPath = getPath(values.next());
      boolean isExternal = values.next().equals("1");
      if (values.hasNext()) {
        throw new IllegalArgumentException(INVALID_FORMAT);
      }

      boolean isSource = rootExecutionPathFragment.toString().isEmpty();
      return ArtifactLocation.newBuilder()
          .setRootExecutionPathFragment(rootExecutionPathFragment.toString())
          .setRelativePath(relPath.toString())
          .setIsSource(isSource)
          .setIsExternal(isExternal)
          .build();

    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException(INVALID_FORMAT);
    }
  }
}
