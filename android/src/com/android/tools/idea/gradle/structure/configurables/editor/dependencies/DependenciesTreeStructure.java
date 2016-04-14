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
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.configurables.editor.treeview.ContainerNode;
import com.android.tools.idea.gradle.structure.configurables.editor.treeview.GradleNode;
import com.android.tools.idea.gradle.structure.configurables.editor.treeview.VariantNode;
import com.android.tools.idea.gradle.structure.configurables.model.ArtifactDependencyMergedModel;
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
import static com.android.tools.idea.gradle.structure.configurables.model.Coordinates.convert;
import static com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

class DependenciesTreeStructure extends AbstractTreeStructure {
  @NotNull private final DependenciesPanel myDependenciesPanel;
  @NotNull private final GradleNode myRoot;

  DependenciesTreeStructure(@NotNull DependenciesPanel dependenciesPanel) {
    myDependenciesPanel = dependenciesPanel;
    AndroidProject androidProject = myDependenciesPanel.getModel().getAndroidProject();
    myRoot = new ContainerNode.Variants(androidProject, true);
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
            if (n1.isInBuildFile() == n2.isInBuildFile()) {
              return Collator.getInstance().compare(n1.getName(), n2.getName());
            }
            return n1.isInBuildFile() ? -1 : 1;
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
      if (child != null) {
        addTransitiveDependencies(child, library.getDependencies());
      }
    }
    for (AndroidLibrary library : dependencies.getLibraries()) {
      ArtifactNode child = addIfMatching(node, library, children);
      if (child != null) {
        addTransitiveDependencies(child, library.getLibraryDependencies());
      }
    }
  }

  @Nullable
  private ArtifactNode addIfMatching(@NotNull GradleNode node, @NotNull Library library, @NotNull List<GradleNode> children) {
    ArtifactDependencyMergedModel dependencyModel = myDependenciesPanel.find(library);
    GradleCoordinate coordinate = getGradleCoordinate(library, dependencyModel);
    if (coordinate != null) {
      ArtifactNode child = new ArtifactNode(coordinate, node, dependencyModel);
      children.add(child);
      return child;
    }

    return null;
  }

  private void addTransitiveDependencies(@NotNull ArtifactNode node, @NotNull List<? extends Library> dependencies) {
    List<GradleNode> transitiveNodes = Lists.newArrayList();
    for (Library dependency : dependencies) {
      ArtifactDependencyMergedModel dependencyModel = myDependenciesPanel.find(dependency);
      GradleCoordinate coordinate = getGradleCoordinate(dependency, dependencyModel);
      if (coordinate != null) {
        ArtifactNode child = new ArtifactNode(coordinate, node, myDependenciesPanel.find(dependency));
        transitiveNodes.add(child);
      }
    }
    if (!transitiveNodes.isEmpty()) {
      node.setChildren(transitiveNodes);
    }
  }

  @Nullable
  private static GradleCoordinate getGradleCoordinate(@NotNull Library library, @Nullable ArtifactDependencyMergedModel dependencyModel) {
    GradleCoordinate coordinate = null;
    if (dependencyModel != null) {
      coordinate = dependencyModel.getCoordinate();
    }
    else {
      MavenCoordinates coordinates = library.getResolvedCoordinates();
      if (coordinates != null) {
        coordinate = convert(coordinates);
      }
    }
    return coordinate;
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

  static class ArtifactNode extends GradleNode {
    @NotNull final GradleCoordinate coordinate;
    @Nullable final ArtifactDependencyMergedModel dependencyModel;

    ArtifactNode(@NotNull GradleCoordinate coordinate,
                 @Nullable GradleNode parentDescriptor,
                 @Nullable ArtifactDependencyMergedModel dependencyModel) {
      super(parentDescriptor);
      this.coordinate = coordinate;
      this.dependencyModel = dependencyModel;
      myName = coordinate.getArtifactId() + GRADLE_PATH_SEPARATOR + coordinate.getRevision();
      myClosedIcon = dependencyModel != null ? LIBRARY_ICON : AndroidIcons.ProjectStructure.UnknownLibrary;
      setChildren(NO_CHILDREN);
      setAutoExpand(true);
    }

    boolean isInBuildFile() {
      return dependencyModel != null;
    }

    @Override
    @NotNull
    public Object[] getEqualityObjects() {
      return dependencyModel != null ? new Object[]{dependencyModel} : super.getEqualityObjects();
    }
  }
}
