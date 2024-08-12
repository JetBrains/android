/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncProject;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Finds java source files corresponding to a class file inside a jar file belonging to a
 * dependency.
 */
public class ClassFileJavaSourceFinder {

  private static final Logger logger = Logger.getInstance(ClassFileJavaSourceFinder.class);

  private final Project project;
  private final QuerySyncManager querySyncManager;
  private final Path workspaceRoot;
  private final Path projectPath;
  private final ClsFileImpl clsFile;

  public ClassFileJavaSourceFinder(ClsFileImpl clsFile) {
    this(
        clsFile.getProject(),
        QuerySyncManager.getInstance(clsFile.getProject()),
        WorkspaceRoot.fromProject(clsFile.getProject()).path(),
        Path.of(clsFile.getProject().getBasePath()),
        clsFile);
  }

  @VisibleForTesting
  public ClassFileJavaSourceFinder(
      Project project,
      QuerySyncManager querySyncManager,
      Path workspaceRoot,
      Path projectPath,
      ClsFileImpl clsFile) {
    this.project = project;
    this.querySyncManager = querySyncManager;
    this.workspaceRoot = workspaceRoot;
    this.projectPath = projectPath;
    this.clsFile = clsFile;
  }

  @Nullable
  public PsiFile findSourceFile() {
    if (!querySyncManager.isProjectLoaded()) {
      return null;
    }
    ImmutableSet<Path> jarSrcsPaths = getWorkspaceSources();
    if (jarSrcsPaths.isEmpty()) {
      return null;
    }
    // first, find source files with the expected name:
    ImmutableSet<Path> matchedJarSrcs = findMatchingSourcePathsByFilename(jarSrcsPaths);
    if (matchedJarSrcs.isEmpty()) {
      return null;
    }
    // convert from workspace relative path to PsiFile:
    ImmutableSet<PsiFile> matchingPsiFiles =
        matchedJarSrcs.stream()
            .map(workspaceRoot::resolve)
            .map(LocalFileSystem.getInstance()::findFileByNioFile)
            .filter(Objects::nonNull)
            .map(PsiManager.getInstance(project)::findFile)
            .collect(toImmutableSet());
    if (matchingPsiFiles.isEmpty()) {
      return null;
    }
    // then, select from those the file(s) that define the class we're looking for:
    // Then select the one that defines the same class as the original class file. This ensures
    // that we select the right source file in case there are files of the same name in multiple
    // packages.
    ImmutableSet<String> qualifiedClassNames =
        stream(clsFile.getClasses()).map(PsiClass::getQualifiedName).collect(toImmutableSet());
    matchingPsiFiles =
        matchingPsiFiles.stream()
            .filter(c -> containsClass(c, qualifiedClassNames))
            .collect(toImmutableSet());
    if (matchingPsiFiles.size() > 1) {
      logger.warn(
          String.format(
              "Warning: found more than 1 matching source file for %s: %s",
              clsFile, matchingPsiFiles));
    }
    return matchingPsiFiles.stream().findAny().orElse(null);
  }

  private ImmutableSet<Path> findMatchingSourcePathsByFilename(Set<Path> workspaceSourcesForJar) {
    // Find the original source file name(s) for the classes in the file:
    ImmutableSet<String> sourceNamesFromClasses =
        stream(clsFile.getClasses())
            .filter(ClsClassImpl.class::isInstance)
            .map(ClsClassImpl.class::cast)
            .map(ClsClassImpl::getSourceFileName)
            .collect(toImmutableSet());
    // Match the original source file name(s) against the sources for the library jar:
    return workspaceSourcesForJar.stream()
        .filter(p -> sourceNamesFromClasses.contains(p.getFileName().toString()))
        .collect(toImmutableSet());
  }

  private ImmutableSet<Path> getWorkspaceSources() {
    VirtualFile file = clsFile.getContainingFile().getVirtualFile();
    // We expect a .class file inside a .jar file. Get the outer jar file:
    VirtualFile jar = JarFileSystem.getInstance().getLocalVirtualFileFor(file);
    if (jar == null) {
      // Not a jar file - ignore it
      return ImmutableSet.of();
    }
    Path jarPath = jar.toNioPath();
    if (!jarPath.startsWith(projectPath)) {
      return ImmutableSet.of();
    }
    jarPath = projectPath.relativize(jarPath);

    QuerySyncProjectSnapshot snapshot =
        querySyncManager
            .getLoadedProject()
            .map(QuerySyncProject::getSnapshotHolder)
            .flatMap(SnapshotHolder::getCurrent)
            .orElse(null);
    if (snapshot == null) {
      return ImmutableSet.of();
    }

    return snapshot
        .getArtifactIndex()
        .getInfoForArtifact(jarPath)
        .map(JavaArtifactInfo::sources)
        .orElse(ImmutableSet.of());
  }

  // private ImmutableSet<PsiFile> filterByExpectedQualified

  private static boolean containsClass(PsiFile file, Set<String> qualifiedClassName) {
    return stream(file.getChildren())
        .filter(PsiClass.class::isInstance)
        .map(PsiClass.class::cast)
        .map(PsiClass::getQualifiedName)
        .anyMatch(qualifiedClassName::contains);
  }
}
