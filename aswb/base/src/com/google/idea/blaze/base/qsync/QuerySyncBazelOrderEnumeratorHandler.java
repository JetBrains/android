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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.base.sync.data.BlazeDataStorage.WORKSPACE_MODULE_NAME;
import static com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation.JAVA_DEPS_LIB_NAME;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;

/**
 * Provides all libraries that quer sync workspace module need to depends on. Registers them via these handlers will avoid performance
 * issues due count of library is too large.
 */
public class QuerySyncBazelOrderEnumeratorHandler extends OrderEnumerationHandler {
  private final Project project;

  public static final class FactoryImpl extends OrderEnumerationHandler.Factory {
    @Override
    public boolean isApplicable(Module module) {
      return Blaze.getProjectType(module.getProject()).equals(BlazeImportSettings.ProjectType.QUERY_SYNC) &&
             module.getName().equals(WORKSPACE_MODULE_NAME) &&
             QuerySync.enableBazelAdditionalLibraryRootsProvider();
    }

    @Override
    public OrderEnumerationHandler createHandler(Module module) {
      return new QuerySyncBazelOrderEnumeratorHandler(module);
    }
  }

  public QuerySyncBazelOrderEnumeratorHandler(Module module) {
    project = module.getProject();
  }

  @Override
  public boolean addCustomRootsForLibraryOrSdk(LibraryOrSdkOrderEntry forOrderEntry, OrderRootType type, Collection<String> urls) {
    if (!forOrderEntry.getPresentableName().equals(JAVA_DEPS_LIB_NAME)) {
      return false;
    }
    return urls.addAll(getRoots(project, type));
  }

  private static ImmutableSet<String> getRoots(Project project, OrderRootType type) {
    if (!type.equals(OrderRootType.CLASSES) && !type.equals(OrderRootType.SOURCES)) {
      return ImmutableSet.of();
    }
    return QuerySyncBazelAdditionalLibraryRootsProvider.getAdditionalBazelLibraries(project).stream()
      .map(type.equals(OrderRootType.CLASSES) ? SyntheticLibrary::getBinaryRoots : SyntheticLibrary::getSourceRoots)
      .flatMap(Collection::stream)
      .map(
        VirtualFile::getUrl).collect(toImmutableSet());
  }
}
