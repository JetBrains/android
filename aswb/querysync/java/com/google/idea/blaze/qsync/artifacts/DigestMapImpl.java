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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/**
 * Digest map based on a map of (path -> digest) and a set of targets that failed to build fully.
 */
public class DigestMapImpl implements DigestMap {
  private final ImmutableMap<Path, String> digestMap;
  private final ImmutableSet<Label> targetsWithErrors;

  public DigestMapImpl(
      ImmutableMap<Path, String> digestMap, ImmutableSet<Label> targetsWithErrors) {
    this.digestMap = digestMap;
    this.targetsWithErrors = targetsWithErrors;
  }

  @Override
  public Optional<String> digestForArtifactPath(Path path, Label fromTarget) {
    if (digestMap.containsKey(path)) {
      String digest = digestMap.get(path);
      Preconditions.checkState(!digest.isEmpty(), "Empty digest for %s from %s", path, fromTarget);
      return Optional.of(digest);
    }
    if (!targetsWithErrors.isEmpty()) {
      // The build had partial failures, so this artifact was not built.
      // This can happen as the aspect can return references to artifacts even if they subsequently
      // fail to build.
      return Optional.empty();
    }
    throw new IllegalStateException(
        String.format("No digest for artifact %s from %s", path, fromTarget));
  }

  @Override
  public Iterator<Path> directoryContents(Path directory) {
    return digestMap.keySet().stream().filter(f -> f.startsWith(directory)).iterator();
  }
}
