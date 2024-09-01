/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.io.ModifiedTimeScanner;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * A helper class to find which output artifacts have been modified since they were locally cached.
 */
public final class FileCacheDiffer {

  /**
   * Returns a map from cache key to BlazeArtifact, containing only those outputs which need to be
   * updated in the cache.
   */
  public static <O extends BlazeArtifact> Map<String, O> findUpdatedOutputs(
      Map<String, O> newOutputs,
      Map<String, File> cachedFiles,
      RemoteOutputArtifacts previousOutputs)
      throws InterruptedException, ExecutionException {
    ImmutableMap<File, Long> timestamps = readTimestamps(newOutputs, cachedFiles);
    return newOutputs.entrySet().stream()
        .filter(
            e -> shouldUpdate(e.getKey(), e.getValue(), previousOutputs, timestamps, cachedFiles))
        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static ImmutableMap<File, Long> readTimestamps(
      Map<String, ? extends BlazeArtifact> newOutputs, Map<String, File> cachedFiles)
      throws InterruptedException, ExecutionException {
    boolean timestampsRequired =
        newOutputs.values().stream().anyMatch(a -> a instanceof LocalFileArtifact);
    if (!timestampsRequired) {
      return ImmutableMap.of();
    }
    Set<File> relevantFiles = new HashSet<>();
    for (Map.Entry<String, ? extends BlazeArtifact> entry : newOutputs.entrySet()) {
      BlazeArtifact newOutput = entry.getValue();
      boolean needsTimestamp = newOutput instanceof LocalFileArtifact;
      if (!needsTimestamp) {
        continue;
      }
      relevantFiles.add(((LocalFileArtifact) newOutput).getFile());
      File cached = cachedFiles.get(entry.getKey());
      if (cached != null) {
        relevantFiles.add(cached);
      }
    }
    return ModifiedTimeScanner.readTimestamps(relevantFiles);
  }

  private static boolean shouldUpdate(
      String key,
      BlazeArtifact newOutput,
      RemoteOutputArtifacts previousOutputs,
      Map<File, Long> timestamps,
      Map<String, File> cachedFiles) {
    if (newOutput instanceof LocalFileArtifact) {
      return shouldUpdateLocal((LocalFileArtifact) newOutput, cachedFiles.get(key), timestamps);
    }
    return !cachedFiles.containsKey(key)
        || shouldUpdateRemote((RemoteOutputArtifact) newOutput, previousOutputs);
  }

  private static boolean shouldUpdateRemote(
      RemoteOutputArtifact newOutput, RemoteOutputArtifacts previousOutputs) {
    RemoteOutputArtifact previous = previousOutputs.findRemoteOutput(newOutput.getBazelOutRelativePath());
    if (previous == null) {
      return true;
    }
    ArtifactState previousState = previous.toArtifactState();
    ArtifactState newState = newOutput.toArtifactState();
    return previousState == null || (newState != null && previousState.isMoreRecent(newState));
  }

  private static boolean shouldUpdateLocal(
      LocalFileArtifact newOutput, @Nullable File localFile, Map<File, Long> timestamps) {
    Long oldTimestamp = localFile != null ? timestamps.get(localFile) : null;
    Long newTimestamp = timestamps.get(newOutput.getFile());
    // we should be comparing sync start time, not artifact creation time. For now, keep the
    // behavior unchanged
    return newTimestamp != null && !Objects.equals(newTimestamp, oldTimestamp);
  }
}
