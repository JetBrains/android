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

import com.android.tools.profilers.memory.adapters.NamespaceObject;
import com.intellij.util.Function;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A simple class that emulates a tree structure, but contains additional tracking data to allow for node lookup by name via a hashmap.
 */
class ClassificationIndex<T> {
  @NotNull
  private final MemoryObjectTreeNode<NamespaceObject> myTreeNode;
  @NotNull
  private final Function<T, MemoryObjectTreeNode<NamespaceObject>> myNodeSupplier;
  @NotNull
  private final Map<T, ClassificationIndex<T>> myClasses = new HashMap<>(); // Note "class" is not a Java class, but classification class.

  ClassificationIndex(@NotNull MemoryObjectTreeNode<NamespaceObject> treeNode,
                      @NotNull Function<T, MemoryObjectTreeNode<NamespaceObject>> nodeSupplier) {
    myTreeNode = treeNode;
    myNodeSupplier = nodeSupplier;
  }

  @NotNull
  public MemoryObjectTreeNode<NamespaceObject> getTreeNode() {
    return myTreeNode;
  }

  @Nullable
  public MemoryObjectTreeNode<NamespaceObject> find(@NotNull NamespaceObject adapter, @NotNull Iterator<T> targetClassIterator) {
    if (!targetClassIterator.hasNext()) {
      return myTreeNode.getChildren().stream().filter(child -> child.getAdapter().isInNamespace(adapter)).findFirst().orElse(null);
    }

    T nextClassifier = targetClassIterator.next();
    return myClasses.containsKey(nextClassifier) ?
           myClasses.get(nextClassifier).find(adapter, targetClassIterator) :
           null;
  }

  /**
   * Iteratively classifies the given {@code targetClasses} into the tree index.
   *
   * @param targetNode          The final node that gets added as is to the tree.
   * @param indexAccumulator    Accumulator to run as the {@code targetClasses} pass through the indices.
   * @param targetClassIterator An {@link Iterator} of classifier objects that will be used to classify the {@code targetNode}.
   */
  public void classify(@NotNull MemoryObjectTreeNode<NamespaceObject> targetNode,
                       @NotNull Consumer<NamespaceObject> indexAccumulator,
                       @NotNull Iterator<T> targetClassIterator) {
    indexAccumulator.accept(myTreeNode.getAdapter());

    if (!targetClassIterator.hasNext()) {
      for (MemoryObjectTreeNode<NamespaceObject> child : myTreeNode.getChildren()) {
        if (child.getAdapter().isInNamespace(targetNode.getAdapter())) {
          indexAccumulator.accept(child.getAdapter());
          return;
        }
      }
      myTreeNode.add(targetNode);
      return;
    }

    T nextClassifier = targetClassIterator.next();
    ClassificationIndex<T> mappedIndex =
      myClasses.computeIfAbsent(nextClassifier, k -> {
        ClassificationIndex<T> result = new ClassificationIndex<>(myNodeSupplier.fun(k), myNodeSupplier);
        myTreeNode.add(result.getTreeNode());
        return result;
      });
    mappedIndex.classify(targetNode, indexAccumulator, targetClassIterator);
  }
}
