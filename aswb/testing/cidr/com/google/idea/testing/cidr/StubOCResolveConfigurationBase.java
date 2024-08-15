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

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrFileSeparators;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculator;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurationImpl;
import com.jetbrains.cidr.lang.workspace.OCVariant;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Stub {@link OCResolveConfiguration} for testing. */
public abstract class StubOCResolveConfigurationBase extends UserDataHolderBase
    implements OCResolveConfiguration {

  private final Project project;
  private final StubOCCompilerSettings compilerSettings;

  StubOCResolveConfigurationBase(Project project) {
    this.project = project;
    this.compilerSettings = new StubOCCompilerSettings(project);
  }

  public void setLibraryIncludeRoots(List<HeadersSearchRoot> searchRoots) {
    compilerSettings.setLibraryIncludeRoots(searchRoots);
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public String getUniqueId() {
    return getName();
  }

  @Override
  public String getName() {
    return "Stub Resolve Configuration";
  }

  @Nullable
  @Override
  public OCVariant getVariant() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return getName();
  }

  @Override
  public Collection<VirtualFile> getSources() {
    return Collections.emptyList();
  }

  @Override
  public Collection<String> getSourceUrls() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public OCLanguageKind getDeclaredLanguageKind(VirtualFile sourceOrHeaderFile) {
    String fileName = sourceOrHeaderFile.getName();
    if (OCFileTypeHelpers.isSourceFile(fileName)) {
      return getLanguageKind(sourceOrHeaderFile);
    }
    return getMaximumLanguageKind();
  }

  @Nullable
  @Override
  public OCLanguageKind getDeclaredLanguageKind(String sourceFileUrl) {
    return null;
  }

  private OCLanguageKind getMaximumLanguageKind() {
    return CLanguageKind.CPP;
  }

  @Override
  public Set<OCLanguageKind> getEnabledLanguageKinds() {
    return ImmutableSet.of(CLanguageKind.C, CLanguageKind.CPP);
  }

  @Override
  public Stream<OCCompilerSettings> getAllCompilerSettings() {
    return Stream.of();
  }

  private OCLanguageKind getLanguageKind(VirtualFile sourceFile) {
    OCLanguageKind kind = OCLanguageKindCalculator.tryFileTypeAndExtension(project, sourceFile);
    return kind != null ? kind : getMaximumLanguageKind();
  }

  @Override
  public OCCompilerSettings getCompilerSettings(
      OCLanguageKind ocLanguageKind, @Nullable VirtualFile virtualFile) {
    return compilerSettings;
  }

  @Override
  public OCCompilerSettings getCompilerSettings(OCLanguageKind languageKind, String sourceFileUrl) {
    return compilerSettings;
  }

  @Override
  public int compareTo(OCResolveConfiguration o) {
    return OCResolveConfigurationImpl.compareConfigurations(this, o);
  }

  @Override
  public CidrFileSeparators getFileSeparators() {
    return CidrFileSeparators.UNIX;
  }

  @Override
  public boolean hasSourceFile(@NotNull VirtualFile virtualFile) {
    return false;
  }
}
