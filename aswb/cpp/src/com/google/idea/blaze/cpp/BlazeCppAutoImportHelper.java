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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.autoImport.OCDefaultAutoImportHelper;
import com.jetbrains.cidr.lang.preprocessor.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;
import java.util.List;
import javax.annotation.Nullable;

/**
 * CLion's auto-import suggestions result in include paths relative to the current file (CPP-7593,
 * CPP-6369). Instead, we want paths relative to the header search root (e.g. the relevant
 * blaze/bazel package path). Presumably this will be fixed in a future CLion release, but in the
 * meantime, fix it ourselves.
 */
public class BlazeCppAutoImportHelper extends OCDefaultAutoImportHelper {

  @Override
  public boolean supports(OCResolveRootAndConfiguration rootAndConfiguration) {
    if (rootAndConfiguration.getConfiguration() == null) {
      return false;
    }
    Project project = rootAndConfiguration.getConfiguration().getProject();
    return Blaze.getProjectType(project) == ProjectType.ASPECT_SYNC;
  }

  /**
   * Search in the configuration's header roots only. All other cases are covered by CLion's default
   * implementation.
   */
  @Override
  public boolean processPathSpecificationToInclude(
      Project project,
      @Nullable VirtualFile targetFile,
      VirtualFile fileToImport,
      OCResolveRootAndConfiguration rootAndConfiguration,
      Processor<ImportSpecification> processor) {
    // Check "system" roots first. "user" roots may include the workspace root, and the system
    // headers might be checked into source control, which would make it under the workspace root
    // as well. NOTE: this is a bit backward compared to the actual #include search roots priority,
    // where system search roots are checked later.
    OCResolveConfiguration resolveConfiguration = rootAndConfiguration.getConfiguration();
    if (resolveConfiguration == null) {
      return false;
    }
    ImportSpecification specification =
        findMatchingRoot(
            fileToImport, getHeaderRoots(rootAndConfiguration), /* asUserHeader= */ false);
    if (specification != null && !processor.process(specification)) {
      return false;
    }
    specification =
        findMatchingRoot(
            fileToImport, getHeaderRoots(rootAndConfiguration), /* asUserHeader= */ true);
    if (specification != null) {
      processor.process(specification);
    }
    return false;
  }

  @Nullable
  private static ImportSpecification findMatchingRoot(
      VirtualFile fileToImport, List<HeadersSearchRoot> roots, boolean asUserHeader) {
    for (HeadersSearchRoot root : roots) {
      if (!(root instanceof IncludedHeadersRoot)) {
        continue;
      }
      IncludedHeadersRoot includedHeadersRoot = (IncludedHeadersRoot) root;
      if (asUserHeader != (includedHeadersRoot.getKind() == HeadersSearchPath.Kind.USER)) {
        continue;
      }
      VirtualFile rootBase = root.getVirtualFile();
      if (rootBase == null) {
        continue;
      }
      String relativePath = VfsUtilCore.getRelativePath(fileToImport, rootBase);
      if (relativePath == null) {
        continue;
      }
      return new ImportSpecification(
          relativePath,
          asUserHeader
              ? ImportSpecification.Kind.USER_HEADER_SEARCH_PATH
              : ImportSpecification.Kind.SYSTEM_HEADER_SEARCH_PATH);
    }
    return null;
  }

  /** Return roots that could be used for angle-bracket or quote includes */
  private List<HeadersSearchRoot> getHeaderRoots(OCResolveRootAndConfiguration rootAndConfig) {
    if (rootAndConfig.getConfiguration() == null) {
      return ImmutableList.of();
    }
    OCCompilerSettings settings =
        rootAndConfig
            .getConfiguration()
            .getCompilerSettings(rootAndConfig.getKind(), rootAndConfig.getRootFile());
    return settings.getHeadersSearchRoots().getAllRoots();
  }
}
