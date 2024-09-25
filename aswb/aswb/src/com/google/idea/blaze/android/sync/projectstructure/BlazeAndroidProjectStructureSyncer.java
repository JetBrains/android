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
package com.google.idea.blaze.android.sync.projectstructure;

import static com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter.WORKSPACE_RESOURCES_TARGET_KEY;
import static java.util.stream.Collectors.toSet;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.manifest.ParsedManifestService;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.importer.BlazeImportInput;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Updates the IDE's project structure. */
public class BlazeAndroidProjectStructureSyncer {
  private static final ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(
          AppExecutorUtil.createBoundedApplicationPoolExecutor("ManifestParser", 8));
  private static final Logger log = Logger.getInstance(BlazeAndroidProjectStructureSyncer.class);

  private static final BoolExperiment attachAarForResourceModule =
      new BoolExperiment("blaze.attach.aar.resource.module.enable", false);
  private static final BoolExperiment asyncFetchApplicationId =
      new BoolExperiment("aswb.sync.async.appid", true);

  /**
   * Updates the IntelliJ project structure to have a module per android_library with resources that
   * is present in the source view.
   */
  public static void updateProjectStructure(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData projectDataFromPreviousSync,
      BlazeSyncPlugin.ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel,
      boolean isAndroidWorkspace) {
    if (!isAndroidWorkspace) {
      AndroidFacetModuleCustomizer.removeAndroidFacet(workspaceModule);
      // Workspace type should always be ANDROID as long as the blaze android plugin is present.
      log.warn(
          "No android workspace found for project \""
              + project.getName()
              + "\". Removing AndroidFacet from workspace module.");
      return;
    }
    AndroidFacetModuleCustomizer.createAndroidFacet(workspaceModule, false);

    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      if (projectDataFromPreviousSync != null) {
        // If the prior sync had android sync data, but the current one doesn't, then something
        // really bad happened. Nothing's gonna work until this is fixed.
        context.output(
            PrintOutput.error(
                "The IDE was not able to retrieve the necessary information from Blaze. Many"
                    + " android specific features may not work. Please try [Blaze > Sync > Sync"
                    + " project with BUILD files] again."));
      }
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }

    // We need to create android resource modules for all targets excluding those that end up
    // getting associated with the workspace module.
    List<AndroidResourceModule> nonWorkspaceResourceModules =
        syncData.importResult.androidResourceModules.stream()
            .filter(m -> !WORKSPACE_RESOURCES_TARGET_KEY.equals(m.targetKey))
            .collect(Collectors.toList());

    int totalOrderEntries = 0;
    Set<File> existingRoots = Sets.newHashSet();
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);

    for (AndroidResourceModule androidResourceModule : nonWorkspaceResourceModules) {
      String moduleName = moduleNameForAndroidModule(androidResourceModule.targetKey);
      Module module = moduleEditor.createModule(moduleName, StdModuleTypes.JAVA);

      TargetIdeInfo target = blazeProjectData.getTargetMap().get(androidResourceModule.targetKey);
      Verify.verifyNotNull(target);
      boolean isApp =
          target.kindIsOneOf(RuleTypes.ANDROID_BINARY.getKind(), RuleTypes.ANDROID_TEST.getKind());
      AndroidFacetModuleCustomizer.createAndroidFacet(module, isApp);

      AndroidIdeInfo androidIdeInfo = Verify.verifyNotNull(target.getAndroidIdeInfo());
      ArrayList<File> newRoots =
          new ArrayList<>(
              OutputArtifactResolver.resolveAll(
                  project, artifactLocationDecoder, androidResourceModule.resources));

      File moduleDirectory =
          moduleDirectoryForAndroidTarget(WorkspaceRoot.fromProject(project), target);
      File manifest =
          manifestFileForAndroidTarget(
              project, artifactLocationDecoder, androidIdeInfo, moduleDirectory);
      if (manifest != null) {
        newRoots.add(manifest);
      }

      // Multiple libraries may end up pointing to files from the same res folder. If we've already
      // added something as a root, then we skip registering it as a root in another res module.
      // This works since our dependency graph is cyclic via the workspace module.
      newRoots.removeAll(existingRoots);
      existingRoots.addAll(newRoots);

      ModifiableRootModel modifiableRootModel = moduleEditor.editModule(module);
      ResourceModuleContentRootCustomizer.setupContentRoots(modifiableRootModel, newRoots);
      modifiableRootModel.addModuleOrderEntry(workspaceModule);
      ++totalOrderEntries;

      // Add a dependency from the workspace to the resource module
      ModuleOrderEntry orderEntry = workspaceModifiableModel.addModuleOrderEntry(module);
      orderEntry.setExported(true);
      ++totalOrderEntries;

      // The workspace module depends on all libraries (including aars). All resource modules
      // depend on the workspace module, and hence transitively depend on all the libraries. As a
      // result, there is no need to explicitly attach libraries to each resource module. Doing
      // so only increases the number of order entries, which exacerbates issues like b/187413558
      // where the Kotlin plugin does calculations proportional to the # of order entries.
      if (attachAarForResourceModule.getValue()) {
        for (String libraryName : androidResourceModule.resourceLibraryKeys) {
          Library lib = libraryTable.getLibraryByName(libraryName);
          if (lib == null) {
            String message =
                String.format(
                    "Could not find library '%s' for module '%s'. Re-syncing might fix this issue.",
                    libraryName, moduleName);
            log.warn(message);
            context.output(PrintOutput.log(message));
          } else {
            modifiableRootModel.addLibraryEntry(lib);
          }
        }
      }
    }

    int allowedGenResources = projectViewSet.listItems(GeneratedAndroidResourcesSection.KEY).size();
    context.output(
        PrintOutput.log(
            String.format(
                "Android resource module count: %d, order entries: %d, generated resources: %d",
                syncData.importResult.androidResourceModules.size(),
                totalOrderEntries,
                allowedGenResources)));
  }

  public static String moduleNameForAndroidModule(TargetKey targetKey) {
    return targetKey
        .toString()
        .substring(2) // Skip initial "//"
        .replace('/', '.')
        .replace(':', '.');
  }

  public static void updateInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      boolean isAndroidWorkspace) {
    BlazeLightResourceClassService.Builder rClassBuilder =
        new BlazeLightResourceClassService.Builder(project);
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    registry.clear();
    if (isAndroidWorkspace) {
      updateInMemoryState(
          project,
          context,
          workspaceRoot,
          projectViewSet,
          blazeProjectData,
          workspaceModule,
          registry,
          rClassBuilder);
    }
    BlazeLightResourceClassService.getInstance(project).installRClasses(rClassBuilder);
  }

  private static void updateInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      AndroidResourceModuleRegistry registry,
      BlazeLightResourceClassService.Builder rClassBuilder) {
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }
    updateWorkspaceModuleFacetInMemoryState(
        project,
        context,
        workspaceRoot,
        workspaceModule,
        androidSdkPlatform
    );

    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    ModuleFinder moduleFinder = ModuleFinder.getInstance(project);

    BlazeImportInput input =
        BlazeImportInput.forProject(project, workspaceRoot, projectViewSet, blazeProjectData);

    // Get package names from all visible targets.
    Set<String> sourcePackages =
        BlazeImportUtil.getSourceTargetsStream(input)
            .filter(targetIdeInfo -> targetIdeInfo.getAndroidIdeInfo() != null)
            .map(targetIdeInfo -> BlazeImportUtil.javaResourcePackageFor(targetIdeInfo, true))
            .collect(toSet());

    for (AndroidResourceModule androidResourceModule :
        syncData.importResult.androidResourceModules) {
      File manifestFile = null;
      String modulePackage;
      File moduleDirectory;
      Module module;
      if (WORKSPACE_RESOURCES_TARGET_KEY.equals(androidResourceModule.targetKey)) {
        // Until ~Jan 2021, we used to create a separate module (.workspace.resources) that included
        // resources that were used by project, but not included in any other resource module.
        // Starting with cl/350385526, these resources are attached to the workspace module itself
        // and we don't create a separate module for workspace resources.
        modulePackage = BlazeAndroidWorkspaceImporter.WORKSPACE_RESOURCES_MODULE_PACKAGE;
        moduleDirectory = workspaceRoot.directory();
        // b/177279296 revealed an issue when we removed the workspace module. To work around this
        // we still use the workspace.resources module as long as such a module still exists in the
        // project. Hopefully, in a couple of months, there won't be any projects around with the
        // resources module, and we can delete this code.
        module =
            moduleFinder.findModuleByName(
                moduleNameForAndroidModule(androidResourceModule.targetKey));
        if (module == null) {
          module = workspaceModule;
        } else {
          // b/177279296: log a warning so we check our metrics for whether there are still users
          // who have workspace resource modules.
          log.warn("Still using a separate module for workspace resources.");
        }
      } else {
        TargetIdeInfo target =
            Preconditions.checkNotNull(
                blazeProjectData.getTargetMap().get(androidResourceModule.targetKey));
        AndroidIdeInfo androidIdeInfo = Preconditions.checkNotNull(target.getAndroidIdeInfo());
        modulePackage = BlazeImportUtil.javaResourcePackageFor(target, true);
        moduleDirectory = moduleDirectoryForAndroidTarget(workspaceRoot, target);
        manifestFile =
            manifestFileForAndroidTarget(
                project, artifactLocationDecoder, androidIdeInfo, moduleDirectory);
        String moduleName = moduleNameForAndroidModule(androidResourceModule.targetKey);
        module = moduleFinder.findModuleByName(moduleName);
        if (module == null) {
          log.warn("No module found for resource target: " + androidResourceModule.targetKey);
          continue;
        }
        registry.put(module, androidResourceModule);
      }

      List<File> resources =
          OutputArtifactResolver.resolveAll(
              project, artifactLocationDecoder, androidResourceModule.resources);
      updateModuleFacetInMemoryState(
          project,
          context,
          androidSdkPlatform,
          module,
          moduleDirectory,
          manifestFile,
          modulePackage,
          resources
      );
      rClassBuilder.addRClass(modulePackage, module);
      sourcePackages.remove(modulePackage);
    }

    rClassBuilder.addWorkspacePackages(sourcePackages);
  }

  private static File moduleDirectoryForAndroidTarget(
      WorkspaceRoot workspaceRoot, TargetIdeInfo target) {
    return workspaceRoot.fileForPath(target.getKey().getLabel().blazePackage());
  }

  @Nullable
  private static File manifestFileForAndroidTarget(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      AndroidIdeInfo androidIdeInfo,
      File moduleDirectory) {
    ArtifactLocation manifestArtifactLocation = androidIdeInfo.getManifest();
    return manifestArtifactLocation != null
        ? OutputArtifactResolver.resolve(project, artifactLocationDecoder, manifestArtifactLocation)
        : new File(moduleDirectory, "AndroidManifest.xml");
  }

  /**
   * Parses the provided manifest to calculate applicationId. Returns the provided default if the
   * manifest file does not exist, or is invalid. This method is potentially called concurrently
   * from background threads so it must be thread safe.
   */
  static String getApplicationIdFromManifestOrDefault(
      Project project,
      @Nullable BlazeContext context,
      @Nullable File manifestFile,
      String defaultId) {
    if (project.isDisposed()) {
      return defaultId;
    }

    if (manifestFile == null) {
      return defaultId;
    }

    try {
      ManifestParser.ParsedManifest parsedManifest =
          ParsedManifestService.getInstance(project).getParsedManifest(manifestFile);
      if (parsedManifest == null) {
        String message = "Could not parse malformed manifest file: " + manifestFile;
        log.warn(message);
        if (context != null) {
          context.output(PrintOutput.log(message));
        }
        return defaultId;
      }
      if (parsedManifest.packageName != null) {
        return parsedManifest.packageName;
      }
    } catch (FileNotFoundException e) {
      log.warn("Existing sync data points to `" + manifestFile + "` which is not present anymore.");
    } catch (IOException e) {
      String message = "Exception while reading manifest file: " + manifestFile;
      log.warn(message, e);
      if (context != null) {
        context.output(PrintOutput.log(message));
      }
    }
    return defaultId;
  }

  /** Updates the shared workspace module with android info. */
  private static void updateWorkspaceModuleFacetInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      Module workspaceModule,
      AndroidSdkPlatform androidSdkPlatform) {
    File moduleDirectory = workspaceRoot.directory();
    String resourceJavaPackage = ":workspace";
    updateModuleFacetInMemoryState(
        project,
        context,
        androidSdkPlatform,
        workspaceModule,
        moduleDirectory,
        null,
        resourceJavaPackage,
        ImmutableList.of()
    );
  }

  private static void updateModuleFacetInMemoryState(
      Project project,
      @Nullable BlazeContext context,
      AndroidSdkPlatform androidSdkPlatform,
      Module module,
      File moduleDirectory,
      @Nullable File manifestFile,
      String resourceJavaPackage,
      Collection<File> resources) {
    String name = module.getName();
    File manifest = manifestFile != null ? manifestFile : new File("MissingManifest.xml");
    NamedIdeaSourceProvider sourceProvider =
        NamedIdeaSourceProviderBuilder.create(name, VfsUtilCore.fileToUrl(manifest))
            .withScopeType(ScopeType.MAIN)
            .withResDirectoryUrls(
                ContainerUtil.map(resources, it -> VfsUtilCore.fileToUrl(it.getAbsoluteFile())))
            .build();

    ListenableFuture<String> applicationId =
        asyncFetchApplicationId.getValue()
            ? EXECUTOR.submit(
                () ->
                    getApplicationIdFromManifestOrDefault(
                        project, context, manifestFile, resourceJavaPackage))
            : Futures.immediateFuture(
                getApplicationIdFromManifestOrDefault(
                    project, context, manifestFile, resourceJavaPackage));

    BlazeAndroidModel androidModel =
        new BlazeAndroidModel(
            project,
            moduleDirectory,
            sourceProvider,
            applicationId,
            androidSdkPlatform.androidMinSdkLevel);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      updateAndroidFacetWithSourceAndModel(facet, sourceProvider, androidModel);
    }
  }

  private static void updateAndroidFacetWithSourceAndModel(
      AndroidFacet facet, NamedIdeaSourceProvider sourceProvider, BlazeAndroidModel androidModel) {
    AndroidModel.set(facet, androidModel);
  }
}
