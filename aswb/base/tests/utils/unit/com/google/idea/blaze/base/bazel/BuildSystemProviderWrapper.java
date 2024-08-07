/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.BazelQueryRunner;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A subclass of {@link BuildSystemProvider} that allows functionality to be overridden in tests.
 */
public class BuildSystemProviderWrapper implements BuildSystemProvider {

  private final Supplier<BuildSystemProvider> innerProvider;
  private BuildSystemProvider inner;
  private BuildSystem buildSystem;
  private boolean throwExceptionOnGetBlazeInfo;
  private BuildBinaryType buildBinaryType;
  private SyncStrategy syncStrategy;

  /**
   * Create a wrapper for the given {@link BuildSystemProvider}.
   *
   * <p>This is generally useful for unit tests which do not rely on {@link
   * BuildSystemProvider#EP_NAME} to create the wrapped implementation.
   *
   * @param wrapped The implementation to wrap.
   */
  public BuildSystemProviderWrapper(BuildSystemProvider wrapped) {
    innerProvider = () -> wrapped;
  }

  /**
   * Create a wrapper that will delegate to a {@link BuildSystemProvider} as would be returned by
   * {@link Blaze#getBuildSystemProvider(Project)}.
   *
   * <p>This is generally used by unit testing code to wrap a real implementation of {@link
   * BuildSystemProvider} and can be injected using {@link BuildSystemProvider#EP_NAME}.
   *
   * @param projectSupplier Supplier of the project used to create the build system provider. The
   *     project will not be requested until the code under test calls {@link
   *     Blaze#getBuildSystemProvider(Project)}.
   */
  public BuildSystemProviderWrapper(Supplier<Project> projectSupplier) {
    this.innerProvider =
        () -> {
          // Note: this basically duplicates the functionality of Blaze.getBuildSystemProvider,
          // but with the added instanceof check to ensure we don't infinitely recurse here. This
          // allows this class to be injected in tests using BuildSystemProvider.EP_NAME, and have
          // it behave transparently in this context.
          Project project = projectSupplier.get();
          BuildSystemName name = Blaze.getBuildSystemName(project);
          for (BuildSystemProvider provider : BuildSystemProvider.EP_NAME.getExtensions()) {
            if (provider instanceof BuildSystemProviderWrapper) {
              continue;
            }
            if (provider.buildSystem() == name) {
              return provider;
            }
          }
          throw new IllegalStateException("No BuildSystemProvider found");
        };
  }

  public static BuildSystemProviderWrapper getInstance(Project project) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    if (provider instanceof BuildSystemProviderWrapper) {
      return (BuildSystemProviderWrapper) provider;
    }
    throw new IllegalStateException(
        "BuildSystemProvider not an instance of BuildSystemProviderWrapper");
  }

  private synchronized BuildSystemProvider inner() {
    if (inner == null) {
      inner = innerProvider.get();
      buildSystem = createBuildSystemWrapper(inner.getBuildSystem());
    }
    return inner;
  }

  @NotNull
  protected BuildSystem createBuildSystemWrapper(BuildSystem innerBuildSystem) {
    return new BuildSystemWrapper(innerBuildSystem);
  }

  @Override
  public final BuildSystem getBuildSystem() {
    inner(); // ensure buildSystem is initialized
    return buildSystem;
  }

  @Override
  public BuildSystemName buildSystem() {
    return inner().buildSystem();
  }

  @Override
  public String getBinaryPath(Project project) {
    return inner().getBinaryPath(project);
  }

  @Override
  public WorkspaceRootProvider getWorkspaceRootProvider() {
    return inner().getWorkspaceRootProvider();
  }

  @Override
  public ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root) {
    return inner().buildArtifactDirectories(root);
  }

  @Nullable
  @Override
  public String getRuleDocumentationUrl(RuleDefinition rule) {
    return inner().getRuleDocumentationUrl(rule);
  }

  @Nullable
  @Override
  public String getProjectViewDocumentationUrl() {
    return inner().getProjectViewDocumentationUrl();
  }

  @Nullable
  @Override
  public String getLanguageSupportDocumentationUrl(String relativeDocName) {
    return inner().getLanguageSupportDocumentationUrl(relativeDocName);
  }

  @Override
  public ImmutableList<String> possibleBuildFileNames() {
    return inner().possibleBuildFileNames();
  }

  @Override
  public ImmutableList<String> possibleWorkspaceFileNames() {
    return inner().possibleWorkspaceFileNames();
  }

  @Override
  public ImmutableList<String> possibleModuleFileNames() {
    return inner().possibleModuleFileNames();
  }

  /**
   * Sets a boolean value to toggle the outcome of getBlazeInfo() to be returned by {@code
   * getBuildSystem().getBuildInvoker().getBlazeInfo()}.
   *
   * <p>If not set, or set to {@code false}, the {@link BlazeInfo} will be provided by the wrapped
   * instance.
   */
  public void setThrowExceptionOnGetBlazeInfo(boolean throwExceptionOnGetBlazeInfo) {
    this.throwExceptionOnGetBlazeInfo = throwExceptionOnGetBlazeInfo;
  }

  /**
   * Sets the build binary type to be returned by {@code
   * getBuildSystem().getBuildInvoker().getType()}.
   *
   * <p>If not set, or set to {@code null}, the {@link BuildBinaryType} returned will come from the
   * wrapped instance.
   */
  public void setBuildBinaryType(BuildBinaryType type) {
    buildBinaryType = type;
  }

  /**
   * Sets the build binary type to be returned by {@code getBuildSystem().getSyncStrategy()}.
   *
   * <p>If not set, or set to {@code null}, the {@link SyncStrategy} returned will come form the
   * wrapped instance.
   */
  public void setSyncStrategy(SyncStrategy strategy) {
    syncStrategy = strategy;
  }

  class BuildInvokerWrapper implements BuildInvoker {
    private final BuildInvoker inner;

    BuildInvokerWrapper(BuildInvoker wrapped) {
      inner = wrapped;
    }

    @Override
    public BuildBinaryType getType() {
      if (buildBinaryType != null) {
        return buildBinaryType;
      }
      return inner.getType();
    }

    @Override
    public String getBinaryPath() {
      return inner.getBinaryPath();
    }

    @Override
    public boolean supportsParallelism() {
      return inner.supportsParallelism();
    }

    @Override
    public BlazeInfo getBlazeInfo() throws SyncFailedException {
      if (throwExceptionOnGetBlazeInfo) {
        throw new SyncFailedException();
      }
      return inner.getBlazeInfo();
    }

    @Override
    @MustBeClosed
    public BuildResultHelper createBuildResultHelper() {
      return inner.createBuildResultHelper();
    }

    @Override
    public BlazeCommandRunner getCommandRunner() {
      return inner.getCommandRunner();
    }

    @Override
    public BuildSystem getBuildSystem() {
      return BuildSystemProviderWrapper.this.getBuildSystem();
    }
  }

  class BuildSystemWrapper implements BuildSystem {

    private final BuildSystem inner;

    BuildSystemWrapper(BuildSystem wrapped) {
      inner = wrapped;
    }

    @Override
    public BuildSystemName getName() {
      return inner.getName();
    }

    @Override
    public BuildInvoker getBuildInvoker(Project project, BlazeContext context) {
      return new BuildInvokerWrapper(inner.getBuildInvoker(project, context));
    }

    @Override
    public BuildInvoker getBuildInvoker(
        Project project, BlazeContext context, BlazeCommandName command) {
      return new BuildInvokerWrapper(inner.getBuildInvoker(project, context));
    }

    @Override
    public Optional<BuildInvoker> getParallelBuildInvoker(Project project, BlazeContext context) {
      return inner.getParallelBuildInvoker(project, context).map(i -> new BuildInvokerWrapper(i));
    }

    @Override
    public Optional<BuildInvoker> getLocalBuildInvoker(Project project, BlazeContext context) {
      return inner.getLocalBuildInvoker(project, context).map(i -> new BuildInvokerWrapper(i));
    }

    @Override
    public SyncStrategy getSyncStrategy(Project project) {
      if (syncStrategy != null) {
        return syncStrategy;
      }
      return inner.getSyncStrategy(project);
    }

    @Override
    public void populateBlazeVersionData(
        WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo, BlazeVersionData.Builder builder) {
      inner.populateBlazeVersionData(workspaceRoot, blazeInfo, builder);
    }

    @Override
    public Optional<String> getBazelVersionString(BlazeInfo blazeInfo) {
      return inner.getBazelVersionString(blazeInfo);
    }

    @Override
    public BazelQueryRunner createQueryRunner(Project project) {
      return inner.createQueryRunner(project);
    }
  }
}
