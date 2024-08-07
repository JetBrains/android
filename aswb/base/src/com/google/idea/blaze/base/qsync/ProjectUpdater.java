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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.artifacts.ProjectArtifactStore;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.util.UrlUtil;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectListener;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import com.intellij.openapi.vfs.VfsUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

/** An object that monitors the build graph and applies the changes to the project structure. */
public class ProjectUpdater implements QuerySyncProjectListener {

  /** Entry point for instantiating {@link ProjectUpdater}. */
  public static class Provider implements QuerySyncProjectListenerProvider {
    @Override
    public QuerySyncProjectListener createListener(QuerySyncProject querySyncProject) {
      return new ProjectUpdater(
          querySyncProject.getIdeProject(),
          querySyncProject.getImportSettings(),
          querySyncProject.getProjectViewSet(),
          querySyncProject.getWorkspaceRoot(),
          querySyncProject.getProjectPathResolver(),
          querySyncProject.getArtifactStore());
    }
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final ProjectViewSet projectViewSet;
  private final WorkspaceRoot workspaceRoot;
  private final ProjectPath.Resolver projectPathResolver;
  private final ProjectArtifactStore artifactStore;

  public ProjectUpdater(
      Project project,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      WorkspaceRoot workspaceRoot,
      ProjectPath.Resolver projectPathResolver,
      ProjectArtifactStore artifactStore) {
    this.project = project;
    this.importSettings = importSettings;
    this.projectViewSet = projectViewSet;
    this.workspaceRoot = workspaceRoot;
    this.projectPathResolver = projectPathResolver;
    this.artifactStore = artifactStore;
  }

  public static ModuleType<?> mapModuleType(ProjectProto.ModuleType type) {
    switch (type) {
      case MODULE_TYPE_DEFAULT:
        return ModuleTypeManager.getInstance().getDefaultModuleType();
      case UNRECOGNIZED:
        break;
    }
    throw new IllegalStateException("Unrecognised module type " + type);
  }

  @Override
  public void onNewProjectSnapshot(Context<?> context, QuerySyncProjectSnapshot graph)
      throws BuildException {
    artifactStore.update(context, graph);
    updateProjectModel(graph.project(), context);
  }

  private void updateProjectModel(ProjectProto.Project spec, Context<?> context) {
    File imlDirectory = new File(BlazeDataStorage.getProjectDataDir(importSettings), "modules");
    Transactions.submitWriteActionTransactionAndWait(
        () -> {
          IdeModifiableModelsProvider models =
              ProjectDataManager.getInstance().createModifiableModelsProvider(project);

          for (BlazeQuerySyncPlugin syncPlugin : BlazeQuerySyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateProjectSettingsForQuerySync(project, context, projectViewSet);
          }
          int removedLibCount = removeUnusedLibraries(models, spec.getLibraryList());
          if (removedLibCount > 0) {
            context.output(PrintOutput.output("Removed " + removedLibCount + " libs"));
          }
          ImmutableMap.Builder<String, Library> libMapBuilder = ImmutableMap.builder();
          for (ProjectProto.Library libSpec : spec.getLibraryList()) {
            Library library = getOrCreateLibrary(models, libSpec);
            libMapBuilder.put(libSpec.getName(), library);
          }
          ImmutableMap<String, Library> libMap = libMapBuilder.buildOrThrow();

          for (ProjectProto.Module moduleSpec : spec.getModulesList()) {
            Module module =
                models.newModule(
                    imlDirectory.toPath().resolve(moduleSpec.getName() + ".iml").toString(),
                    mapModuleType(moduleSpec.getType()).getId());

            ModifiableRootModel roots = models.getModifiableRootModel(module);
            ImmutableList<OrderEntry> existingLibraryOrderEntries =
                stream(roots.getOrderEntries())
                    .filter(it -> it instanceof LibraryOrderEntry)
                    .collect(toImmutableList());
            for (OrderEntry entry : existingLibraryOrderEntries) {
              roots.removeOrderEntry(entry);
            }
            // TODO: should this be encapsulated in ProjectProto.Module?
            roots.inheritSdk();

            // TODO instead of removing all content entries and re-adding, we should calculate the
            //  diff.
            for (ContentEntry entry : roots.getContentEntries()) {
              roots.removeContentEntry(entry);
            }
            for (ProjectProto.ContentEntry ceSpec : moduleSpec.getContentEntriesList()) {
              ProjectPath projectPath = ProjectPath.create(ceSpec.getRoot());

              ContentEntry contentEntry =
                  roots.addContentEntry(
                      UrlUtil.pathToUrl(projectPathResolver.resolve(projectPath).toString()));
              for (ProjectProto.SourceFolder sfSpec : ceSpec.getSourcesList()) {
                ProjectPath sourceFolderProjectPath = ProjectPath.create(sfSpec.getProjectPath());

                JavaSourceRootProperties properties =
                    JpsJavaExtensionService.getInstance()
                        .createSourceRootProperties(
                            sfSpec.getPackagePrefix(), sfSpec.getIsGenerated());
                JavaSourceRootType rootType =
                    sfSpec.getIsTest() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
                String url =
                    UrlUtil.pathToUrl(
                        projectPathResolver.resolve(sourceFolderProjectPath).toString(),
                        sourceFolderProjectPath.innerJarPath());
                SourceFolder unused = contentEntry.addSourceFolder(url, rootType, properties);
              }
              for (String exclude : ceSpec.getExcludesList()) {
                contentEntry.addExcludeFolder(
                    UrlUtil.pathToIdeaDirectoryUrl(workspaceRoot.absolutePathFor(exclude)));
              }
            }

            for (String lib : moduleSpec.getLibraryNameList()) {
              Library library = libMap.get(lib);
              if (library == null) {
                throw new IllegalStateException(
                    "Module refers to library " + lib + " not present in the project spec");
              }
              LibraryOrderEntry entry = roots.addLibraryEntry(library);
              // TODO should this stuff be specified by the Module proto too?
              entry.setScope(DependencyScope.COMPILE);
              entry.setExported(false);
            }

            WorkspaceLanguageSettings workspaceLanguageSettings =
                LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

            for (BlazeQuerySyncPlugin syncPlugin : BlazeQuerySyncPlugin.EP_NAME.getExtensions()) {
              // TODO update ProjectProto.Module and updateProjectStructure() to allow a more
              // suitable
              //   data type to be passed in here instead of androidResourceDirectories and
              //   androidSourcePackages
              syncPlugin.updateProjectStructureForQuerySync(
                  project,
                  context,
                  models,
                  workspaceRoot,
                  module,
                  ImmutableSet.copyOf(moduleSpec.getAndroidResourceDirectoriesList()),
                  ImmutableSet.<String>builder()
                      .addAll(moduleSpec.getAndroidSourcePackagesList())
                      .addAll(moduleSpec.getAndroidCustomPackagesList())
                      .build(),
                  workspaceLanguageSettings);
            }
            models.commit();
          }
        });
  }

  private Library getOrCreateLibrary(
      IdeModifiableModelsProvider models, ProjectProto.Library libSpec) {
    // TODO this needs more work, it's a bit messy.
    Library library = models.getLibraryByName(libSpec.getName());
    if (library == null) {
      library = models.createLibrary(libSpec.getName());
    }
    Path projectBase = Paths.get(project.getBasePath());
    ImmutableMap<String, ProjectProto.JarDirectory> dirs =
        libSpec.getClassesJarList().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    d -> UrlUtil.pathToIdeaUrl(projectBase.resolve(d.getPath())),
                    Function.identity()));

    // make sure the library contains only jar directory urls we want
    ModifiableModel modifiableModel = models.getModifiableLibraryModel(library);

    Set<String> foundJarDirectories = Sets.newHashSet();
    for (String url : modifiableModel.getUrls(OrderRootType.CLASSES)) {
      if (modifiableModel.isJarDirectory(url) && dirs.containsKey(url)) {
        foundJarDirectories.add(url);
      } else {
        modifiableModel.removeRoot(url, OrderRootType.CLASSES);
      }
    }
    for (String notFound : Sets.difference(dirs.keySet(), foundJarDirectories)) {
      ProjectProto.JarDirectory dir = dirs.get(notFound);
      modifiableModel.addJarDirectory(notFound, dir.getRecursive(), OrderRootType.CLASSES);
    }

    ImmutableSet<String> srcJars =
        libSpec.getSourcesList().stream()
            .filter(LibrarySource::hasSrcjar)
            .map(LibrarySource::getSrcjar)
            .map(ProjectPath::create)
            .map(
                p -> UrlUtil.pathToUrl(projectPathResolver.resolve(p).toString(), p.innerJarPath()))
            .collect(ImmutableSet.toImmutableSet());
    Set<String> foundSrcJars = Sets.newHashSet();
    for (String url : modifiableModel.getUrls(OrderRootType.SOURCES)) {
      if (srcJars.contains(url)) {
        foundSrcJars.add(url);
      } else {
        final String file = VfsUtil.urlToPath(url);
        if (workspaceRoot.isInWorkspace(new File(file)) || Path.of(file).startsWith(projectBase)) {
          modifiableModel.removeRoot(url, OrderRootType.SOURCES);
        }
      }
    }
    for (String missing : Sets.difference(srcJars, foundSrcJars)) {
      modifiableModel.addRoot(missing, OrderRootType.SOURCES);
    }

    return library;
  }

  /**
   * Removes any existing library that should not be used by this project e.g. inherit from old
   * project.
   */
  private int removeUnusedLibraries(
      IdeModifiableModelsProvider models, List<ProjectProto.Library> libraries) {
    ImmutableSet<String> librariesToKeep =
        libraries.stream().map(ProjectProto.Library::getName).collect(toImmutableSet());
    int removedLibCount = 0;
    for (Library library : models.getAllLibraries()) {
      if (!librariesToKeep.contains(library.getName())) {
        removedLibCount++;
        models.removeLibrary(library);
      }
    }
    return removedLibCount;
  }
}
