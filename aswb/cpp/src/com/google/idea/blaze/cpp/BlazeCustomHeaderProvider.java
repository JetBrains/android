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

import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CustomHeaderProvider;
import com.jetbrains.cidr.lang.preprocessor.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * Provides a quick path to resolving #include "foo/bar.h" where "foo/bar.h" is relative to the
 * workspace root. This assumes that the workspace root is the first entry in the list of search
 * roots (there are also corner cases where this isn't 100% correct). Other search roots could be
 * the genfiles directory and libc, libc++'s directories.
 *
 * <p>In typical projects, only a tiny fraction of includes are generated files (~0.1%), so handling
 * non-generated files efficiently is very low hanging fruit. Including search roots like libc and
 * libc++, non-workspace-root-relative includes could be 35% of the header searches.
 *
 * <p>Ideally our aspect would record which generated files are used, and we could avoid FS
 * operations entirely.
 */
public class BlazeCustomHeaderProvider extends CustomHeaderProvider {

  // Cache the workspace root's VirtualFile so that we can start search from there instead of having
  // to start from the root of the file system.
  private final ConcurrentMap<File, Optional<VirtualFile>> cachedWorkspaceRoots =
      new ConcurrentHashMap<>();

  @Override
  public boolean accepts(@Nullable OCResolveRootAndConfiguration rootAndConfig) {
    if (rootAndConfig == null || rootAndConfig.getConfiguration() == null) {
      return false;
    }
    Project project = rootAndConfig.getConfiguration().getProject();
    return Blaze.getProjectType(project) == ProjectType.ASPECT_SYNC;
  }
  
  @Nullable
  @Override
  public VirtualFile getCustomHeaderFile(
      String includeString,
      HeaderSearchStage stage,
      @Nullable OCResolveConfiguration configuration) {
    if (stage != HeaderSearchStage.BEFORE_START
        || includeString.startsWith("/")
        || configuration == null) {
      return null;
    }
    Project project = configuration.getProject();
    BlazeProjectData data = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (data == null) {
      return null;
    }
    WorkspacePathResolver workspacePathResolver = data.getWorkspacePathResolver();
    Optional<VirtualFile> workspaceRoot = getWorkspaceRoot(includeString, workspacePathResolver);
    if (!workspaceRoot.isPresent()) {
      return null;
    }
    VirtualFile file = workspaceRoot.get().findFileByRelativePath(includeString);
    if (file == null || file.isDirectory()) {
      return null;
    }
    return file;
  }

  private Optional<VirtualFile> getWorkspaceRoot(
      String includeString, WorkspacePathResolver workspacePathResolver) {
    File packageRoot = workspacePathResolver.findPackageRoot(includeString);
    return cachedWorkspaceRoots.compute(
        packageRoot,
        (file, oldValue) -> {
          if (oldValue != null && oldValue.isPresent() && oldValue.get().isValid()) {
            return oldValue;
          }
          return Optional.ofNullable(
              VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(file));
        });
  }

  @Nullable
  @Override
  public String provideSerializationPath(VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getCustomSerializedHeaderFile(
      String serializationPath, Project project, VirtualFile currentFile) {
    return null;
  }
}
