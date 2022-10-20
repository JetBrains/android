/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.projectView;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EventListener;
import java.util.List;

/**
 * Changes the structure of the JDK node (under "External Libraries" in the Project View
 * <ul>
 * <li>Shows only the "rt.jar" node</li>
 * <li>Shows only the "java" and "javax" packages in the "rt.jar" node</li>
 * </ul>
 */
public class AndroidTreeStructureProvider implements TreeStructureProvider {
  private final EventDispatcher<ChangeListener> myEventDispatcher =  EventDispatcher.create(ChangeListener.class);

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> modify(
    @NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    Project project = parent.getProject();
    if (project != null && ProjectSystemUtil.requiresAndroidModel(project)) {
      if (parent instanceof NamedLibraryElementNode) {
        NamedLibraryElement value = ((NamedLibraryElementNode)parent).getValue();
        LibraryOrSdkOrderEntry orderEntry = value.getOrderEntry();
        if (orderEntry instanceof JdkOrderEntry) {
          Sdk sdk = ((JdkOrderEntry)orderEntry).getJdk();
          if (sdk != null && sdk.getSdkType() instanceof JavaSdk) {
            List<AbstractTreeNode<?>> newChildren = new ArrayList<>();
            for (AbstractTreeNode child : children) {
              if (isRtJar(child)) {
                newChildren.add(child);
              }
            }
            if (!newChildren.isEmpty()) {
              myEventDispatcher.getMulticaster().nodeChanged(parent, newChildren);
              return newChildren;
            }
          }
        }
      }
      else if (isRtJar(parent)) {
        List<AbstractTreeNode<?>> newChildren = new ArrayList<>();
        for (AbstractTreeNode child : children) {
          if (child instanceof PsiDirectoryNode) {
            VirtualFile file = ((PsiDirectoryNode)child).getVirtualFile();
            if (file != null && ("java".equals(file.getName()) || "javax".equals(file.getName()))) {
              newChildren.add(child);
            }
          }
        }
        if (!newChildren.isEmpty()) {
          myEventDispatcher.getMulticaster().nodeChanged(parent, newChildren);
          return newChildren;
        }
      }
    }
    return children;
  }

  private static boolean isRtJar(@NotNull AbstractTreeNode node) {
    if (node instanceof PsiDirectoryNode) {
      VirtualFile file = ((PsiDirectoryNode)node).getVirtualFile();
      return file != null && "rt.jar".equals(file.getName());
    }
    return false;
  }

  @VisibleForTesting
  public void addChangeListener(@NotNull ChangeListener changeListener) {
    if (GuiTestingService.getInstance().isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()) {
      myEventDispatcher.addListener(changeListener);
      return;
    }
    throw new UnsupportedOperationException("'addChangeListener' should only be used in tests");
  }

  @VisibleForTesting
  public interface ChangeListener extends EventListener {
    void nodeChanged(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode<?>> newChildren);
  }
}
