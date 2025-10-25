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
import com.android.SdkConstants;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.JarFileSystem;
/**
 * Finds java source files corresponding to a class file inside a jar file belonging to a
 * dependency.
 */
public class ProtoFileJavaSourceFinder extends CompiledJavaSourceFinderBase {
  private static final Logger logger = Logger.getInstance(ProtoFileJavaSourceFinder.class);
  private final JarFileSystem jarFileSystem;
  private final PsiManager psiManager;

  public ProtoFileJavaSourceFinder(ClsFileImpl clsFile) {
    super(clsFile);
    jarFileSystem = JarFileSystem.getInstance();
    psiManager = PsiManager.getInstance(project);
  }

  @Nullable
  public PsiFile findSourceFile() {
    ImmutableSet<Path> srcJars = getProtoSrcJars();
    if (srcJars.isEmpty()) {
      return null;
    }
    Set<PsiFile> matchingPsiFiles = new HashSet<>();
    ImmutableSet<String> qualifiedClassNames =
      stream(clsFile.getClasses()).map(PsiClass::getQualifiedName).collect(toImmutableSet());
    ContentIterator iterator =
      (VirtualFile fileOrDir) -> {
        if (!fileOrDir.isDirectory()) {
          PsiFile psiFile = psiManager.findFile(fileOrDir);
          if (psiFile != null && containsClass(psiFile, qualifiedClassNames)) {
            matchingPsiFiles.add(psiFile);
          }
        }
        return true;
      };
    srcJars.forEach(
      jarRoot -> {
        VirtualFile vf = jarFileSystem.findLocalVirtualFileByPath(jarRoot.toString());
        if (vf != null) {
          VfsUtilCore.iterateChildrenRecursively(vf, null, iterator);
        }
      });
    if (matchingPsiFiles.size() > 1) {
      logger.warn(
        String.format(
          "Warning: found more than 1 matching source file for %s: %s",
          clsFile, matchingPsiFiles));
    }
    return matchingPsiFiles.stream().findAny().orElse(null);
  }

  private ImmutableSet<Path> getProtoSrcJars() {
    return getJavaArtifactInfos().stream()
      .flatMap(it -> it.protoSrcjars().stream())
      .map(BuildArtifact::artifactPath)
      .map(ArtifactDirectories.DEFAULT::resolveChild)
      .map(ProjectPath.ProjectRelativeProjectPath::relativePath)
      .map(projectPath::resolve)
      .filter(
        path ->
          (path.toString().endsWith(SdkConstants.DOT_JAR))
          || path.toString().endsWith(SdkConstants.DOT_SRCJAR))
      .collect(toImmutableSet());
  }
}
