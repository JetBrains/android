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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.lang.buildfile.sync.LanguageSpecResult;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Calls 'blaze info build-language', to retrieve the language spec. */
public class BuildLanguageSpecProviderImpl implements BuildLanguageSpecProvider {

  private static final Logger logger = Logger.getInstance(BuildLanguageSpecProviderImpl.class);

  private BuildLanguageSpec languageSpec = null;

  private final Project project;

  private String blazeRelease;

  // Instantiated by IntelliJ
  public BuildLanguageSpecProviderImpl(Project project) {
    this.project = project;
  }

  @Override
  public BuildLanguageSpec getLanguageSpec() {
    if (Blaze.getProjectType(project) != ProjectType.ASPECT_SYNC) {
      return getLanguageSpecInternal();
    }

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    LanguageSpecResult spec = blazeProjectData.getSyncState().get(LanguageSpecResult.class);
    if (spec == null) {
      return null;
    }
    return spec.getSpec();
  }

  private synchronized void setLanguageSpec(BuildLanguageSpec languageSpec, String blazeRelease) {
    this.languageSpec = languageSpec;
    this.blazeRelease = blazeRelease;
  }

  @Nullable
  private synchronized BuildLanguageSpec getLanguageSpecInternal() {
    return languageSpec;
  }

  @Nullable
  private synchronized String getBlazeRelease() {
    return blazeRelease;
  }

  private void fetchLanguageSpecIfNeeded(BlazeContext context) {
    // Invocations are run in a separate context as the info commands are not crucial or useful for
    // the core sync query.
    BlazeContext fetchContext = BlazeContext.create();
    ListenableFuture<String> releaseFuture = fetchBlazeRelease(fetchContext);

    Futures.addCallback(
        releaseFuture,
        new FutureCallback<String>() {
          @Override
          public void onSuccess(String releaseResult) {
            String previousBlazeRelease = getBlazeRelease();
            if (previousBlazeRelease == null || !previousBlazeRelease.equals(releaseResult)) {
              ListenableFuture<BuildLanguageSpec> specFuture = fetchBuildLanguageSpec(fetchContext);
              Futures.addCallback(
                  specFuture,
                  new FutureCallback<BuildLanguageSpec>() {
                    @Override
                    public void onSuccess(BuildLanguageSpec buildLanguageSpec) {
                      setLanguageSpec(buildLanguageSpec, releaseResult);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                      logger.error("Failed to fetch build language spec", throwable);
                      context.output(
                          IssueOutput.error(
                                  "Failed to obtain Build language spec. Build language support may"
                                      + " be limited.")
                              .build());
                    }
                  },
                  BlazeExecutor.getInstance().getExecutor());
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            logger.error("Could not fetch blaze version", throwable);
            context.output(
                IssueOutput.error(
                        String.format(
                            "Failed to obtain %s version. Build language support may be limited.",
                            Blaze.buildSystemName(project)))
                    .build());
          }
        },
        BlazeExecutor.getInstance().getExecutor());
  }

  private ListenableFuture<BuildLanguageSpec> fetchBuildLanguageSpec(BlazeContext context) {
    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getDefaultInvoker(project, context);
    ListenableFuture<byte[]> future =
        BlazeInfoRunner.getInstance()
            .runBlazeInfoGetBytes(
                project, invoker, context, ImmutableList.of(), BlazeInfo.BUILD_LANGUAGE);

    /**
     * TransformAsync allows the checked {@link InvalidProtocolBufferException} to propagate to
     * caller
     */
    return Futures.transformAsync(
        future,
        bytes -> {
          ExtensionRegistry registry = ExtensionRegistry.newInstance();
          Build.registerAllExtensions(registry);
          return Futures.immediateFuture(
              BuildLanguageSpec.fromProto(Build.BuildLanguage.parseFrom(bytes, registry)));
        },
        BlazeExecutor.getInstance().getExecutor());
  }

  private ListenableFuture<String> fetchBlazeRelease(BlazeContext context) {
    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getDefaultInvoker(project, context);
    ListenableFuture<byte[]> future =
        BlazeInfoRunner.getInstance()
            .runBlazeInfoGetBytes(project, invoker, context, ImmutableList.of(), BlazeInfo.RELEASE);
    return Futures.transform(
        future,
        bytes -> new String(bytes, UTF_8).trim(),
        BlazeExecutor.getInstance().getExecutor());
  }

  /** {@link SyncListener} for fetching BUILD language specs after sync, if needed */
  public static class Listener implements SyncListener {

    // Callback is specific to query sync
    @Override
    public void afterQuerySync(Project project, BlazeContext context) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      BuildLanguageSpecProvider buildLanguageSpecProvider =
          BuildLanguageSpecProvider.getInstance(project);
      if (!(buildLanguageSpecProvider instanceof BuildLanguageSpecProviderImpl)) {
        logger.error(
            String.format(
                "Expected BuildLanguageSpecProviderImpl but found %s",
                buildLanguageSpecProvider.getClass()));
        return;
      }
      BuildLanguageSpecProviderImpl provider =
          (BuildLanguageSpecProviderImpl) buildLanguageSpecProvider;

      ProgressManager.getInstance()
          .run(
              new Backgroundable(project, "Fetching BUILD language spec") {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                  provider.fetchLanguageSpecIfNeeded(context);
                }
              });
    }
  }
}
