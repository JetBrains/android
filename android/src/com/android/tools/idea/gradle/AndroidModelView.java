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
import com.android.builder.model.*;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.ProxyUtil;
import com.android.tools.idea.gradle.util.ui.ToolWindowAlikePanel;
import com.google.common.collect.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
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
import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.tools.idea.gradle.util.ProxyUtil.getAndroidModelProxyValues;
import static com.android.tools.idea.gradle.util.ProxyUtil.isAndroidModelProxyObject;

/**
 * "Android Model" tool window to visualize the Android-Gradle model data.
 */
public class AndroidModelView {
  private static final Logger LOG = Logger.getInstance(AndroidModelView.class);

  private static final ImmutableMultimap<String, String> SOURCE_PROVIDERS_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("SourceProviders", "SourceProvider", "ExtraSourceProviders").build();
  private static final ImmutableMultimap<String, String> SDK_VERSIONS_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("SdkVersions", "MinSdkVersion", "MaxSdkVersion", "TargetSdkVersion").build();
  private static final ImmutableMultimap<String, String> ARTIFACTS_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("Artifacts", "MainArtifact", "ExtraAndroidArtifacts", "ExtraJavaArtifacts").build();
  private static final ImmutableMultimap<String, String> JNI_DIRECTORIES_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("JniDirectories", "CDirectories").build();

  @NotNull private final Project myProject;
  @NotNull private final Tree myTree;

  public AndroidModelView(@NotNull Project project) {
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

  public static AndroidModelView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidModelView.class);
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
    }
    else {
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Loading ...")));
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(myProject.getName());
        String projectPath = myProject.getBasePath();
        for (Module module : ModuleManager.getInstance(myProject).getModules()) {
          AndroidGradleModel androidModel = AndroidGradleModel.get(module);
          if (androidModel != null) {
            DefaultMutableTreeNode moduleNode = new ModuleNodeBuilder(module.getName(), androidModel, projectPath).getNode();
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
  static class ModuleNodeBuilder {
    @NotNull private final String myModuleName;
    @NotNull private final AndroidGradleModel myAndroidModel;
    @Nullable private final String myProjectPath;

    private List<DefaultMutableTreeNode> artifactNodes = Lists.newArrayList();

    public ModuleNodeBuilder(@NotNull String moduleName, @NotNull AndroidGradleModel androidModel, @Nullable String projectPath) {
      myModuleName = moduleName;
      myAndroidModel = androidModel;

      if (projectPath != null && !projectPath.endsWith(File.separator)) {
        projectPath += File.separator;
      }
      myProjectPath = projectPath;
    }

    public DefaultMutableTreeNode getNode() {
      DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(myModuleName);
      AndroidProject androidProject = myAndroidModel.waitForAndGetProxyAndroidProject();

      artifactNodes.clear();
      addProxyObject(moduleNode, androidProject, false, ImmutableList.of("SyncIssues", "UnresolvedDependencies", "ApiVersion"));
      for (DefaultMutableTreeNode artifactNode : artifactNodes) {
        addSources(artifactNode);
      }
      return moduleNode;
    }

    @VisibleForTesting
    void addProxyObject(@NotNull DefaultMutableTreeNode node, @NotNull Object obj) {
      addProxyObject(node, obj, false);
    }

    private void addProxyObject(@NotNull DefaultMutableTreeNode node, @NotNull Object obj, boolean useDerivedNodeName) {
      addProxyObject(node, obj, useDerivedNodeName, ImmutableList.<String>of());
    }

    private void addProxyObject(@NotNull DefaultMutableTreeNode node,
                                @NotNull Object obj,
                                boolean useDerivedNodeName,
                                @NotNull Collection<String> skipProperties) {
      addProxyObject(node, obj, useDerivedNodeName, skipProperties, ImmutableList.<String>of(), ImmutableMultimap.<String, String>of());
    }

    private void addProxyObject(@NotNull DefaultMutableTreeNode node,
                                @NotNull Object obj,
                                boolean useDerivedNodeName,
                                @NotNull Collection<String> inlineProperties,
                                @NotNull Multimap<String, String> groupProperties) {
      addProxyObject(node, obj, useDerivedNodeName, ImmutableList.<String>of(), inlineProperties, groupProperties);
    }

    private void addProxyObject(@NotNull DefaultMutableTreeNode node,
                                @NotNull Object obj,
                                boolean useDerivedNodeName,
                                @NotNull Collection<String> skipProperties,
                                @NotNull Collection<String> inlineProperties,
                                @NotNull Multimap<String, String> groupProperties) {
      assert isAndroidModelProxyObject(obj);
      Map<String, DefaultMutableTreeNode> groupPropertyNodes = Maps.newHashMap();

      String name = null;
      for (Map.Entry<String, Object> entry : getAndroidModelProxyValues(obj).entrySet()) {
        String property = entry.getKey(); // method name in canonical form.
        property = property.substring(0, property.lastIndexOf('('));
        property = property.substring(property.lastIndexOf('.') + 1, property.length());
        if (property.startsWith("get")) {
          property = property.substring(3);
        }
        if (skipProperties.contains(property)) {
          continue;
        }

        boolean useDerivedNameValue = false;
        boolean addToParentNode = false;

        Object value = entry.getValue();
        if (value != null && property.equals("Name")) {
          name = value.toString();
        }
        if (inlineProperties.contains(property)) {
          addToParentNode = true;
          useDerivedNameValue = useDerivedNodeName;
        }

        DefaultMutableTreeNode parentNode = node;
        if (groupProperties.values().contains(property)) {
          for (Map.Entry<String, String> group : groupProperties.entries()) {
            if (group.getValue().equals(property)) {
              String groupName = group.getKey();
              DefaultMutableTreeNode groupNode = groupPropertyNodes.get(groupName);
              if (groupNode == null) {
                groupNode = new DefaultMutableTreeNode(groupName);
                groupPropertyNodes.put(groupName, groupNode);
              }
              parentNode = groupNode;
              useDerivedNameValue = true;
              break;
            }
          }
        }

        addPropertyNode(parentNode, property, value, useDerivedNameValue, addToParentNode);
      }

      for (DefaultMutableTreeNode groupNode : groupPropertyNodes.values()) {
        addToNode(node, groupNode);
      }

      if (useDerivedNodeName && name != null) {
        node.setUserObject(name);
      }
    }

    private void addPropertyNode(@NotNull DefaultMutableTreeNode node,
                                 @NotNull String property,
                                 @Nullable Object value,
                                 boolean useDerivedNodeName,
                                 boolean addToParentNode) {
      DefaultMutableTreeNode propertyNode = addToParentNode ? node : new DefaultMutableTreeNode(property);

      if (value != null && (isAndroidModelProxyObject(value))) {
        if (!customizeProxyObject(propertyNode, value, useDerivedNodeName)) {
          addProxyObject(propertyNode, value, useDerivedNodeName);
        }
      }
      else if (value instanceof Collection && (!((Collection)value).isEmpty() || addToParentNode)) {
        for (Object obj : (Collection)value) {
          addPropertyNode(propertyNode, "", obj, true, false);
        }
      }
      else if (value instanceof Map && (!((Map)value).isEmpty() || addToParentNode)) {
        Map map = (Map)value;
        for (Object key : map.keySet()) {
          addPropertyNode(propertyNode, key.toString(), map.get(key), false, false);
        }
      }
      else if (value instanceof ProxyUtil.InvocationErrorValue) {
        Throwable exception = ((ProxyUtil.InvocationErrorValue)value).exception;
        propertyNode.setUserObject(getNodeValue(property, "Error: " + exception.getClass().getName()));
      }
      else {
        propertyNode.setUserObject(getNodeValue(property, getStringForValue(value)));
      }

      if (!addToParentNode) {
        addToNode(node, propertyNode);
      }
    }

    private boolean customizeProxyObject(@NotNull DefaultMutableTreeNode propertyNode, @NotNull Object value, boolean useDerivedName) {
      assert isAndroidModelProxyObject(value);
      if (value instanceof ProductFlavorContainer) {
        addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("ProductFlavor", "ExtraSourceProviders"),
                       SOURCE_PROVIDERS_GROUP);
      }
      else if (value instanceof BuildTypeContainer) {
        addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("BuildType", "ExtraSourceProviders"), SOURCE_PROVIDERS_GROUP);
      }
      else if (value instanceof SourceProviderContainer) {
        addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("SourceProvider"), ImmutableMultimap.<String, String>of());
      }
      else if (value instanceof SourceProvider) {
        addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("CppDirectories"), ImmutableList.of("CDirectories"),
                       JNI_DIRECTORIES_GROUP);
      }
      else if (value instanceof ProductFlavor) {
        addProxyObject(propertyNode, value, useDerivedName, ImmutableList.<String>of(), SDK_VERSIONS_GROUP);
      }
      else if (value instanceof Variant) {
        addProxyObject(propertyNode, value, useDerivedName, ImmutableList.<String>of(),
                       ImmutableList.of("ExtraAndroidArtifacts", "ExtraJavaArtifacts"), ARTIFACTS_GROUP);
      }
      else if (value instanceof BaseArtifact) {
        addProxyObject(propertyNode, value, useDerivedName);
        artifactNodes.add(propertyNode);
      }
      else {
        return false;
      }
      return true;
    }

    private void addSources(@NotNull DefaultMutableTreeNode artifactNode) {
      String artifactName = artifactNode.getUserObject().toString();
      String variantName = ((DefaultMutableTreeNode)(artifactNode.getParent().getParent())).getUserObject().toString();

      List<SourceProvider> sourceProviders;
      if (artifactName.equals(ARTIFACT_MAIN)) {
        sourceProviders = myAndroidModel.getMainSourceProviders(variantName);
      }
      else {
        sourceProviders = myAndroidModel.getTestSourceProviders(variantName, artifactName);
      }

      DefaultMutableTreeNode sourcesNode = new DefaultMutableTreeNode("Sources");
      addSourceProviders(sourcesNode, sourceProviders);
      addToNode(artifactNode, sourcesNode);
    }

    private void addSourceProviders(@NotNull DefaultMutableTreeNode sourcesNode, @NotNull List<SourceProvider> sourceProviders) {
      DefaultMutableTreeNode manifestFilesNode = new DefaultMutableTreeNode("ManifestFiles");
      DefaultMutableTreeNode javaDirectoriesNode = new DefaultMutableTreeNode("JavaDirectories");
      DefaultMutableTreeNode jniDirectoriesNode = new DefaultMutableTreeNode("JniDirectories");
      DefaultMutableTreeNode jniLibsDirectoriesNode = new DefaultMutableTreeNode("JniLibsDirectories");
      DefaultMutableTreeNode resDirectoriesNode = new DefaultMutableTreeNode("ResDirectories");
      DefaultMutableTreeNode aidlDirectoriesNode = new DefaultMutableTreeNode("AidlDirectories");
      DefaultMutableTreeNode resourcesDirectoriesNode = new DefaultMutableTreeNode("ResourcesDirectories");
      DefaultMutableTreeNode assetsDirectoriesNode = new DefaultMutableTreeNode("AssetsDirectories");
      DefaultMutableTreeNode renderscriptDirectoriesNode = new DefaultMutableTreeNode("RenderscriptDirectories");

      for (SourceProvider sourceProvider : sourceProviders) {
        addFiles(manifestFilesNode, ImmutableList.of(sourceProvider.getManifestFile()));
        addFiles(javaDirectoriesNode, sourceProvider.getJavaDirectories());
        addFiles(jniDirectoriesNode, sourceProvider.getCDirectories());
        addFiles(jniLibsDirectoriesNode, sourceProvider.getJniLibsDirectories());
        addFiles(resDirectoriesNode, sourceProvider.getResDirectories());
        addFiles(aidlDirectoriesNode, sourceProvider.getAidlDirectories());
        addFiles(resourcesDirectoriesNode, sourceProvider.getResourcesDirectories());
        addFiles(assetsDirectoriesNode, sourceProvider.getAssetsDirectories());
        addFiles(renderscriptDirectoriesNode, sourceProvider.getRenderscriptDirectories());
      }

      sourcesNode.add(manifestFilesNode);
      sourcesNode.add(javaDirectoriesNode);
      sourcesNode.add(jniDirectoriesNode);
      sourcesNode.add(jniLibsDirectoriesNode);
      sourcesNode.add(resDirectoriesNode);
      sourcesNode.add(aidlDirectoriesNode);
      sourcesNode.add(resourcesDirectoriesNode);
      sourcesNode.add(assetsDirectoriesNode);
      sourcesNode.add(renderscriptDirectoriesNode);
    }

    private void addFiles(@NotNull DefaultMutableTreeNode node, Collection<File> files) {
      for (File file : files) {
        node.add(new DefaultMutableTreeNode(getStringForValue(file)));
      }
    }

    @NotNull
    private String getStringForValue(@Nullable Object value) {
      if (value != null && value instanceof File) {
        String filePath = ((File)value).getPath();
        if (myProjectPath != null && filePath.startsWith(myProjectPath)) {
          return filePath.substring(myProjectPath.length());
        }
      }
      return value == null ? "null" : value.toString();
    }

    @NotNull
    private static String getNodeValue(@NotNull String property, @NotNull String value) {
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
}
