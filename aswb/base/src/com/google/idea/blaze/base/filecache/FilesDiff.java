/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.io.ModifiedTimeScanner;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A data class representing the diff between two sets of files.
 *
 * <p>The type of file in the two sets can be different (F vs S), with a one-way F to S mapping
 * provided in {@link #diffFiles}.
 */
@AutoValue
public abstract class FilesDiff<F, S> {

  public abstract ImmutableMap<F, Long> getNewFileState();

  public abstract ImmutableList<F> getUpdatedFiles();

  public abstract ImmutableSet<S> getRemovedFiles();

  /** Diffs a collection of files based on timestamps. */
  public static FilesDiff<File, File> diffFileTimestamps(
      @Nullable ImmutableMap<File, Long> oldState, Collection<File> files)
      throws InterruptedException, ExecutionException {
    ImmutableMap<File, Long> newState = ModifiedTimeScanner.readTimestamps(files);
    return diffFiles(oldState, newState);
  }

  public static <F> FilesDiff<F, F> diffFiles(
      @Nullable ImmutableMap<F, Long> oldState, ImmutableMap<F, Long> newState) {
    return diffFiles(oldState, newState, Functions.identity());
  }

  public static <F, S> FilesDiff<F, S> diffFiles(
      @Nullable ImmutableMap<S, Long> oldState,
      ImmutableMap<F, Long> newState,
      Function<F, S> mappingFn) {

    // Find changed/new
    final ImmutableMap<S, Long> previous = oldState != null ? oldState : ImmutableMap.of();
    ImmutableList<F> updated =
        newState.entrySet().stream()
            .filter(e -> !e.getValue().equals(previous.get(mappingFn.apply(e.getKey()))))
            .map(Map.Entry::getKey)
            .collect(toImmutableList());

    // Find removed
    Set<S> removed = new HashSet<>(previous.keySet());
    newState.forEach((k, v) -> removed.remove(mappingFn.apply(k)));

    return new AutoValue_FilesDiff<>(
        newState, ImmutableList.copyOf(updated), ImmutableSet.copyOf(removed));
  }
}
