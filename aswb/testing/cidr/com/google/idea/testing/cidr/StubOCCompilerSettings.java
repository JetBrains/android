/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing.cidr;

import com.google.idea.cidr.CidrCompat;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSettingsKey;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoots;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Stub {@link OCCompilerSettings} for testing. */
class StubOCCompilerSettings implements OCCompilerSettings {
  private final Project project;
  private HeadersSearchRoots roots;

  StubOCCompilerSettings(Project project) {
    this.project = project;
  }

  @Override
  @Nullable
  public OCCompilerKind getCompilerKind() {
    return CidrCompat.getCompilerKind();
  }

  @Override
  @Nullable
  public File getCompilerExecutable() {
    return null;
  }

  @Override
  public File getCompilerWorkingDir() {
    return VfsUtilCore.virtualToIoFile(project.getBaseDir());
  }

  @Override
  @Nullable
  public CidrCompilerSwitches getCompilerSwitches() {
    return new CidrSwitchBuilder().build();
  }

  @Override
  public HeadersSearchRoots getHeadersSearchRoots() {
    return roots;
  }

  @Override
  public List<HeadersSearchPath> getHeadersSearchPaths() {
    return Collections.emptyList();
  }

  @Override
  public List<VirtualFile> getImplicitIncludes() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getImplicitIncludeUrls() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public VirtualFile getMappedInclude(String include) {
    return null;
  }

  @Override
  public String getMappedIncludeUrl(String include) {
    return "";
  }

  @Override
  public List<String> getPreprocessorDefines() {
    return Collections.emptyList();
  }

  @Override
  public Map<OCCompilerFeatures.Type<?>, ?> getCompilerFeatures() {
    return new HashMap<>();
  }

  @Nullable
  @Override
  public CompilerSettingsKey getCachingKey() {
    return null;
  }

  @Override
  public Object getIndexingCluster() {
    return null;
  }

  public void setLibraryIncludeRoots(List<HeadersSearchRoot> roots) {
    this.roots = HeadersSearchRoots.create(roots);
  }
}
