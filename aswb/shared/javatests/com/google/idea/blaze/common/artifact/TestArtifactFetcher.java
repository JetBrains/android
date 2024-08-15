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
package com.google.idea.blaze.common.artifact;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.idea.blaze.common.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Writes fake artifacts on demands, fabricating their contents.
 *
 * <p>After a call to {@link #copy(ImmutableMap, Context)}, nothing it done until {@link
 * #executePendingTasks()} is called. After that is called, the fetched artifacts will be written to
 * disk and the future(s) completed.
 */
public class TestArtifactFetcher implements ArtifactFetcher<OutputArtifact> {

  class Task<T> {
    public final SettableFuture<T> done = SettableFuture.create();
    private final Callable<T> workload;

    Task(Callable<T> workload) {
      this.workload = workload;
    }

    void run() {
      done.setFuture(executor.submit(workload));
    }

    void fail() {
      done.setException(new Exception("failed"));
    }
  }

  private final List<Task> pendingTasks = Lists.newArrayList();
  private final List<String> requestedDigests = Lists.newArrayList();
  private final Set<String> completedDigests = Sets.newHashSet();
  private final ListeningExecutorService executor = newDirectExecutorService();

  @Override
  public ListenableFuture<?> copy(
      ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
      Context<?> context) {
    requestedDigests.addAll(
        artifactToDest.keySet().stream().map(OutputArtifact::getDigest).collect(toImmutableList()));
    Task t =
        new Task(
            () -> {
              for (Entry<? extends OutputArtifact, ArtifactDestination> entry :
                  artifactToDest.entrySet()) {
                Files.writeString(
                    entry.getValue().path,
                    getExpectedArtifactContents(entry.getKey().getDigest()),
                    StandardCharsets.UTF_8);
                completedDigests.add(entry.getKey().getDigest());
              }
              return null;
            });
    pendingTasks.add(t);
    return t.done;
  }

  public String getExpectedArtifactContents(String digest) {
    return String.format("Artifact created by %s\n%s", TestArtifactFetcher.class.getName(), digest);
  }

  public void executePendingTasks() {
    pendingTasks.forEach(Task::run);
    pendingTasks.clear();
  }

  public void executeNewestTask() {
    pendingTasks.remove(pendingTasks.size() - 1).run();
  }

  public void failNewestTask() {
    pendingTasks.remove(pendingTasks.size() - 1).fail();
  }

  public void executeOldestTask() {
    pendingTasks.remove(0).run();
  }

  public void failOldestTask() {
    pendingTasks.remove(0).fail();
  }

  public ImmutableList<String> takeRequestedDigests() {
    ImmutableList<String> requested = ImmutableList.copyOf(requestedDigests);
    requestedDigests.clear();
    return requested;
  }

  public void flushTasks() {
    while (!pendingTasks.isEmpty()) {
      executeOldestTask();
    }
  }

  public ImmutableSet<String> getCompletedDigests() {
    return ImmutableSet.copyOf(completedDigests);
  }

  @Override
  public Class<OutputArtifact> supportedArtifactType() {
    return OutputArtifact.class;
  }
}
