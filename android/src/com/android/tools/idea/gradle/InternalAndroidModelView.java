/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.ProxyUtil;
import com.android.tools.idea.gradle.util.ui.ToolWindowAlikePanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.util.ProxyUtil.getAndroidModelProxyValues;
import static com.android.tools.idea.gradle.util.ProxyUtil.isAndroidModelProxyObject;

/**
 * "Android Model (Internal)" tool window to visualize the Android-Gradle model data. This is an internal only view and visible only when
 * the {@code idea.is.internal} property is set to true.
 */
public class InternalAndroidModelView {
  @NotNull
  private final Project myProject;
  @NotNull
  private final Tree myTree;

  public InternalAndroidModelView(@NotNull Project project) {
    myProject = project;
    myTree = new Tree();
    GradleSyncState.subscribe(myProject, new GradleSyncListener.Adapter() {
      @Override
      public void syncStarted(@NotNull Project project) {
        updateContents();
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        updateContents();
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        updateContents();
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        updateContents();
      }
    });
  }

  public static InternalAndroidModelView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, InternalAndroidModelView.class);
  }

  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    JPanel toolWindowPanel = ToolWindowAlikePanel.createTreePanel(myProject.getName(), myTree);
    Content content = contentFactory.createContent(toolWindowPanel, "", false);
    toolWindow.getContentManager().addContent(content);
    updateContents();
  }

  private void updateContents() {
    myTree.setRootVisible(true);
    if (GradleSyncState.getInstance(myProject).isSyncInProgress()) {
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Gradle project sync in progress ...")));
      return;
    } else {
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Loading ...")));
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(myProject.getName());
        for (Module module : ModuleManager.getInstance(myProject).getModules()) {
          AndroidGradleModel androidModel = AndroidGradleModel.get(module);
          if (androidModel != null) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(module.getName());
            AndroidProject androidProject = androidModel.waitForAndGetProxyAndroidProject();
            addProxyObject(moduleNode, androidProject);
            rootNode.add(moduleNode);
          }
        }

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
            renderer.setOpenIcon(AllIcons.Nodes.NewFolder);
            renderer.setClosedIcon(AllIcons.Nodes.NewFolder);
            renderer.setLeafIcon(AllIcons.ObjectBrowser.ShowModules);
            myTree.setCellRenderer(renderer);

            DefaultTreeModel model = new DefaultTreeModel(rootNode);
            myTree.setRootVisible(false);
            myTree.setModel(model);
          }
        });
      }
    });
  }

  @VisibleForTesting
  void addProxyObject(@NotNull DefaultMutableTreeNode node, @NotNull Object obj) {
    addProxyObject(node, obj, false);
  }

  private void addProxyObject(@NotNull DefaultMutableTreeNode node, @NotNull Object obj, boolean useDerivedNodeName) {
    assert isAndroidModelProxyObject(obj);

    String name = null;
    for (Map.Entry<String, Object> entry : getAndroidModelProxyValues(obj).entrySet()) {
      String property = entry.getKey(); // method name in canonical form.
      property = property.substring(0, property.lastIndexOf('('));
      property = property.substring(property.lastIndexOf('.') + 1, property.length());

      if (property.startsWith("get")) {
        property = property.substring(3);
      }
      Object value = entry.getValue();
      if (value != null && property.equals("Name")) {
        name = value.toString();
      }
      addPropertyNode(node, property, value);
    }

    if (useDerivedNodeName && name != null) {
      node.setUserObject(name);
    }
  }

  private void addPropertyNode(@NotNull DefaultMutableTreeNode node, @NotNull String property, @Nullable Object value) {
    DefaultMutableTreeNode propertyNode = new DefaultMutableTreeNode(property);

    if (value != null && (isAndroidModelProxyObject(value))) {
      addProxyObject(propertyNode, value, property.isEmpty());
    }
    else if (value instanceof Collection && !((Collection)value).isEmpty()) {
      for (Object obj : (Collection)value) {
        addPropertyNode(propertyNode, "", obj);
      }
    }
    else if (value instanceof Map && !((Map)value).isEmpty()) {
      Map map = (Map) value;
      for (Object key : map.keySet()) {
        addPropertyNode(propertyNode, key.toString(), map.get(key));
      }
    }
    else if (value instanceof ProxyUtil.InvocationErrorValue) {
      Throwable exception = ((ProxyUtil.InvocationErrorValue)value).exception;
      propertyNode.setUserObject(getNodeValue(property, "Error: " + exception.getClass().getName()));
    }
    else {
      propertyNode.setUserObject(getNodeValue(property, getStringForValue(value)));
    }

    addToNode(node, propertyNode);
  }

  @NotNull
  private String getStringForValue(@Nullable Object value) {
    if (value != null && value instanceof File) {
      String filePath = ((File)value).getPath();
      String basePath = FileUtil.toSystemDependentName(myProject.getBasePath());
      if (basePath != null) {
        if (!basePath.endsWith(File.separator)) {
          basePath += File.separator;
        }
        if (filePath.startsWith(basePath)) {
          return filePath.substring(basePath.length());
        }
      }
    }
    return value == null ? "null" : value.toString();
  }

  @NotNull
  private static String getNodeValue(@NotNull String property, @Nullable String value) {
    return property.isEmpty() ? value : property + " -> " + value;
  }

  // Inserts the new child node at appropriate place in the parent.
  private static void addToNode(@NotNull DefaultMutableTreeNode parent, @NotNull DefaultMutableTreeNode newChild) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      DefaultMutableTreeNode existingChild = (DefaultMutableTreeNode)parent.getChildAt(i);
      if (compareTo(existingChild, newChild) >= 0) {
        parent.insert(newChild, i);
        return;
      }
    }
    parent.add(newChild);
  }

  private static int compareTo(@NotNull DefaultMutableTreeNode node1, @NotNull DefaultMutableTreeNode node2) {
    if (node1.isLeaf() && !node2.isLeaf()) {
      return -1;
    }
    else if (!node1.isLeaf() && node2.isLeaf()) {
      return 1;
    }
    else {
      return node1.getUserObject().toString().compareTo(node2.getUserObject().toString());
    }
  }
}
