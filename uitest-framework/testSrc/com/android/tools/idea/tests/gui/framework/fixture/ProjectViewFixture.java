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

import static com.android.tools.idea.tests.gui.framework.UiTestUtilsKt.fixupWaiting;
import static com.android.tools.idea.tests.gui.framework.UiTestUtilsKt.waitForIdle;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.MouseClickInfo.leftButton;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.navigator.nodes.apk.ApkModuleNode;
import com.android.tools.idea.navigator.nodes.apk.ndk.LibFolderNode;
import com.android.tools.idea.navigator.nodes.apk.ndk.LibraryNode;
import com.android.tools.idea.navigator.nodes.apk.ndk.NdkSourceNode;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.TreeModel;
import org.apache.commons.lang3.StringUtils;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.MouseClickInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ProjectViewFixture extends ToolWindowFixture {

  @NotNull private final IdeFrameFixture ideFrameFixture;

  public ProjectViewFixture(@NotNull IdeFrameFixture ideFrameFixture) {
    super("Project", ideFrameFixture.getProject(), fixupWaiting(ideFrameFixture.robot()));
    this.ideFrameFixture = ideFrameFixture;
  }

  @NotNull
  public PaneFixture selectProjectPane() {
    return selectPane("Project");
  }

  @NotNull
  public PaneFixture selectAndroidPane() {
    return selectPane("Android");
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
    myToolWindow.getComponent().requestFocusInWindow();
    Component projectDropDown = GuiTests.waitUntilFound(myRobot, Matchers.byText(BaseLabel.class, "Project:"));
    myRobot.click(projectDropDown.getParent());

    String paneFullName = "Content name=" + paneName;
    GuiTests.clickPopupMenuItemMatching(s -> s.equals(paneFullName), projectDropDown, myRobot);
  }

  @NotNull
  public String  getCurrentViewId() {
    return ProjectView.getInstance(myProject).getCurrentViewId();
  }

  @NotNull
  private PaneFixture selectPane(String name) {
    activate();
    changePane(name);
    final ProjectView projectView = ProjectView.getInstance(myProject);
    return new PaneFixture(ideFrameFixture, projectView.getCurrentProjectViewPane(), myRobot).waitForTreeToFinishLoading();
  }

  public static class PaneFixture {
    @NotNull private final IdeFrameFixture myIdeFrameFixture;
    @NotNull private final AbstractProjectViewPane myPane;
    @NotNull private final Robot myRobot;
    @NotNull private final JTreeFixture myTree;

    PaneFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull AbstractProjectViewPane pane, @NotNull Robot robot) {
      myIdeFrameFixture = ideFrameFixture;
      myPane = pane;
      myRobot = robot;
      myTree = new JTreeFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byType(ProjectViewTree.class)));
    }

    @NotNull
    private PaneFixture waitForTreeToFinishLoading() {
      return waitForTreeToFinishLoading(5);
    }

    @NotNull
    private PaneFixture waitForTreeToFinishLoading(long secondsToWait) {
      TreeModel model = myTree.target().getModel();
      if (model instanceof AsyncTreeModel) { // otherwise there's nothing to wait for, as the tree loading should be synchronous
        Wait.seconds(secondsToWait).expecting("tree to load").until(() -> !(((AsyncTreeModel) model).isProcessing()));
        waitForIdle();
      }
      return this;
    }

    @NotNull
    public PaneFixture expand() {
      return expand(5);
    }

    @NotNull
    public PaneFixture expand(long secondsToWait) {
      GuiTask.execute(() -> TreeUtil.expandAll(myPane.getTree()));
      waitForTreeToFinishLoading(secondsToWait);
      return this;
    }

    @NotNull
    private AbstractTreeStructure getTreeStructure() {
      final AtomicReference<AbstractTreeStructure> treeStructureRef = new AtomicReference<>();
      Wait.seconds(1).expecting("AbstractTreeStructure to be built").until(() -> GuiQuery.getNonNull(() -> {
        try {
          treeStructureRef.set(myPane.getTreeStructure());
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
    private NodeFixture findApkNode() {
      AbstractTreeStructure treeStructure = getTreeStructure();

      ApkModuleNode apkNode = GuiQuery.getNonNull(() -> {
        for (Object child : treeStructure.getChildElements(treeStructure.getRootElement())) {
          if(child instanceof ApkModuleNode) {
            return (ApkModuleNode)child;
          }
        }
        throw new IllegalStateException("Unable to find 'APK module' node");
      });

      return new NodeFixture(apkNode, treeStructure);
    }

    @NotNull
    private NodeFixture findNativeLibrariesNode(@NotNull NodeFixture parentNode) {
      for (NodeFixture child : parentNode.getChildren()) {
        if (child.myNode instanceof LibFolderNode) {
          return child;
        }
      }
      throw new IllegalStateException("Unable to find the child native library node under given parent node");
    }

    @NotNull
    public NodeFixture findNativeLibraryNodeFor(@NotNull String libraryName) {
      List<NodeFixture> nativeLibs = findNativeLibrariesNode(findApkNode()).getChildren();

      for (NodeFixture child : nativeLibs) {
        if (child.myNode instanceof LibraryNode) {
          String libName = child.myNode.toTestString(null);
          if (libName != null) {
            if (libraryName.equals(libName)) {
              return child;
            }
          }
        }
      }
      throw new IllegalStateException("Unable to find native library node for " + libraryName);
    }

    public void doubleClickPath(@NotNull final String... path) {
      Preconditions.checkArgument(path.length > 0, "You must specify a non empty path");
      myTree.doubleClickPath(StringUtils.join(path, '/'));
    }

    public boolean hasPath(@NotNull final String... path) {
      Preconditions.checkArgument(path.length > 0, "You must specify a non empty path");
      try {
        myTree.node(StringUtils.join(path, '/'));
          return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    }

    public IdeFrameFixture clickPath(@NotNull final String... path) {
      return clickPath(MouseButton.LEFT_BUTTON, path);
    }

    public IdeFrameFixture clickPath(@NotNull MouseButton button, @NotNull final String... path) {
      StringBuilder totalPath = new StringBuilder(path[0]);
      for (int i = 1; i < path.length; i++) {
        myTree.selectPath(totalPath.toString());
        myTree.robot().pressAndReleaseKey(KeyEvent.VK_ADD);
        totalPath.append('/').append(path[i]);
      }
      Wait.seconds(10);
      if (SystemInfo.isMac) {
        MouseClickInfo mouseClickInfo = leftButton().times(1);
        myTree.clickPath(totalPath.toString(), mouseClickInfo);
      } else {
        myTree.clickPath(totalPath.toString(), button);
      }
      return myIdeFrameFixture;
    }

    public IdeFrameFixture deletePath(@NotNull final String... pathSegments) {
      return clickPath(MouseButton.RIGHT_BUTTON, pathSegments)
        .openFromContextualMenu(DeleteDialogFixture::find, "Delete...")
        .unsafeDelete();
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
      final List<NodeFixture> children = new ArrayList<>();
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

    public boolean isSourceFolder() {
      return myNode instanceof PsiDirectoryNode || myNode instanceof NdkSourceNode;
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
