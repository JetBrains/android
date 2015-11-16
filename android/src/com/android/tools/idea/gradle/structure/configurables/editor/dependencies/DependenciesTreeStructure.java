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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.structure.configurables.editor.treeview.ContainerNode;
import com.android.tools.idea.gradle.structure.configurables.editor.treeview.GradleNode;
import com.android.tools.idea.gradle.structure.configurables.editor.treeview.VariantNode;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

class DependenciesTreeStructure extends AbstractTreeStructure {
  @NotNull private final DependenciesPanel myDependenciesPanel;
  @NotNull private final GradleNode myRoot;

  DependenciesTreeStructure(@NotNull DependenciesPanel dependenciesPanel) {
    myDependenciesPanel = dependenciesPanel;
    AndroidProject androidProject = myDependenciesPanel.getModel().getAndroidProject();
    myRoot = new ContainerNode.Variants(androidProject);
    myRoot.setAutoExpand(true);
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }

  @Override
  public Object[] getChildElements(Object element) {
    if (element instanceof GradleNode) {
      GradleNode node = (GradleNode)element;
      GradleNode[] children = node.getChildren();
      if (children != null) {
        return children;
      }

      if (node instanceof VariantNode) {
        setChildren((VariantNode)node);
        return node.getChildren();
      }
    }
    return EMPTY_OBJECT_ARRAY;
  }

  private void setChildren(@NotNull VariantNode node) {
    Variant variant = node.getVariant();
    List<GradleNode> children = Lists.newArrayList();
    collectDependencies(node, variant.getMainArtifact(), children);
    for (AndroidArtifact artifact : variant.getExtraAndroidArtifacts()) {
      collectDependencies(node, artifact, children);
    }
    for (JavaArtifact artifact : variant.getExtraJavaArtifacts()) {
      collectDependencies(node, artifact, children);
    }
    Collections.sort(children, new Comparator<GradleNode>() {
      @Override
      public int compare(GradleNode o1, GradleNode o2) {
        // Push unknown dependencies to the bottom of the list.
        if (o1 instanceof ArtifactNode) {
          ArtifactNode n1 = (ArtifactNode)o1;
          if (o2 instanceof ArtifactNode) {
            ArtifactNode n2 = (ArtifactNode)o2;
            if (n1.unknown == n2.unknown) {
              return Collator.getInstance().compare(n1.getName(), n2.getName());
            }
            return n1.unknown ? 1 : -1;
          }
        }
        return -1;
      }
    });
    node.setChildren(children);
  }

  private void collectDependencies(@NotNull VariantNode node, @NotNull BaseArtifact artifact, @NotNull List<GradleNode> children) {
    Dependencies dependencies = artifact.getDependencies();
    for (JavaLibrary library : dependencies.getJavaLibraries()) {
      ArtifactNode child = addIfMatching(node, library, children);
      addTransitiveDependencies(child, library.getDependencies());
    }
    for (AndroidLibrary library : dependencies.getLibraries()) {
      ArtifactNode child = addIfMatching(node, library, children);
      addTransitiveDependencies(child, library.getLibraryDependencies());
    }
  }

  @NotNull
  private ArtifactNode addIfMatching(@NotNull GradleNode node, @NotNull Library library, @NotNull List<GradleNode> children) {
    ArtifactNode child = new ArtifactNode(library, node);
    children.add(child);
    if (!myDependenciesPanel.contains(library)) {
      child.showAsUnknown();
    }
    return child;
  }

  private static void addTransitiveDependencies(@NotNull ArtifactNode node, @NotNull List<? extends Library> dependencies) {
    List<GradleNode> transitive = Lists.newArrayList();
    for (Library dependency : dependencies) {
      transitive.add(new ArtifactNode(dependency, node));
    }
    if (!transitive.isEmpty()) {
      node.setChildren(transitive);
    }
  }

  @Override
  @Nullable
  public Object getParentElement(Object element) {
    if (element instanceof GradleNode) {
      GradleNode node = (GradleNode)element;
      return node.getParentDescriptor();
    }
    return null;
  }

  @Override
  @NotNull
  public GradleNode createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element instanceof GradleNode) {
      return (GradleNode)element;
    }
    throw new IllegalArgumentException("Failed to find a node descriptor for " + element);
  }

  @Override
  public void commit() {

  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public boolean isToBuildChildrenInBackground(Object element) {
    return true;
  }

  private static class ArtifactNode extends GradleNode {
    boolean unknown;

    protected ArtifactNode(@NotNull Library library, @Nullable GradleNode parentDescriptor) {
      super(parentDescriptor);
      MavenCoordinates coordinates = library.getResolvedCoordinates();
      myName = coordinates.getArtifactId() + GRADLE_PATH_SEPARATOR + coordinates.getVersion();
      myClosedIcon = LIBRARY_ICON;
      setChildren(NO_CHILDREN);
    }

    void showAsUnknown() {
      unknown = true;
      myClosedIcon = AndroidIcons.ProjectStructure.UnknownLibrary;
    }
  }
}
