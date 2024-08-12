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
package com.google.idea.blaze.java.run.coverage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.coverage.CoverageUtils;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.coverage.CoverageAnnotator;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageFileProvider;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.view.CoverageListNode;
import com.intellij.coverage.view.CoverageListRootNode;
import com.intellij.coverage.view.CoverageViewExtension;
import com.intellij.coverage.view.CoverageViewManager.StateBean;
import com.intellij.coverage.view.DirectoryCoverageViewExtension;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Provides coverage support for blaze run configurations. */
@SuppressWarnings("rawtypes")
public class BlazeCoverageEngine extends CoverageEngine {

  public static BlazeCoverageEngine getInstance() {
    return Extensions.findExtension(EP_NAME, BlazeCoverageEngine.class);
  }

  @Override
  public boolean isApplicableTo(@Nullable RunConfigurationBase config) {
    return CoverageUtils.isApplicableTo(config);
  }

  @Override
  public boolean canHavePerTestCoverage(@Nullable RunConfigurationBase runConfigurationBase) {
    return false;
  }

  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(
      @Nullable RunConfigurationBase config) {
    return new BlazeCoverageEnabledConfiguration(config);
  }

  @Nullable
  @Override
  public CoverageSuite createCoverageSuite(
      CoverageRunner covRunner,
      String name,
      CoverageFileProvider coverageDataFileProvider,
      @Nullable String[] filters,
      long lastCoverageTimeStamp,
      @Nullable String suiteToMerge,
      boolean coverageByTestEnabled,
      boolean tracingEnabled,
      boolean trackTestFolders,
      Project project) {
    return null;
  }

  @Nullable
  @Override
  public CoverageSuite createCoverageSuite(
      CoverageRunner runner,
      String name,
      CoverageFileProvider fileProvider,
      CoverageEnabledConfiguration config) {
    if (!(config instanceof BlazeCoverageEnabledConfiguration)) {
      return null;
    }
    Project project = config.getConfiguration().getProject();
    return new BlazeCoverageSuite(project, name, fileProvider, runner);
  }

  @Nullable
  @Override
  public CoverageSuite createEmptyCoverageSuite(CoverageRunner coverageRunner) {
    return new BlazeCoverageSuite();
  }

  @Override
  public CoverageAnnotator getCoverageAnnotator(Project project) {
    return BlazeCoverageAnnotator.getInstance(project);
  }

  @Override
  public boolean coverageEditorHighlightingApplicableTo(PsiFile psiFile) {
    return true;
  }

  @Override
  public boolean acceptedByFilters(PsiFile psiFile, CoverageSuitesBundle coverageSuitesBundle) {
    return true;
  }

  @Override
  public boolean coverageProjectViewStatisticsApplicableTo(VirtualFile fileOrDir) {
    return true;
  }

  @Override
  public boolean recompileProjectAndRerunAction(
      Module module, CoverageSuitesBundle coverageSuitesBundle, Runnable runnable) {
    return false;
  }

  @Override
  public boolean includeUntouchedFileInCoverage(
      String qualifiedName, File outputFile, PsiFile sourceFile, CoverageSuitesBundle suite) {
    return false;
  }

  @Nullable
  @Override
  public List<Integer> collectSrcLinesForUntouchedFile(
      File file, CoverageSuitesBundle coverageSuitesBundle) {
    return null;
  }

  @Override
  public String getQualifiedName(File outputFile, PsiFile sourceFile) {
    return sourceFile.getVirtualFile().getPath();
  }

  @Override
  public Set<String> getQualifiedNames(PsiFile psiFile) {
    return ImmutableSet.of(psiFile.getVirtualFile().getPath());
  }

  @Override
  public List<PsiElement> findTestsByNames(String[] strings, Project project) {
    return ImmutableList.of();
  }

  @Nullable
  @Override
  public String getTestMethodName(PsiElement psiElement, AbstractTestProxy abstractTestProxy) {
    return null;
  }

  @Override
  public String getPresentableText() {
    return Blaze.defaultBuildSystemName() + " Coverage";
  }

  @Nullable
  @Override
  public CoverageViewExtension createCoverageViewExtension(
      Project project, CoverageSuitesBundle suites, StateBean stateBean) {
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    if (root == null) {
      return null;
    }
    Set<File> topLevelDirectories = getTopLevelDirectories(suites);
    if (topLevelDirectories.isEmpty()) {
      return null;
    }
    CoverageAnnotator annotator = getCoverageAnnotator(project);
    return new DirectoryCoverageViewExtension(project, annotator, suites, stateBean) {
      private List<AbstractTreeNode<?>> topLevelNodes;

      @Override
      public AbstractTreeNode createRootNode() {
        if (topLevelDirectories.size() == 1) {
          File file = topLevelDirectories.iterator().next();
          return new CoverageListRootNode(project, resolveFile(project, file), suites, stateBean);
        }
        return new CoverageListRootNode(
            project, resolveFile(project, root.directory()), suites, stateBean) {
          @Override
          public List<? extends AbstractTreeNode<?>> getChildren() {
            return getRootChildren(this);
          }
        };
      }

      @Override
      public List<AbstractTreeNode<?>> getChildrenNodes(AbstractTreeNode node) {
        if (node instanceof CoverageListRootNode && topLevelDirectories.size() != 1) {
          return getRootChildren((CoverageListRootNode) node);
        }
        return super.getChildrenNodes(node).stream()
            .filter(treeNode -> !isLeaf(treeNode) || getPercentage(0, treeNode) != null)
            .collect(Collectors.toList());
      }

      private List<AbstractTreeNode<?>> getRootChildren(CoverageListRootNode root) {
        if (topLevelNodes == null) {
          topLevelNodes = ReadAction.compute(() -> getTopLevelNodes(project, suites, stateBean));
          for (AbstractTreeNode<?> node : topLevelNodes) {
            node.setParent(root);
          }
        }
        return topLevelNodes;
      }
    };
  }

  private static Set<File> getTopLevelDirectories(CoverageSuitesBundle suites) {
    return Arrays.stream(suites.getSuites())
        .map(s -> ((BlazeCoverageSuite) s).getDeepestRootDirectory())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static List<AbstractTreeNode<?>> getTopLevelNodes(
      Project project, CoverageSuitesBundle suites, StateBean stateBean) {
    return getTopLevelDirectories(suites).stream()
        .map(file -> resolveFile(project, file))
        .filter(Objects::nonNull)
        .map(psiFile -> new CoverageListNode(project, psiFile, suites, stateBean))
        .collect(Collectors.toList());
  }

  private static boolean isLeaf(AbstractTreeNode node) {
    return node.getValue() instanceof PsiFile;
  }

  @Nullable
  private static PsiFileSystemItem resolveFile(Project project, @Nullable File file) {
    if (file == null) {
      return null;
    }
    PsiManager manager = PsiManager.getInstance(project);
    VirtualFile vf =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByPath(file.getPath());
    if (vf == null) {
      return null;
    }
    return vf.isDirectory() ? manager.findDirectory(vf) : manager.findFile(vf);
  }
}
