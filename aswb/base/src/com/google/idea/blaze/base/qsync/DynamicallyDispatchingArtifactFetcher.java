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
package com.google.idea.blaze.base.qsync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.util.Pair;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A delegating {@link ArtifactFetcher} that relies on {@link
 * ArtifactFetcher#supportedArtifactType()} to chose fetchers to fetchers to delegate fetching to.
 */
@VisibleForTesting
public class DynamicallyDispatchingArtifactFetcher implements ArtifactFetcher<OutputArtifact> {

  private final ImmutableList<ArtifactFetcher<?>> fetchers;

  public DynamicallyDispatchingArtifactFetcher(ImmutableList<ArtifactFetcher<?>> fetchers) {
    this.fetchers = fetchers;
  }

  @Override
  public ListenableFuture<?> copy(
      ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
      Context<?> context) {
    ImmutableList<ListenableFuture<?>> futures =
        artifactToDest.entrySet().stream()
            .collect(Collectors.groupingBy(it -> it.getKey().getClass()))
            .entrySet()
            .stream()
            .map(it -> Pair.create(findArtifactFetcherFor(it.getKey()), it.getValue()))
            .map(it -> fetchBy(it.first, ImmutableMap.copyOf(it.second), context))
            .collect(ImmutableList.toImmutableList());

    return Futures.allAsList(futures);
  }

  private static ListenableFuture<?> fetchBy(ArtifactFetcher<OutputArtifact> fetcher,
                                             ImmutableMap<OutputArtifact, ArtifactDestination> artifactFetchingMap,
                                             Context<?> context) {
    final var sw = Stopwatch.createStarted();
    context.output(PrintOutput.output("Fetching %d artifacts using %s", artifactFetchingMap.size(), fetcher.toString()));
    return Futures.transform(fetcher.copy(artifactFetchingMap, context), it -> {
      context.output(
        PrintOutput.output("Done fetching %d artifacts using %s in %sms", artifactFetchingMap.size(), fetcher.toString(), sw.elapsed(
          TimeUnit.MILLISECONDS)));
      return it;
    }, MoreExecutors.directExecutor());
  }

  @Override
  public Class<OutputArtifact> supportedArtifactType() {
    return OutputArtifact.class;
  }

  @SuppressWarnings("unchecked")
  private <T extends OutputArtifact> ArtifactFetcher<T> findArtifactFetcherFor(
      Class<? extends OutputArtifact> artifactClass) {
    for (ArtifactFetcher<?> artifactFetcher : fetchers) {
      if (artifactFetcher.supportedArtifactType().isAssignableFrom(artifactClass)) {
        return (ArtifactFetcher<T>) artifactFetcher;
      }
    }
    throw new IllegalStateException(
        String.format("No artifact fetcher is registered for: artifactClass=%s", artifactClass));
  }
}
