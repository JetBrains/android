/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeSettings;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.*;
import com.android.tools.idea.navigator.nodes.ndk.includes.resolver.IncludeResolver;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.util.VirtualFiles;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

/**
 * <pre>
 * This node represents all of the include folders and files for a given native module.
 *
 * So for example,
 *
 * app
 *   cpp
 *     includes (this class)
 *       NDK Components (PackagingFamilyExpressionView)
 * ...
 * </pre>
 */
public class IncludesViewNode extends ProjectViewNode<NativeIncludes> implements FolderGroupNode {

  @NotNull
  private final NativeIncludes myDependencyInfo;

  public IncludesViewNode(@Nullable Project project, @NotNull NativeIncludes dependencyInfo, @NotNull ViewSettings settings) {
    super(project, dependencyInfo, settings);
    myDependencyInfo = dependencyInfo;
  }

  @NotNull
  private static IncludeSet distinctIncludes(@NotNull NativeIncludes nativeIncludes) {
    IncludeSet set = new IncludeSet();

    // Then include folders from the settings
    Set<String> settingsSeen = new HashSet<>();
    for (NativeArtifact artifact : nativeIncludes.myArtifacts) {
      for (NativeFile sourceFile : artifact.getSourceFiles()) {
        File workingDirectory = sourceFile.getWorkingDirectory();
        String settingsName = sourceFile.getSettingsName();
        if (settingsSeen.contains(settingsName)) {
          continue;
        }
        settingsSeen.add(settingsName);
        NativeSettings settings = nativeIncludes.findExpectedSettings(settingsName);
        set.addIncludesFromCompilerFlags(settings.getCompilerFlags(), workingDirectory);
      }
    }
    return set;
  }

  /**
   * Decides whether an include file would be contained by this node.
   */
  public static boolean containedInIncludeFolders(@NotNull NativeIncludes includes, @NotNull VirtualFile file) {
    if (!LexicalIncludePaths.hasHeaderExtension(file.getName())) {
      return false;
    }
    IncludeSet includeSet = distinctIncludes(includes);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (File include : includeSet.getIncludesInOrder()) {
      VirtualFile ancestor = fileSystem.findFileByIoFile(include);
      if (ancestor != null && VfsUtilCore.isAncestor(ancestor, file, false)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public PsiDirectory[] getFolders() {
    return PsiDirectory.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<AbstractTreeNode> result = new ArrayList<>();
    Project project = getProject();
    if (project == null) {
      return result;
    }
    IncludeSet includeSet = distinctIncludes(myDependencyInfo);
    List<SimpleIncludeValue> simpleIncludes = new ArrayList<>();
    for (File includeFolder : includeSet.getIncludesInOrder()) {
      simpleIncludes.add(IncludeResolver
                           .getGlobalResolver(IdeSdks.getInstance().getAndroidNdkPath())
                           .resolve(includeFolder));
    }

    List<IncludeValue> includes = IncludeValues.organize(simpleIncludes);

    for (IncludeValue include : includes) {
      if (include instanceof ShadowingIncludeValue) {
        ShadowingIncludeValue concrete = (ShadowingIncludeValue)include;
        result.addAll(IncludeViewNodes.getIncludeFolderNodesWithShadowing(concrete.getIncludePathsInOrder(),
                                                                          VirtualFiles.convertToVirtualFile(concrete.myExcludes), false,
                                                                          project, getSettings()));
      } else if (include instanceof SimpleIncludeValue) {
        result.add(new SimpleIncludeViewNode((SimpleIncludeValue)include, includeSet, true, getProject(), getSettings()));
      } else if (include instanceof ClassifiedIncludeValue) {
        // Add folders to the list of folders to exclude from the simple path group
        ClassifiedIncludeValue classifiedIncludeValue = (ClassifiedIncludeValue)include;
        result.add(IncludeViewNode.createIncludeView(
          classifiedIncludeValue, includeSet, true, getProject(), getSettings()));
      }
    }
    return result;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return containedInIncludeFolders(myDependencyInfo, file);
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    return -100;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText("includes", REGULAR_ATTRIBUTES);
    presentation.setIcon(AllIcons.Nodes.WebFolder);
  }

  @NotNull
  @Override
  public String toString() {
    return "includes";
  }
}
