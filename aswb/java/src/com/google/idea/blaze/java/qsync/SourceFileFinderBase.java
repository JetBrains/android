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
package com.google.idea.blaze.java.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import java.nio.file.Path;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClassOwner;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A base class used to find source files for a class jar. It provides a basic workflow to fetch
 * source files. And it allows different implementation to provide its own implementation.
 */
abstract class SourceFileFinderBase {
  private static final Logger logger = Logger.getInstance(SourceFileFinderBase.class);

  protected final Project project;
  protected final QuerySyncManager querySyncManager;
  protected final Path workspaceRoot;
  protected final Path projectPath;
  protected final PsiFile clsFile;
  protected final ImmutableSet<String> qualifiedClassNames;

  public SourceFileFinderBase(PsiFile clsFile) {
    this(
        clsFile.getProject(),
        QuerySyncManager.getInstance(clsFile.getProject()),
        WorkspaceRoot.fromProject(clsFile.getProject()).path(),
        Path.of(clsFile.getProject().getBasePath()),
        clsFile);
  }

  @VisibleForTesting
  public SourceFileFinderBase(
      Project project,
      QuerySyncManager querySyncManager,
      Path workspaceRoot,
      Path projectPath,
      PsiFile clsFile) {
    this.project = project;
    this.querySyncManager = querySyncManager;
    this.workspaceRoot = workspaceRoot;
    this.projectPath = projectPath;
    this.clsFile = clsFile;
    this.qualifiedClassNames = getQualifiedClassNames(clsFile);
  }

  @Nullable
  public PsiFile findSourceFile() {
    ImmutableSet<Path> sourcePaths =
        getJavaArtifactInfos().stream().flatMap(this::filterSourcePaths).collect(toImmutableSet());
    if (sourcePaths.isEmpty()) {
      return null;
    }

    ImmutableSet<PsiFile> matchingPsiFiles =
        sourcePaths.stream()
            .map(this::convertToVirtualFile)
            .filter(Objects::nonNull)
            .flatMap(vf -> getMatchingPsiFile(vf).stream())
            .collect(toImmutableSet());

    if (matchingPsiFiles.size() > 1) {
      logger.warn(
          String.format(
              "Warning: found more than 1 matching source file for %s: %s",
              clsFile, matchingPsiFiles));
    }
    return matchingPsiFiles.stream().findAny().orElse(null);
  }

  /**
   * Returns the path to the source files stored in current JavaArtifactInfo. In most of cases, it's
   * a list of source files or a source jar.
   */
  abstract Stream<Path> filterSourcePaths(JavaArtifactInfo artifactInfo);

  /**
   * Converts the file to VirtualFile. The implementation can be different according to the type of
   * file a jar file or a plain file.
   */
  @Nullable
  abstract VirtualFile convertToVirtualFile(Path path);

  /**
   * Returns a list of PsiFile that contains the class we are looking for. It can be more than one
   * since the given virtual file can be a jar file where we need to visit a set of source files in
   * it.
   */
  abstract Set<PsiFile> getMatchingPsiFile(VirtualFile vf);

  private ImmutableList<JavaArtifactInfo> getJavaArtifactInfos() {
    VirtualFile file = clsFile.getContainingFile().getVirtualFile();
    // We expect a .class file inside a .jar file. Get the outer jar file:
    VirtualFile jar = JarFileSystem.getInstance().getLocalVirtualFileFor(file);
    if (jar == null) {
      // Not a jar file - ignore it
      return ImmutableList.of();
    }
    Path jarPath = jar.toNioPath();
    if (!jarPath.startsWith(projectPath)) {
      return ImmutableList.of();
    }
    jarPath = projectPath.relativize(jarPath);

    QuerySyncProjectSnapshot snapshot = querySyncManager.getCurrentSnapshot().orElse(null);
    if (snapshot == null) {
      return ImmutableList.of();
    }
    return snapshot.getArtifactIndex().getInfoForJarArtifact(jarPath);
  }

  protected ImmutableSet<String> getQualifiedClassNames(PsiFile psiFile) {
    if (psiFile instanceof PsiClassOwner psiClassOwner) {
      return stream(psiClassOwner.getClasses())
          .map(PsiClass::getQualifiedName)
          .collect(toImmutableSet());
    }
    return ImmutableSet.of();
  }

  protected boolean containsClass(PsiFile file) {
    return getQualifiedClassNames(file).stream().anyMatch(this.qualifiedClassNames::contains);
  }
}
