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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.util.ui.tree.TreeUtil;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JMenuItemFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

public class ProjectViewFixture extends ToolWindowFixture {
  ProjectViewFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Project", project, robot);
  }

  @NotNull
  public PaneFixture selectProjectPane() {
    activate();
    final ProjectView projectView = ProjectView.getInstance(myProject);

    if (!"ProjectView".equals(projectView.getCurrentViewId())) {
      changePane("Project");
    }

    return new PaneFixture(projectView.getCurrentProjectViewPane(), myRobot);
  }

  @NotNull
  public PaneFixture selectAndroidPane() {
    activate();
    final ProjectView projectView = ProjectView.getInstance(myProject);

    if (!"AndroidView".equals(projectView.getCurrentViewId())) {
      changePane("Android");
    }

    return new PaneFixture(projectView.getCurrentProjectViewPane(), myRobot);
  }

  @NotNull
  public LibraryPropertiesDialogFixture showPropertiesForLibrary(@NotNull String libraryName) {
    selectProjectPane();
    new JTreeFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byType(ProjectViewTree.class)))
      .clickPath("External Libraries/" + libraryName, MouseButton.RIGHT_BUTTON);
    new JMenuItemFixture(myRobot, GuiTests.waitUntilShowing(myRobot,  Matchers.byText(JMenuItem.class, "Library Properties..."))).click();
    return LibraryPropertiesDialogFixture.find(myRobot, libraryName, myProject);
  }

  /**
   * Given a list of relative paths, finds if they all belong to the Project.
   * @param paths The list of relative paths with / used as separators
   */
  public void assertFilesExist(@NotNull String... paths) {
    VirtualFile baseDir = myProject.getBaseDir();
    for (String path : paths) {
      VirtualFile file = baseDir.findFileByRelativePath(path);
      assertTrue("File doesn't exist: " + path, file != null && file.exists());
    }
  }

  private void changePane(@NotNull String paneName) {
    Component projectDropDown = GuiTests.waitUntilFound(myRobot, Matchers.byText(BaseLabel.class, "Project:"));

    myRobot.click(projectDropDown.getParent());
    GuiTests.clickPopupMenuItem(paneName, projectDropDown, myRobot);
  }

  public static class PaneFixture {
    @NotNull private final AbstractProjectViewPane myPane;
    @NotNull private final Robot myRobot;
    @NotNull private final JTreeFixture myTree;

    PaneFixture(@NotNull AbstractProjectViewPane pane, @NotNull Robot robot) {
      myPane = pane;
      myRobot = robot;
      myTree = new JTreeFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byType(ProjectViewTree.class)));
    }

    @NotNull
    public PaneFixture expand() {
      GuiTask.execute(() -> TreeUtil.expandAll(myPane.getTree()));
      return this;
    }

    /* Returns {@code true} if the tree root has a child {@link Module} with {@code name}, {@code false} otherwise. */
    public boolean hasModuleRootNode(@NotNull String name) {
      final AbstractTreeStructure treeStructure = getTreeStructure();
      return GuiQuery.getNonNull(
        () -> {
          Object[] childElements = treeStructure.getChildElements(treeStructure.getRootElement());
          for (Object child : childElements) {
            ProjectViewNode childNode = (ProjectViewNode)child;
            Object value = childNode.getValue();
            if (value instanceof Module && ((Module)value).getName().equals(name)) {
              return true;
            }
          }
          return false;
        });
    }

    @NotNull
    private AbstractTreeStructure getTreeStructure() {
      final AtomicReference<AbstractTreeStructure> treeStructureRef = new AtomicReference<>();
      Wait.seconds(1).expecting("AbstractTreeStructure to be built").until(() -> GuiQuery.getNonNull(() -> {
        try {
          treeStructureRef.set(myPane.getTreeBuilder().getTreeStructure());
          return true;
        }
        catch (NullPointerException e) {
          // expected;
        }
        return false;
      }));
      return treeStructureRef.get();
    }

    @NotNull
    public NodeFixture findExternalLibrariesNode() {
      final AbstractTreeStructure treeStructure = getTreeStructure();

      ExternalLibrariesNode node = GuiQuery.getNonNull(() -> {
        for (Object child : treeStructure.getChildElements(treeStructure.getRootElement())) {
          if (child instanceof ExternalLibrariesNode) {
            return (ExternalLibrariesNode)child;
          }
        }
        throw new IllegalStateException("Unable to find 'External Libraries' node");
      });
      return new NodeFixture(node, treeStructure);
    }

    public void clickPath(@NotNull final String... paths) {
      clickPath(MouseButton.LEFT_BUTTON, paths);
    }

    public void clickPath(@NotNull MouseButton button, @NotNull final String... paths) {
      Wait.seconds(5).expecting("Tree to load").until(() -> !String.valueOf(myTree.target().getCellRenderer()).equals("loading..."));
      StringBuilder totalPath = new StringBuilder();
      for (String node : paths) {
        totalPath.append(node);
        myTree.expandPath(totalPath.toString());
        totalPath.append('/');
      }
      myTree.clickPath(totalPath.toString(), button);
    }
  }

  public static class NodeFixture {
    @NotNull private final ProjectViewNode<?> myNode;
    @NotNull private final AbstractTreeStructure myTreeStructure;

    NodeFixture(@NotNull ProjectViewNode<?> node, @NotNull AbstractTreeStructure treeStructure) {
      myNode = node;
      myTreeStructure = treeStructure;
    }

    @NotNull
    public List<NodeFixture> getChildren() {
      final List<NodeFixture> children = Lists.newArrayList();
      GuiTask.execute(
        () -> {
          for (Object child : myTreeStructure.getChildElements(myNode)) {
            if (child instanceof ProjectViewNode) {
              children.add(new NodeFixture((ProjectViewNode<?>)child, myTreeStructure));
            }
          }
        });
      return children;
    }

    public boolean isJdk() {
      if (myNode instanceof NamedLibraryElementNode) {
        LibraryOrSdkOrderEntry orderEntry = ((NamedLibraryElementNode)myNode).getValue().getOrderEntry();
        if (orderEntry instanceof JdkOrderEntry) {
          Sdk sdk = ((JdkOrderEntry)orderEntry).getJdk();
          return sdk.getSdkType() instanceof JavaSdk;
        }
      }
      return false;
    }

    @NotNull
    public NodeFixture requireDirectory(@NotNull String name) {
      assertThat(myNode).isInstanceOf(PsiDirectoryNode.class);
      assertThat(myNode.getVirtualFile().getName()).isEqualTo(name);
      return this;
    }

    @Override
    public String toString() {
      return Strings.nullToEmpty(myNode.getName());
    }
  }
}
