/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.util.UrlUtil;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.JavaSyntheticLibrary;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides all libraries that bazel query sync projects need to depends on. Registers them via these handlers will avoid performance issues
 * due count of library is too large.
 */
public class QuerySyncBazelAdditionalLibraryRootsProvider extends AdditionalLibraryRootsProvider {


  @Override
  public final Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    if (!Blaze.getProjectType(project).equals(BlazeImportSettings.ProjectType.QUERY_SYNC) ||
        !QuerySync.enableBazelAdditionalLibraryRootsProvider()) {
      return ImmutableList.of();
    }
    return CachedValuesManager.getManager(project).getCachedValue(
      project,
      () ->
        Result.create(getLibs(project),
                      QuerySyncManager.getInstance(project).getProjectModificationTracker()));
  }

  private ImmutableSet<SyntheticLibrary> getLibs(Project project) {
    Optional<QuerySyncProject> loadedProject = QuerySyncManager.getInstance(project).getLoadedProject();
    Optional<QuerySyncProjectSnapshot> snapshot = QuerySyncManager.getInstance(project).getCurrentSnapshot();
    if (loadedProject.isEmpty() || snapshot.isEmpty()) {
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<SyntheticLibrary> libs = ImmutableSet.builder();
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    for (ProjectProto.Library libSpec : snapshot.get().project().getLibraryList()) {
      libs.add(createSyntheticLibrary(Paths.get(project.getBasePath()), virtualFileManager, loadedProject.get().getProjectPathResolver(),
                                      libSpec));
    }
    return libs.build();
  }

  private SyntheticLibrary createSyntheticLibrary(Path projectBase,
                                                  VirtualFileManager virtualFileManager,
                                                  ProjectPath.Resolver projectPathResolver,
                                                  ProjectProto.Library libSpec) {
    List<? extends VirtualFile> classJars = libSpec.getClassesJarList().stream()
      .map(d -> virtualFileManager.findFileByUrl(UrlUtil.pathToIdeaUrl(projectBase.resolve(d.getPath()))))
      .filter(Objects::nonNull)
      .distinct()
      .collect(toImmutableList());

    List<? extends VirtualFile> sourceJars = libSpec.getSourcesList().stream()
      .filter(ProjectProto.LibrarySource::hasSrcjar)
      .map(ProjectProto.LibrarySource::getSrcjar)
      .map(ProjectPath::create)
      .map(
        p -> virtualFileManager.findFileByUrl(UrlUtil.pathToUrl(projectPathResolver.resolve(p).toString(), p.innerJarPath())))
      .filter(Objects::nonNull)
      .distinct()
      .collect(toImmutableList());
    return new JavaSyntheticLibrary(libSpec.getName(), sourceJars, classJars, ImmutableSet.of());
  }

  public static Collection<SyntheticLibrary> getAdditionalBazelLibraries(Project project) {
    QuerySyncBazelAdditionalLibraryRootsProvider querySyncBazelAdditionalLibraryRootsProvider =
      AdditionalLibraryRootsProvider.EP_NAME.findExtension(
        QuerySyncBazelAdditionalLibraryRootsProvider.class);
    if (querySyncBazelAdditionalLibraryRootsProvider == null) {
      throw new IllegalStateException("Cannot load QuerySyncBazelAdditionalLibraryRootsProvider class. Restart the IDE may help.");
    }
    return querySyncBazelAdditionalLibraryRootsProvider.getAdditionalProjectLibraries(project);
  }
}
