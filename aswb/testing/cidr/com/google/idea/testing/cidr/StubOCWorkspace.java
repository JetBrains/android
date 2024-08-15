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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.cidr.lang.OCFileType;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackersImpl;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** A stub {@link OCWorkspace} to use for testing. */
public class StubOCWorkspace implements OCWorkspace {
  private final ImmutableList<OCResolveConfiguration> resolveConfigurations;
  private final OCWorkspaceModificationTrackersImpl modificationTrackers;

  public StubOCWorkspace(Project project) {
    // For now, every source file gets the same resolve configuration.
    resolveConfigurations = ImmutableList.of(new StubOCResolveConfigurationBase(project) {});
    modificationTrackers = new OCWorkspaceModificationTrackersImpl(project);
  }

  public StubOCResolveConfigurationBase getModifiableStubConfiguration() {
    return (StubOCResolveConfigurationBase) resolveConfigurations.get(0);
  }

  // @Override removed in #api223
  public int getClientVersion() {
    return 0;
  }

  @Override
  public int getClientVersion(String clientKey) {
    return 0;
  }

  @Override
  public List<OCResolveConfiguration> getConfigurations() {
    return resolveConfigurations;
  }

  @Override
  public List<OCResolveConfiguration> getConfigurations(String clientKey) {
    return resolveConfigurations;
  }

  @Override
  public List<OCResolveConfiguration> getConfigurationsForFile(VirtualFile sourceFile) {
    return OCFileType.INSTANCE.equals(sourceFile.getFileType())
        ? resolveConfigurations
        : Collections.emptyList();
  }

  @Override
  public List<OCResolveConfiguration> getConfigurationsForFile(String sourceFileUrl) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public OCResolveConfiguration getConfigurationById(String id) {
    return ContainerUtil.find(resolveConfigurations, it -> Objects.equals(id, it.getUniqueId()));
  }

  // @Override removed in #api223
  public ModifiableModel getModifiableModel() {
    throw new UnsupportedOperationException();
  }

  // @Override removed in #api233
  public ModifiableModel getModifiableModel(boolean clear) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ModifiableModel getModifiableModel(String clientKey, boolean clear) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OCWorkspaceModificationTrackers getModificationTrackers() {
    return modificationTrackers;
  }
}
