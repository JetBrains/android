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
package com.google.idea.blaze.android.sync.importer;

import static java.util.stream.Collectors.toCollection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.android.sync.importer.aggregators.DependencyUtil;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceRetentionFilter;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceWarnings;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.IssueOutput.Category;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.common.Output;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Builds a BlazeWorkspace. */
public class BlazeAndroidWorkspaceImporter {

  // Provides a dummy TargetKey that can be used to merge all resources need to be visible to
  // .workspace module but are not a part of any resource module
  public static final TargetKey WORKSPACE_RESOURCES_TARGET_KEY =
      TargetKey.forPlainTarget(Label.create("//.workspace:resources"));
  public static final String WORKSPACE_RESOURCES_MODULE_PACKAGE = "workspace.only.resources";

  @VisibleForTesting
  static final BoolExperiment mergeResourcesEnabled =
      new BoolExperiment("blaze.merge.conflicting.resources", true);

  // b/193068824
  @VisibleForTesting
  public static final BoolExperiment includeManifestOnlyAars =
      new BoolExperiment("aswb.include.manifest.only.aars", true);

  private final Project project;
  private final Consumer<Output> context;
  private final BlazeImportInput input;
  // filter used to get all ArtifactLocation that under project view resource directories
  private final Predicate<ArtifactLocation> isOutsideProjectViewFilter;
  ImmutableSet<String> allowedGenResourcePaths;
  private final AllowlistFilter allowlistFilter;

  public BlazeAndroidWorkspaceImporter(
      Project project, BlazeContext context, BlazeImportInput input) {

    this(project, BlazeImportUtil.asConsumer(context), input);
  }

  public BlazeAndroidWorkspaceImporter(
      Project project, Consumer<Output> context, BlazeImportInput input) {
    this.context = context;
    this.input = input;
    this.project = project;
    this.isOutsideProjectViewFilter = BlazeImportUtil.isOutsideProjectViewFilter(input);
    allowedGenResourcePaths = BlazeImportUtil.getAllowedGenResourcePaths(input.projectViewSet);
    allowlistFilter =
        new AllowlistFilter(allowedGenResourcePaths, GeneratedResourceRetentionFilter.getFilter());
  }

  public BlazeAndroidImportResult importWorkspace() {
    List<TargetIdeInfo> sourceTargets = BlazeImportUtil.getSourceTargets(input);
    LibraryFactory libraries = new LibraryFactory();
    ImmutableList.Builder<AndroidResourceModule> resourceModules = new ImmutableList.Builder<>();
    ImmutableList.Builder<AndroidResourceModule> workspaceResourceModules =
        new ImmutableList.Builder<>();
    Map<TargetKey, AndroidResourceModule.Builder> targetKeyToAndroidResourceModuleBuilder =
        new HashMap<>();

    ImmutableSet<String> allowedGenResourcePaths =
        BlazeImportUtil.getAllowedGenResourcePaths(input.projectViewSet);
    for (TargetIdeInfo target : sourceTargets) {
      if (containsProjectRelevantResources(target.getAndroidIdeInfo())) {
        AndroidResourceModule.Builder androidResourceModuleBuilder =
            getOrCreateResourceModuleBuilder(
                target, libraries, targetKeyToAndroidResourceModuleBuilder);
        resourceModules.add(androidResourceModuleBuilder.build());
      } else if (dependsOnResourceDeclaringDependencies(target)) {
        // Add the target to list of potential resource modules if any of target's dependencies
        // declare resources. A target is allowed to consume resources even if it does not declare
        // any of its own
        AndroidResourceModule.Builder resourceModuleBuilder =
            getOrCreateResourceModuleBuilder(
                target, libraries, targetKeyToAndroidResourceModuleBuilder);
        workspaceResourceModules.add(resourceModuleBuilder.build());
      }
    }

    GeneratedResourceWarnings.submit(
        context::accept,
        project,
        input.projectViewSet,
        input.artifactLocationDecoder,
        allowlistFilter.testedAgainstAllowlist,
        allowedGenResourcePaths);

    ImmutableList<AndroidResourceModule> androidResourceModules =
        buildAndroidResourceModules(resourceModules.build(), workspaceResourceModules.build());

    return new BlazeAndroidImportResult(
        androidResourceModules,
        libraries.getAarLibs(),
        BlazeImportUtil.getJavacJars(input.targetMap.targets()),
        BlazeImportUtil.getResourceJars(input.targetMap.targets()));
  }

  /**
   * Creates and populates an AndroidResourceModule.Builder for the given target by recursively
   * aggregating the AndroidResourceModule.Builders of its transitive dependencies, or reuses an
   * existing builder if it's cached in resourceModuleBuilderCache.
   */
  private AndroidResourceModule.Builder getOrCreateResourceModuleBuilder(
      TargetIdeInfo target,
      LibraryFactory libraryFactory,
      Map<TargetKey, AndroidResourceModule.Builder> resourceModuleBuilderCache) {
    TargetKey targetKey = target.getKey();
    if (resourceModuleBuilderCache.containsKey(targetKey)) {
      return resourceModuleBuilderCache.get(targetKey);
    }
    AndroidResourceModule.Builder targetResourceModule =
        createResourceModuleBuilder(target, libraryFactory);
    resourceModuleBuilderCache.put(targetKey, targetResourceModule);
    for (TargetKey dep : DependencyUtil.getResourceDependencies(target)) {
      TargetIdeInfo depIdeInfo = input.targetMap.get(dep);
      reduce(
          targetKey,
          targetResourceModule,
          dep,
          depIdeInfo,
          libraryFactory,
          resourceModuleBuilderCache);
    }
    return targetResourceModule;
  }

  protected void reduce(
      TargetKey targetKey,
      AndroidResourceModule.Builder targetResourceModule,
      TargetKey depKey,
      TargetIdeInfo depIdeInfo,
      LibraryFactory libraryFactory,
      Map<TargetKey, AndroidResourceModule.Builder> resourceModuleBuilderCache) {
    if (depIdeInfo != null) {
      AndroidResourceModule.Builder depTargetResourceModule =
          getOrCreateResourceModuleBuilder(depIdeInfo, libraryFactory, resourceModuleBuilderCache);
      targetResourceModule.addTransitiveResources(depTargetResourceModule.getTransitiveResources());
      targetResourceModule.addResourceLibraryKeys(depTargetResourceModule.getResourceLibraryKeys());
      targetResourceModule.addTransitiveResourceDependencies(
          depTargetResourceModule.getTransitiveResourceDependencies().stream()
              .filter(key -> !targetKey.equals(key))
              .collect(Collectors.toList()));
      if (containsProjectRelevantResources(depIdeInfo.getAndroidIdeInfo())
          && !depKey.equals(targetKey)) {
        targetResourceModule.addTransitiveResourceDependency(depKey);
      }
    }
  }

  private boolean containsProjectRelevantResources(@Nullable AndroidIdeInfo androidIdeInfo) {
    if (androidIdeInfo == null) {
      return false;
    }
    return androidIdeInfo.generateResourceClass()
        && containsSourcesOrAllowedGeneratedResources(androidIdeInfo, allowlistFilter);
  }

  /** Returns true if any direct dependency of `androidIdeInfo` declares resources. */
  private boolean dependsOnResourceDeclaringDependencies(TargetIdeInfo androidIdeInfo) {
    List<TargetKey> dependencies = DependencyUtil.getResourceDependencies(androidIdeInfo);
    return dependencies.stream()
        .map(input.targetMap::get)
        .filter(Objects::nonNull)
        .anyMatch(d -> containsProjectRelevantResources(d.getAndroidIdeInfo()));
  }

  /**
   * Helper function to create an AndroidResourceModule.Builder with initial resource information.
   * The builder is incomplete since it doesn't contain information about dependencies. {@link
   * #getOrCreateResourceModuleBuilder} will aggregate AndroidResourceModule.Builder over its
   * transitive dependencies.
   */
  protected AndroidResourceModule.Builder createResourceModuleBuilder(
      TargetIdeInfo target, LibraryFactory libraryFactory) {
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    TargetKey targetKey = target.getKey();
    AndroidResourceModule.Builder androidResourceModule =
        new AndroidResourceModule.Builder(targetKey);
    if (androidIdeInfo == null) {
      String libraryKey = libraryFactory.createAarLibrary(target);
      if (libraryKey != null) {
        ArtifactLocation artifactLocation = target.getAndroidAarIdeInfo().getAar();
        if (isSourceOrAllowedGenPath(artifactLocation, allowlistFilter)) {
          androidResourceModule.addResourceLibraryKey(libraryKey);
        }
      }
      return androidResourceModule;
    }

    for (AndroidResFolder androidResFolder : androidIdeInfo.getResources()) {
      if (!includeManifestOnlyAars.getValue()
          && androidResFolder.getRoot().getRelativePath().isEmpty()) {
        continue;
      }

      ArtifactLocation artifactLocation = androidResFolder.getRoot();
      if (isSourceOrAllowedGenPath(artifactLocation, allowlistFilter)) {
        if (isOutsideProjectViewFilter.test(artifactLocation)) {
          // we are creating aar libraries, and this resource isn't inside the project view
          // so we can skip adding it to the module
          String libraryKey =
              libraryFactory.createAarLibrary(
                  androidResFolder.getAar(),
                  BlazeImportUtil.javaResourcePackageFor(target, /* inferPackage = */ true));
          if (libraryKey != null) {
            androidResourceModule.addResourceLibraryKey(libraryKey);
          }
        } else if (!artifactLocation.getRelativePath().isEmpty()) {
          if (containsProjectRelevantResources(androidIdeInfo)) {
            androidResourceModule.addResource(artifactLocation);
          }
          androidResourceModule.addTransitiveResource(artifactLocation);
        }
      }
    }
    return androidResourceModule;
  }

  public static boolean containsSourcesOrAllowedGeneratedResources(
      AndroidIdeInfo androidIdeInfo, Predicate<ArtifactLocation> allowlistTester) {
    return androidIdeInfo.getResources().stream()
        .map(resource -> resource.getRoot())
        .anyMatch(location -> isSourceOrAllowedGenPath(location, allowlistTester));
  }

  public static boolean isSourceOrAllowedGenPath(
      ArtifactLocation artifactLocation, Predicate<ArtifactLocation> allowlistTest) {
    return artifactLocation.isSource() || allowlistTest.test(artifactLocation);
  }

  private ImmutableList<AndroidResourceModule> buildAndroidResourceModules(
      ImmutableList<AndroidResourceModule> inputModules,
      ImmutableList<AndroidResourceModule> workspaceResourceModules) {
    // Filter empty resource modules
    List<AndroidResourceModule> androidResourceModules =
        inputModules.stream()
            .filter(
                androidResourceModule ->
                    !(androidResourceModule.resources.isEmpty()
                        && androidResourceModule.resourceLibraryKeys.isEmpty()))
            .collect(Collectors.toList());

    // Detect, filter, and warn about multiple R classes
    Multimap<String, AndroidResourceModule> javaPackageToResourceModule =
        ArrayListMultimap.create();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      TargetIdeInfo target = input.targetMap.get(androidResourceModule.targetKey);
      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      assert androidIdeInfo != null;
      javaPackageToResourceModule.put(
          androidIdeInfo.getResourceJavaPackage(), androidResourceModule);
    }

    List<AndroidResourceModule> result = Lists.newArrayList();
    for (String resourceJavaPackage : javaPackageToResourceModule.keySet()) {
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage =
          javaPackageToResourceModule.get(resourceJavaPackage);

      if (androidResourceModulesWithJavaPackage.size() == 1) {
        result.addAll(androidResourceModulesWithJavaPackage);
      } else {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder
            .append("Multiple R classes generated with the same java package ")
            .append(resourceJavaPackage)
            .append(".R: ");
        messageBuilder.append('\n');
        for (AndroidResourceModule androidResourceModule : androidResourceModulesWithJavaPackage) {
          messageBuilder.append("  ").append(androidResourceModule.targetKey).append('\n');
        }

        if (mergeResourcesEnabled.getValue()) {
          messageBuilder.append("  ").append("Merging Resources...").append("\n");
          String message = messageBuilder.toString();
          context.accept(IssueOutput.issue(Category.INFORMATION, message).build());

          result.add(mergeAndroidResourceModules(androidResourceModulesWithJavaPackage));
        } else {
          String message = messageBuilder.toString();
          context.accept(new PerformanceWarning(message));
          context.accept(IssueOutput.warn(message).build());

          result.add(selectBestAndroidResourceModule(androidResourceModulesWithJavaPackage));
        }
      }
    }
    if (!workspaceResourceModules.isEmpty()) {
      // Create and add one module for all resources that are visible to .workspace module, but do
      // not have an attached resource module of their own.

      // We lump all such targets into one module because chances are none of these targets have an
      // associated manifest. {@link BlazeAndroidProjectStructureSyncer} expects all resource
      // modules to have an associated target with a manifest, which is a behavior we want to keep.
      // It makes an exception for this module and attaches the workspace resource module without a
      // manifest.
      result.add(createWorkspaceResourceModule(result, workspaceResourceModules));
    }

    Collections.sort(result, Comparator.comparing(m -> m.targetKey));
    return ImmutableList.copyOf(result);
  }

  private static AndroidResourceModule selectBestAndroidResourceModule(
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage) {
    return androidResourceModulesWithJavaPackage.stream()
        .max(
            (lhs, rhs) ->
                ComparisonChain.start()
                    .compare(lhs.resources.size(), rhs.resources.size()) // Most resources wins
                    .compare(
                        lhs.transitiveResources.size(),
                        rhs.transitiveResources.size()) // Most transitive resources wins
                    .compare(
                        lhs.resourceLibraryKeys.size(),
                        rhs.resourceLibraryKeys.size()) // Most transitive resources wins
                    .compare(
                        rhs.targetKey.toString().length(),
                        lhs.targetKey
                            .toString()
                            .length()) // Shortest label wins - note lhs, rhs are flipped
                    .result())
        .get();
  }

  /**
   * Creates a {@link AndroidResourceModule} that contains all dependencies of
   * `workspaceResourceModules` that are not present in `existingModules` without attaching any
   * sources. This module is meant to link resources used by .workspace module but not by any other
   * resource modules.
   */
  private static AndroidResourceModule createWorkspaceResourceModule(
      List<AndroidResourceModule> existingModules,
      ImmutableList<AndroidResourceModule> workspaceResourceModules) {

    Set<ArtifactLocation> newTransitiveResources =
        workspaceResourceModules.stream()
            .flatMap(m -> m.transitiveResources.stream())
            .collect(Collectors.toSet());

    Set<String> newLibraryKeys =
        workspaceResourceModules.stream()
            .flatMap(m -> m.resourceLibraryKeys.stream())
            .collect(toCollection(HashSet::new));

    Set<TargetKey> newResourceDeps =
        workspaceResourceModules.stream()
            .flatMap(m -> m.transitiveResourceDependencies.stream())
            .collect(toCollection(HashSet::new));

    existingModules.forEach(
        m -> {
          newTransitiveResources.removeAll(m.transitiveResources);
          newLibraryKeys.removeAll(m.resourceLibraryKeys);
          newResourceDeps.removeAll(m.transitiveResourceDependencies);
        });

    // We add library keys and resource dependencies only. This module should not contain any
    // resource sources.
    return AndroidResourceModule.builder(WORKSPACE_RESOURCES_TARGET_KEY)
        .addTransitiveResources(newTransitiveResources)
        .addResourceLibraryKeys(newLibraryKeys)
        .addTransitiveResourceDependencies(newResourceDeps)
        .build();
  }

  @VisibleForTesting
  public static AndroidResourceModule mergeAndroidResourceModules(
      Collection<AndroidResourceModule> modules) {
    // Choose the shortest label as the canonical label (arbitrarily chosen from the original
    // filtering logic)
    TargetKey targetKey =
        modules.stream()
            .map(m -> m.targetKey)
            .min(Comparator.comparingInt(tk -> tk.toString().length()))
            .get();

    AndroidResourceModule.Builder moduleBuilder = AndroidResourceModule.builder(targetKey);
    modules.forEach(
        m ->
            moduleBuilder
                .addSourceTarget(m.targetKey)
                .addResources(m.resources)
                .addTransitiveResources(m.transitiveResources)
                .addResourceLibraryKeys(m.resourceLibraryKeys)
                .addTransitiveResourceDependencies(m.transitiveResourceDependencies));
    return moduleBuilder.build();
  }

  static class LibraryFactory {
    private Map<String, AarLibrary> aarLibraries = new HashMap<>();

    public ImmutableMap<String, AarLibrary> getAarLibs() {
      return ImmutableMap.copyOf(aarLibraries);
    }

    /**
     * Creates a new Aar repository for this target, if possible, or locates an existing one if one
     * already existed for this location. Returns the key for the library or null if no aar exists
     * for this target.
     */
    @Nullable
    private String createAarLibrary(@NotNull TargetIdeInfo target) {
      // NOTE: we are not doing jdeps optimization, even though we have the jdeps data for the AAR's
      // jar. The aar might still have resources that are used (e.g., @string/foo in .xml), and we
      // don't have the equivalent of jdeps data.
      if (target.getAndroidAarIdeInfo() == null
          || target.getJavaIdeInfo() == null
          || target.getJavaIdeInfo().getJars().isEmpty()) {
        return null;
      }

      String resourcePackage =
          BlazeImportUtil.javaResourcePackageFor(target, /* inferPackage = */ true);

      String libraryKey =
          LibraryKey.libraryNameFromArtifactLocation(target.getAndroidAarIdeInfo().getAar());
      if (!aarLibraries.containsKey(libraryKey)) {
        // aar_import should only have one jar (a merged jar from the AAR's jars).
        LibraryArtifact firstJar = target.getJavaIdeInfo().getJars().iterator().next();
        aarLibraries.put(
            libraryKey,
            new AarLibrary(firstJar, target.getAndroidAarIdeInfo().getAar(), resourcePackage));
      }
      return libraryKey;
    }

    /**
     * Creates a new Aar library for this ArtifactLocation. Returns the key for the library or null
     * if no aar exists for this target. Note that this function is designed for aar created by
     * aspect which does not contains class jar. Mistakenly using this function for normal aar
     * imported by user will fail to cache jar file in this Aar.
     */
    @Nullable
    private String createAarLibrary(
        @Nullable ArtifactLocation aar, @Nullable String resourcePackage) {
      if (aar == null) {
        return null;
      }
      String libraryKey = LibraryKey.libraryNameFromArtifactLocation(aar);
      if (!aarLibraries.containsKey(libraryKey)) {
        // aar_import should only have one jar (a merged jar from the AAR's jars).
        aarLibraries.put(libraryKey, new AarLibrary(aar, resourcePackage));
      }
      return libraryKey;
    }
  }
}
