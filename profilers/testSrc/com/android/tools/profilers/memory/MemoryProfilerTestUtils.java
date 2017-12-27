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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.ClassSet;
import com.android.tools.profilers.memory.adapters.ClassifierSet;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MemoryProfilerTestUtils {
  @NotNull
  public static ClassSet findChildClassSetWithName(@NotNull ClassifierSet.Classifier classifier, @NotNull String className) {
    List<ClassSet> classSets = classifier.getClassifierSets().stream()
      .filter(
        classifierSet -> classifierSet instanceof ClassSet && className.equals(((ClassSet)classifierSet).getClassEntry().getClassName()))
      .map(classSet -> (ClassSet)classSet).collect(Collectors.toList());
    assertEquals(1, classSets.size());
    return classSets.get(0);
  }

  @NotNull
  public static ClassSet findChildClassSetWithName(@NotNull ClassifierSet classifierSets, @NotNull String className) {
    List<ClassSet> classSets = classifierSets.getChildrenClassifierSets().stream()
      .filter(classifier -> classifier instanceof ClassSet && className.equals(((ClassSet)classifier).getClassEntry().getClassName()))
      .map(classifierSet -> (ClassSet)classifierSet).collect(Collectors.toList());
    assertEquals(1, classSets.size());
    return classSets.get(0);
  }

  @NotNull
  public static ClassSet findDescendantClassSetNodeWithInstance(@NotNull ClassifierSet root, @NotNull InstanceObject instanceObject) {
    ClassifierSet classifierSet = root.findContainingClassifierSet(instanceObject);
    assertTrue(classifierSet instanceof ClassSet);
    return (ClassSet)classifierSet;
  }

  @NotNull
  public static MemoryObjectTreeNode<ClassSet> findChildClassSetNodeWithClassName(@NotNull MemoryObjectTreeNode<? extends ClassifierSet> root,
                                                                                  @NotNull String className) {
    //noinspection unchecked
    List<MemoryObjectTreeNode<ClassSet>> nodes = root.getChildren().stream()
      .filter(child -> child.getAdapter() instanceof ClassSet &&
                       className.equals(((ClassSet)child.getAdapter()).getClassEntry().getClassName()))
      .map(child -> (MemoryObjectTreeNode<ClassSet>)child).collect(Collectors.toList());
    assertEquals(1, nodes.size());
    return nodes.get(0);
  }

  @NotNull
  public static MemoryObjectTreeNode<? extends ClassifierSet> findChildWithName(@NotNull MemoryObjectTreeNode<? extends ClassifierSet> root,
                                                                                @NotNull String name) {
    List<MemoryObjectTreeNode<? extends ClassifierSet>> nodes = root.getChildren().stream()
      .filter(child -> name.equals(child.getAdapter().getName())).collect(Collectors.toList());
    assertEquals(1, nodes.size());
    return nodes.get(0);
  }

  @NotNull
  public static MemoryObjectTreeNode<? extends MemoryObject> findChildWithPredicate(@NotNull MemoryObjectTreeNode<? extends MemoryObject> root,
                                                                                    @NotNull Function<MemoryObject, Boolean> predicate) {
    List<MemoryObjectTreeNode<? extends MemoryObject>> nodes = root.getChildren().stream()
      .filter(child -> predicate.apply(child.getAdapter())).collect(Collectors.toList());
    assertEquals(1, nodes.size());
    return nodes.get(0);
  }

  @NotNull
  public static MemoryObjectTreeNode<ClassifierSet> getRootClassifierSet(@Nullable JTree tree) {
    assertNotNull(tree);
    Object root = tree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)root).getAdapter() instanceof ClassifierSet);
    //noinspection unchecked
    return (MemoryObjectTreeNode<ClassifierSet>)root;
  }

  public static void verifyNode(@NotNull MemoryObjectTreeNode<? extends ClassifierSet> node,
                                int childNodeCount,
                                int count,
                                int shallowSize,
                                long retainedSize) {
    assertEquals(childNodeCount, node.getChildCount());
    assertEquals(count, node.getAdapter().getAllocatedCount());
    assertEquals(shallowSize, node.getAdapter().getTotalShallowSize());
    assertEquals(retainedSize, node.getAdapter().getTotalRetainedSize());
  }
}
