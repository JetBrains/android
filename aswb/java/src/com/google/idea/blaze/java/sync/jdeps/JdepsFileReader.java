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
package com.google.idea.blaze.java.sync.jdeps;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.view.proto.Deps;
import com.google.devtools.build.lib.view.proto.Deps.Dependency;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactsDiff;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.google.idea.blaze.java.sync.jdeps.JdepsState.JdepsData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/** Reads jdeps from the ide info result. */
public class JdepsFileReader {
  private static final Logger logger = Logger.getInstance(JdepsFileReader.class);

  private static class Result {
    OutputArtifactWithoutDigest output;
    TargetKey targetKey;
    List<String> dependencies;

    Result(OutputArtifactWithoutDigest output, TargetKey targetKey, List<String> dependencies) {
      this.output = output;
      this.targetKey = targetKey;
      this.dependencies = dependencies;
    }
  }

  /** Loads any updated jdeps files since the last invocation of this method. */
  @Nullable
  public JdepsMap loadJdepsFiles(
      Project project,
      BlazeContext parentContext,
      ArtifactLocationDecoder artifactLocationDecoder,
      Collection<TargetIdeInfo> targetsToLoad,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      SyncMode syncMode) {
    JdepsState oldState =
        previousSyncState != null ? previousSyncState.get(JdepsState.class) : null;
    JdepsState jdepsState =
        Scope.push(
            parentContext,
            (context) -> {
              context.push(new TimingScope("LoadJdepsFiles", EventType.Other));
              try {
                return doLoadJdepsFiles(
                    project, context, artifactLocationDecoder, oldState, targetsToLoad, syncMode);
              } catch (InterruptedException e) {
                throw new ProcessCanceledException(e);
              } catch (ExecutionException e) {
                context.setHasError();
                logger.error(e);
              }
              return null;
            });
    if (jdepsState == null) {
      return null;
    }
    syncStateBuilder.put(jdepsState);
    return jdepsState.getJdepsMap()::get;
  }

  @Nullable
  private JdepsState doLoadJdepsFiles(
      Project project,
      BlazeContext context,
      ArtifactLocationDecoder decoder,
      @Nullable JdepsState oldState,
      Collection<TargetIdeInfo> targetsToLoad,
      SyncMode syncMode)
      throws InterruptedException, ExecutionException {
    Map<OutputArtifactWithoutDigest, TargetKey> fileToTargetMap = Maps.newHashMap();
    for (TargetIdeInfo target : targetsToLoad) {
      BlazeArtifact output = resolveJdepsOutput(decoder, target);
      if (output instanceof OutputArtifactWithoutDigest) {
        fileToTargetMap.put((OutputArtifactWithoutDigest) output, target.getKey());
      }
    }

    ArtifactsDiff diff =
        ArtifactsDiff.diffArtifacts(
            oldState != null ? oldState.getArtifactState() : null, fileToTargetMap.keySet());

    // TODO: handle prefetching for arbitrary OutputArtifacts
    List<OutputArtifactWithoutDigest> outputArtifacts = diff.getUpdatedOutputs();
    // b/187759986: if there was no build, then all the needed artifacts should've been cached
    // already. Additional logging to identify what is going wrong.
    if (!outputArtifacts.isEmpty() && !syncMode.involvesBlazeBuild()) {
      logger.warn(
          "ArtifactDiff: "
              + outputArtifacts.size()
              + " outputs need to be updated during SyncMode.NO_BUILD ");
      if (oldState == null) {
        logger.warn("ArtifactDiff: oldState == null, we failed to load prior JdepsState.");
      } else {
        // Do not list all artifacts since it may be pretty long.
        if (oldState.getArtifactState().size() != fileToTargetMap.size()) {
          logger.warn(
              "Existing artifact state does not match with target map."
                  + " [oldState.getArtifactState().size() = "
                  + oldState.getArtifactState().size()
                  + ", fileToTargetMap.size() = "
                  + fileToTargetMap.size()
                  + "]");
        }
      }
    }

    ListenableFuture<?> downloadArtifactsFuture =
        RemoteArtifactPrefetcher.getInstance()
            .downloadArtifacts(
                /* projectName= */ project.getName(),
                /* outputArtifacts= */ RemoteOutputArtifact.getRemoteArtifacts(outputArtifacts));
    ListenableFuture<?> fetchLocalFilesFuture =
        PrefetchService.getInstance()
            .prefetchFiles(LocalFileArtifact.getLocalFiles(outputArtifacts), true, false);
    if (!FutureUtil.waitForFuture(
            context, Futures.allAsList(downloadArtifactsFuture, fetchLocalFilesFuture))
        .timed("FetchJdeps", EventType.Prefetching)
        .withProgressMessage("Reading jdeps files...")
        .run()
        .success()) {
      return null;
    }

    AtomicLong totalSizeLoaded = new AtomicLong(0);

    List<ListenableFuture<Result>> futures = Lists.newArrayList();
    for (OutputArtifactWithoutDigest updatedFile : outputArtifacts) {
      futures.add(
          FetchExecutor.EXECUTOR.submit(
              () -> {
                totalSizeLoaded.addAndGet(updatedFile.getLength());
                try (InputStream inputStream = updatedFile.getInputStream()) {
                  Deps.Dependencies dependencies = Deps.Dependencies.parseFrom(inputStream);
                  if (dependencies == null) {
                    return null;
                  }
                  List<String> deps =
                      dependencies.getDependencyList().stream()
                          .filter(dep -> relevantDep(dep))
                          .map(Dependency::getPath)
                          .collect(toImmutableList());
                  TargetKey targetKey = fileToTargetMap.get(updatedFile);
                  return new Result(updatedFile, targetKey, deps);
                } catch (IOException e) {
                  logger.info("Could not read jdeps file: " + updatedFile);
                  return null;
                }
              }));
    }

    JdepsState.Builder state = JdepsState.builder();
    if (oldState != null) {
      state.list.addAll(oldState.data);
    }
    state.removeArtifacts(
        diff.getUpdatedOutputs().stream()
            .map(OutputArtifactWithoutDigest::toArtifactState)
            .collect(toImmutableList()));
    state.removeArtifacts(diff.getRemovedOutputs());
    for (Result result : Futures.allAsList(futures).get()) {
      if (result != null) {
        state.list.add(
            JdepsData.create(
                result.targetKey, result.dependencies, result.output.toArtifactState()));
      }
    }
    context.output(
        PrintOutput.log(
            String.format(
                "Loaded %d jdeps files, total size %dkB",
                diff.getUpdatedOutputs().size(), totalSizeLoaded.get() / 1024)));
    return state.build();
  }

  private static boolean relevantDep(Deps.Dependency dep) {
    // we only want explicit or implicit deps that were actually resolved by the compiler, not ones
    // that are available for use in the same package
    return dep.getKind() == Deps.Dependency.Kind.EXPLICIT
        || dep.getKind() == Deps.Dependency.Kind.IMPLICIT;
  }

  @Nullable
  private static BlazeArtifact resolveJdepsOutput(
      ArtifactLocationDecoder decoder, TargetIdeInfo target) {
    JavaIdeInfo javaIdeInfo = target.getJavaIdeInfo();
    if (javaIdeInfo == null || javaIdeInfo.getJdepsFile() == null) {
      return null;
    }
    return decoder.resolveOutput(javaIdeInfo.getJdepsFile());
  }
}
