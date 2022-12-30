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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

public final class IncludeViewTests {

  /*
  Get the IncludesViewNode for the given nativeIncludes setup
   */
  static List<? extends AbstractTreeNode<?>> getChildNodesForIncludes(@NotNull Project project, @NotNull NativeIncludes nativeIncludes) {
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    IncludesViewNode includesViewNode = new IncludesViewNode(project, nativeIncludes, settings);
    return Lists.newArrayList(includesViewNode.getChildren());
  }

  static <T extends AbstractTreeNode> List<T> getChildNodesForIncludes(@Nullable Project project, @NotNull NativeIncludes nativeIncludes, @NotNull Class<T> clazz) {
    ViewSettings settings = Mockito.mock(ViewSettings.class);
    IncludesViewNode includesViewNode = new IncludesViewNode(project, nativeIncludes, settings);
    return Lists.newArrayList(getChildrenOfType(includesViewNode.getChildren(), clazz));
  }

  /*
  Given a set of nodes, return all of the children that match the given type
   */
  static <T extends AbstractTreeNode> List<T> getChildrenOfType(@NotNull Collection<? extends AbstractTreeNode<?>> parents, @NotNull Class<T> clazz) {
    List<T> children = new ArrayList<>();
    appendChildrenOfType(children, parents, clazz);
    return children;
  }

  /*
  Append children of parent matching type. Search is depth-first so that the caller can rely on ordering.
   */
  private static <T extends AbstractTreeNode<?>> void appendChildrenOfType(@NotNull Collection<T> appendTo,
                                                                           @NotNull Collection<? extends AbstractTreeNode<?>> parents,
                                                                           @NotNull Class<T> clazz) {
    for (AbstractTreeNode<?> parent : parents) {
      Collection<? extends AbstractTreeNode<?>> children = parent.getChildren();
      for (AbstractTreeNode<?> child : children) {
        if (clazz.isAssignableFrom(child.getClass())) {
          appendTo.add((T)child);
        }
        appendChildrenOfType(appendTo, Lists.newArrayList(child), clazz);
      }
    }
  }

  static void assertContainsAllFilesAsChildren(@NotNull Collection<? extends AbstractTreeNode<?>> parents, @NotNull Collection<File> children) {
    for (File child : children) {
      boolean foundChild = false;
      for (AbstractTreeNode parent : parents) {
        ProjectViewNode viewNode = (ProjectViewNode)parent;
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        VirtualFile virtualFile = fileSystem.findFileByIoFile(child);
        if (virtualFile != null) {
          if (viewNode.contains(virtualFile)) {
            foundChild = true;
          }
        }
      }
      assertThat(foundChild).named(String.format("%s not found", child)).isTrue();
    }
  }

  static void assertDoesNotContainAnyFilesAsChildren(@NotNull Collection<? extends AbstractTreeNode<?>> parents, @NotNull Collection<File> children) {
    for (File child : children) {
      for (AbstractTreeNode<?> parent : parents) {
        ProjectViewNode viewNode = (ProjectViewNode)parent;
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        VirtualFile virtualFile = fileSystem.findFileByIoFile(child);
        if (virtualFile != null) {
          if (viewNode.contains(virtualFile)) {
            assertThat(false).named(String.format("%s was unexpectedly found", child)).isTrue();
          }
        }
      }
    }
  }
}
