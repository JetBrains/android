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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedOperation;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class BlazeConfigurationResolver {
  private static final Logger logger = Logger.getInstance(BlazeConfigurationResolver.class);

  private final Project project;

  BlazeConfigurationResolver(Project project) {
    this.project = project;
  }

  public BlazeConfigurationResolverResult update(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      BlazeConfigurationResolverResult oldResult) {
    ExecutionRootPathResolver executionRootPathResolver =
        new ExecutionRootPathResolver(
            Blaze.getBuildSystemProvider(project),
            WorkspaceRoot.fromProject(project),
            blazeProjectData.getBlazeInfo().getExecutionRoot(),
            blazeProjectData.getWorkspacePathResolver());
    ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap =
        BlazeConfigurationToolchainResolver.buildToolchainLookupMap(
            context, blazeProjectData.getTargetMap());
    ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings =
        BlazeConfigurationToolchainResolver.buildCompilerSettingsMap(
            context,
            project,
            toolchainLookupMap,
            executionRootPathResolver,
            oldResult.getCompilerSettings());
    ProjectViewTargetImportFilter projectViewFilter =
        new ProjectViewTargetImportFilter(
            Blaze.getBuildSystemName(project), workspaceRoot, projectViewSet);
    Predicate<TargetIdeInfo> targetFilter = getTargetFilter(projectViewFilter);
    BlazeConfigurationResolverResult.Builder builder = BlazeConfigurationResolverResult.builder();
    buildBlazeConfigurationData(
        context, blazeProjectData, toolchainLookupMap, compilerSettings, targetFilter, builder);
    builder.setCompilerSettings(compilerSettings);
    ImmutableSet<File> validHeaderRoots =
        HeaderRootTrimmer.getValidRoots(
            context, blazeProjectData, toolchainLookupMap, targetFilter, executionRootPathResolver);
    builder.setValidHeaderRoots(validHeaderRoots);
    return builder.build();
  }

  private static Predicate<TargetIdeInfo> getTargetFilter(
      ProjectViewTargetImportFilter projectViewFilter) {
    return target ->
        target.getcIdeInfo() != null
            && projectViewFilter.isSourceTarget(target)
            && containsCompiledSources(target);
  }

  private static boolean containsCompiledSources(TargetIdeInfo target) {
    Predicate<ArtifactLocation> isCompiled =
        location -> {
          String locationExtension = FileUtilRt.getExtension(location.getRelativePath());
          return CFileExtensions.SOURCE_EXTENSIONS.contains(locationExtension);
        };
    return target.getcIdeInfo() != null
        && target.getcIdeInfo().getSources().stream()
            .filter(ArtifactLocation::isSource)
            .anyMatch(isCompiled);
  }

  private void buildBlazeConfigurationData(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings,
      Predicate<TargetIdeInfo> targetFilter,
      BlazeConfigurationResolverResult.Builder builder) {
    // Type specification needed to avoid incorrect type inference during command line build.
    Scope.push(
        parentContext,
        (ScopedOperation)
            context -> {
              context.push(new TimingScope("Build C configuration map", EventType.Other));

              ConcurrentMap<TargetKey, BlazeResolveConfigurationData> targetToData =
                  Maps.newConcurrentMap();
              List<ListenableFuture<?>> targetToDataFutures =
                  blazeProjectData.getTargetMap().targets().stream()
                      .filter(targetFilter)
                      .map(
                          target ->
                              submit(
                                  () -> {
                                    BlazeResolveConfigurationData data =
                                        createResolveConfiguration(
                                            target, toolchainLookupMap, compilerSettings);
                                    if (data != null) {
                                      targetToData.put(target.getKey(), data);
                                    }
                                    return null;
                                  }))
                      .collect(Collectors.toList());
              try {
                Futures.allAsList(targetToDataFutures).get();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                context.setCancelled();
                return;
              } catch (ExecutionException e) {
                IssueOutput.error("Could not build C resolve configurations: " + e).submit(context);
                logger.error("Could not build C resolve configurations", e);
                return;
              }
              findEquivalenceClasses(context, project, blazeProjectData, targetToData, builder);
            });
  }

  private static void findEquivalenceClasses(
      BlazeContext context,
      Project project,
      BlazeProjectData blazeProjectData,
      Map<TargetKey, BlazeResolveConfigurationData> targetToData,
      BlazeConfigurationResolverResult.Builder builder) {
    Multimap<BlazeResolveConfigurationData, TargetKey> dataEquivalenceClasses =
        ArrayListMultimap.create();
    for (Map.Entry<TargetKey, BlazeResolveConfigurationData> entry : targetToData.entrySet()) {
      TargetKey target = entry.getKey();
      BlazeResolveConfigurationData data = entry.getValue();
      dataEquivalenceClasses.put(data, target);
    }

    ImmutableMap.Builder<BlazeResolveConfigurationData, BlazeResolveConfiguration>
        dataToConfiguration = ImmutableMap.builder();
    for (Map.Entry<BlazeResolveConfigurationData, Collection<TargetKey>> entry :
        dataEquivalenceClasses.asMap().entrySet()) {
      BlazeResolveConfigurationData data = entry.getKey();
      Collection<TargetKey> targets = entry.getValue();
      dataToConfiguration.put(
          data,
          BlazeResolveConfiguration.createForTargets(project, blazeProjectData, data, targets));
    }
    context.output(
        PrintOutput.log(
            String.format(
                "%s unique C configurations, %s C targets",
                dataEquivalenceClasses.keySet().size(), dataEquivalenceClasses.size())));
    builder.setUniqueConfigurations(dataToConfiguration.build());
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }

  @Nullable
  private BlazeResolveConfigurationData createResolveConfiguration(
      TargetIdeInfo target,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettingsMap) {
    TargetKey targetKey = target.getKey();
    CIdeInfo cIdeInfo = target.getcIdeInfo();
    if (cIdeInfo == null) {
      return null;
    }
    CToolchainIdeInfo toolchainIdeInfo = toolchainLookupMap.get(targetKey);
    if (toolchainIdeInfo == null) {
      return null;
    }
    BlazeCompilerSettings compilerSettings = compilerSettingsMap.get(toolchainIdeInfo);
    if (compilerSettings == null) {
      return null;
    }
    return BlazeResolveConfigurationData.create(cIdeInfo, toolchainIdeInfo, compilerSettings);
  }
}
