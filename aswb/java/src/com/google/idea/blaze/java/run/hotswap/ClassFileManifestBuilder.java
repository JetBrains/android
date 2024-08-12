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
package com.google.idea.blaze.java.run.hotswap;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.io.File;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/** Builds a .class file manifest to support hotswapping. */
public class ClassFileManifestBuilder {

  /** Used to associate data with an {@link ExecutionEnvironment}. */
  private static final Key<AtomicReference<ClassFileManifest>> MANIFEST_KEY =
      Key.create("blaze.debug.class.manifest");

  /** Called when initializing our run profile state, to support hotswapping. */
  public static void initState(ExecutionEnvironment env) {
    if (!HotSwapUtils.canHotSwap(env)) {
      return;
    }
    if (env.getCopyableUserData(MANIFEST_KEY) == null) {
      env.putCopyableUserData(MANIFEST_KEY, new AtomicReference<>());
    }
  }

  @Nullable
  static ClassFileManifest getManifest(ExecutionEnvironment env) {
    AtomicReference<ClassFileManifest> ref = env.getCopyableUserData(MANIFEST_KEY);
    return ref != null ? ref.get() : null;
  }

  /**
   * Builds a .class file manifest, then diffs against any previously calculated manifest for this
   * debugging session.
   *
   * @return null if no diff is available (either no manifest could be calculated, or no previously
   *     calculated manifest is available.
   */
  @Nullable
  public static ClassFileManifest.Diff buildManifest(
      ExecutionEnvironment env, @Nullable HotSwapProgress progress) throws ExecutionException {
    if (!HotSwapUtils.canHotSwap(env)) {
      return null;
    }
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    Project project = configuration.getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      throw new ExecutionException("Not synced yet; please sync project");
    }

    BlazeVersionData versionData = projectData.getBlazeVersionData();

    JavaClasspathAspectStrategy aspectStrategy =
        JavaClasspathAspectStrategy.findStrategy(versionData);
    if (aspectStrategy == null) {
      return null;
    }

    SaveUtil.saveAllFiles();
    // Explicitly create a local build helper because the logic below assumes the JARs to be present
    // locally
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.createForLocalBuild(project)) {

      ListenableFuture<BuildResult> buildOperation =
          BlazeBeforeRunCommandHelper.runBlazeCommand(
              BlazeCommandName.BUILD,
              configuration,
              buildResultHelper,
              aspectStrategy.getBuildFlags(versionData),
              ImmutableList.of(),
              BlazeInvocationContext.runConfigContext(
                  ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true),
              "Building debug binary");

      if (progress != null) {
        progress.setCancelWorker(() -> buildOperation.cancel(true));
      }
      try {
        BuildResult result = buildOperation.get();
        if (result.status != BuildResult.Status.SUCCESS) {
          throw new ExecutionException("Blaze failure building debug binary");
        }
      } catch (InterruptedException | CancellationException e) {
        buildOperation.cancel(true);
        throw new RunCanceledByUserException();
      } catch (java.util.concurrent.ExecutionException e) {
        throw new ExecutionException(e);
      }
      ImmutableList<File> jars;
      try {
        jars =
            LocalFileArtifact.getLocalFiles(
                    buildResultHelper.getArtifactsForOutputGroup(
                        JavaClasspathAspectStrategy.OUTPUT_GROUP, file -> true))
                .stream()
                .filter(f -> f.getName().endsWith(".jar"))
                .collect(toImmutableList());
      } catch (GetArtifactsException e) {
        throw new ExecutionException("Failed to get debug binary: " + e.getMessage());
      }
      ClassFileManifest oldManifest = getManifest(env);
      ClassFileManifest newManifest = ClassFileManifest.build(jars, oldManifest);
      env.getCopyableUserData(MANIFEST_KEY).set(newManifest);
      return oldManifest != null
          ? ClassFileManifest.modifiedClasses(oldManifest, newManifest)
          : null;
    }
  }
}
