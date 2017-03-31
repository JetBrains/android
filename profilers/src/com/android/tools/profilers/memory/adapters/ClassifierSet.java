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
package com.android.tools.profilers.memory.adapters;

import com.android.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A general base class for classifying/filtering objects into categories.
 */
public abstract class ClassifierSet implements MemoryObject {
  @NotNull private final String myName;

  @NotNull protected ArrayList<InstanceObject> myInstances;

  @Nullable protected Classifier myClassifier = null;

  protected int myCount = 0;
  protected long myShallowSize = 0L;
  protected long myRetainedSize = 0L;
  protected boolean myHasStackInfo = false;

  public ClassifierSet(@NotNull String name) {
    myName = name;
    myInstances = new ArrayList<>(0);
    resetDescendants();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  public int getCount() {
    return myCount;
  }

  public long getRetainedSize() {
    return myRetainedSize;
  }

  public long getShallowSize() {
    return myShallowSize;
  }

  public void addInstanceObject(@NotNull InstanceObject instanceObject) {
    if (myClassifier != null) {
      myClassifier.partition(instanceObject);
    }
    else {
      myInstances.add(instanceObject);
    }
    myCount += 1;
    myShallowSize += instanceObject.getShallowSize() == INVALID_VALUE ? 0 : instanceObject.getShallowSize();
    myRetainedSize += instanceObject.getRetainedSize() == INVALID_VALUE ? 0 : instanceObject.getRetainedSize();
    myHasStackInfo |= (instanceObject.getCallStack() != null && instanceObject.getCallStack().getStackFramesCount() > 0);
  }

  public int getInstancesCount() {
    if (myClassifier == null) {
      return myInstances.size();
    }
    else {
      return (int)getInstancesStream().count();
    }
  }

  /**
   * Gets a stream of all instances (including all descendants) in this {@link ClassifierSet}.
   */
  @NotNull
  public Stream<InstanceObject> getInstancesStream() {
    if (myClassifier == null) {
      return myInstances.stream();
    }
    else {
      return Stream.concat(getChildrenClassifierSets().stream().flatMap(ClassifierSet::getInstancesStream), myInstances.stream());
    }
  }

  public boolean hasStackInfo() {
    return myHasStackInfo;
  }

  @NotNull
  public List<ClassifierSet> getChildrenClassifierSets() {
    ensurePartition();
    assert myClassifier != null;
    return myClassifier.getClassifierSets();
  }

  /**
   * O(N) search through all descendant {@link ClassifierSet}.
   *
   * @return the set that contains the {@code target}, or null otherwise.
   */
  @Nullable
  public ClassifierSet findContainingClassifierSet(@NotNull InstanceObject target) {
    boolean instancesContainsTarget = myInstances.contains(target);
    if (instancesContainsTarget && myClassifier != null) {
      return this;
    }
    else if (instancesContainsTarget || myClassifier != null) {
      List<ClassifierSet> childrenClassifierSets = getChildrenClassifierSets();
      if (instancesContainsTarget && myInstances.contains(target)) {
        return this; // If after the partition the target still falls within the instances within this set, then return this set.
      }
      for (ClassifierSet set : childrenClassifierSets) {
        ClassifierSet result = set.findContainingClassifierSet(target);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  /**
   * Determines if {@code this} {@link ClassifierSet}'s descendant children forms a superset (could be equivalent) of the given
   * {@code targetSet}'s immediate children.
   */
  public boolean isSupersetOf(@NotNull ClassifierSet targetSet) {
    // TODO perhaps not use getImmediateInstances if we want this to work across all inheritors of ClassifierSet?
    if (getInstancesCount() < targetSet.getInstancesCount()) {
      return false;
    }

    Set<InstanceObject> instances = getInstancesStream().collect(Collectors.toSet());
    return targetSet.getInstancesStream().allMatch(instances::contains);
  }

  protected void resetDescendants() {
    myInstances.clear();
    myInstances.trimToSize();
    myClassifier = null;
    myCount = 0;
    myShallowSize = 0;
    myRetainedSize = 0;
    myHasStackInfo = false;
  }

  /**
   * Force the instances of this node to be partitioned.
   */
  private void ensurePartition() {
    if (myClassifier == null) {
      myClassifier = createSubClassifier();
      myClassifier.partition(myInstances);
    }
  }

  /**
   * Gets the classifier this class will use to classify its instances.
   */
  @NotNull
  protected abstract Classifier createSubClassifier();

  /**
   * The base index for holding child {@link ClassifierSet}s.
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  public static abstract class Classifier {
    public static final Classifier IDENTITY_CLASSIFIER = new Classifier() {
      @Override
      public boolean partition(@NotNull InstanceObject instance) {
        return false;
      }

      @NotNull
      @Override
      public List<ClassifierSet> getClassifierSets() {
        return Collections.emptyList();
      }
    };

    /**
     * Classifies/partitions a single instance for this {@link Classifier}.
     *
     * @return whether or not the given instance has been further partitioned into child classifiers
     */
    // TODO fix partition for post-sort behavior
    public abstract boolean partition(@NotNull InstanceObject instance);

    /**
     * Partitions {@link InstanceObject}s in {@code myInstances} according to the current {@link ClassifierSet}'s strategy.
     * This will consume the instance from the input.
     */
    public void partition(@NotNull ArrayList<InstanceObject> instances) {
      List<InstanceObject> partitionedInstances = new ArrayList<>(instances.size());

      instances.forEach(instance -> {
        if (partition(instance)) {
          partitionedInstances.add(instance);
        }
      });

      if (partitionedInstances.size() == instances.size()) {
        instances.clear();
      }
      else {
        instances.removeAll(partitionedInstances);
      }
      instances.trimToSize();
    }

    /**
     * Gets a {@link List} of the child ClassifierSets.
     *
     * @return
     */
    @NotNull
    public abstract List<ClassifierSet> getClassifierSets();
  }
}
