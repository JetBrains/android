/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.testing.cidr.StubOCResolveConfigurationBase;
import com.google.idea.testing.cidr.StubOCWorkspace;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;
import java.util.List;
import org.junit.Before;

/** C++ test cases which require resolving headers with non-default header search roots. */
public class BlazeCppResolvingTestCase extends BlazeCppIntegrationTestCase {

  // Assumes tests want some representative non-workspace header search roots.
  // Assumes workspace setup generates similar OCResolveConfigurations.
  @Before
  public void setupDefaultIncludeRoots() {
    VirtualFile genfilesRoot = fileSystem.createDirectory("output/genfiles");
    VirtualFile readonlyRoot = fileSystem.createDirectory("READONLY/workspace");
    workspace.createDirectory(new WorkspacePath("third_party/stl"));
    workspace.createDirectory(new WorkspacePath("third_party/toolchain/include/c++/4.9"));
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setOutputBase(fileSystem.getRootDir() + "/output")
            .build();
    setupIncludeRoots(
        projectData,
        ImmutableList.of(
            new ExecutionRootPath("."),
            new ExecutionRootPath(VfsUtilCore.virtualToIoFile(readonlyRoot)),
            new ExecutionRootPath(VfsUtilCore.virtualToIoFile(genfilesRoot))),
        ImmutableList.of(
            new ExecutionRootPath("third_party/stl"),
            new ExecutionRootPath("third_party/toolchain/include/c++/4.9")));
  }

  void buildSymbols(OCFile... files) {
    for (OCFile file : files) {
      resetFileSymbols(file);
    }
    FileSymbolTablesCache.getInstance(getProject()).ensurePendingFilesProcessed();
  }

  private void setupIncludeRoots(
      BlazeProjectData projectData,
      List<ExecutionRootPath> quoteRoots,
      List<ExecutionRootPath> angleRoots) {
    ExecutionRootPathResolver pathResolver = executionRootPathResolver(projectData);
    ImmutableList.Builder<HeadersSearchRoot> searchRoots = ImmutableList.builder();
    for (ExecutionRootPath path : quoteRoots) {
      searchRoots.add(searchRootFromExecRoot(pathResolver, path, true));
    }
    for (ExecutionRootPath path : angleRoots) {
      searchRoots.add(searchRootFromExecRoot(pathResolver, path, false));
    }
    StubOCWorkspace stubOCWorkspace = new StubOCWorkspace(getProject());
    StubOCResolveConfigurationBase stubConfiguration =
        stubOCWorkspace.getModifiableStubConfiguration();
    // Assumes the 2018.1+ behavior where projectHeaderRoots is not used and only
    // libraryHeadersRoots is used.
    stubConfiguration.setLibraryIncludeRoots(searchRoots.build());

    // OCWorkspace is registered as project service after 2023.1.1.1
    if (ApplicationInfo.getInstance().getBuild().getBaselineVersion() >= 231) {
      registerProjectService(OCWorkspace.class, stubOCWorkspace);
    } else {
      registerProjectComponent(OCWorkspace.class, stubOCWorkspace);
    }
  }

  private ExecutionRootPathResolver executionRootPathResolver(BlazeProjectData projectData) {
    return new ExecutionRootPathResolver(
        new BazelBuildSystemProvider(),
        workspaceRoot,
        projectData.getBlazeInfo().getExecutionRoot(),
        projectData.getWorkspacePathResolver());
  }

  private HeadersSearchRoot searchRootFromExecRoot(
      ExecutionRootPathResolver resolver, ExecutionRootPath path, boolean isUserHeader) {
    VirtualFile vf = fileSystem.findFile(resolver.resolveExecutionRootPath(path).getAbsolutePath());
    HeadersSearchPath.Kind kind =
        isUserHeader ? HeadersSearchPath.Kind.USER : HeadersSearchPath.Kind.SYSTEM;
    return IncludedHeadersRoot.create(
        getProject(), vf, /* recursive= */ false, /* preferQuotes= */ false, kind);
  }

  private void resetFileSymbols(OCFile file) {
    FileSymbolTablesCache.getInstance(getProject())
        .handleOutOfCodeBlockChange(file, /* hasMacro= */ true);
  }
}
