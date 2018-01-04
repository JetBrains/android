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
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A general base class for classifying/filtering objects into categories.
 */
public abstract class ClassifierSet implements MemoryObject {
  @Nullable private String myName;
  @Nullable private Supplier<String> myNameSupplier = null;

  // The set of instances that make up our baseline snapshot (e.g. live objects at the left of a selection range).
  @NotNull protected final Set<InstanceObject> mySnapshotInstances = new LinkedHashSet<>(0);
  // The set of instances that have delta events (e.g. delta allocations/deallocations within a selection range).
  // Note that instances here can also appear in the set of snapshot instances (e.g. when a instance is allocated before the selection
  // and deallocation within the selection).
  @NotNull protected final Set<InstanceObject> myDeltaInstances = new LinkedHashSet<>(0);

  // Lazily create the Classifier, as it is configurable and isn't necessary until nodes under this node needs to be classified.
  @Nullable protected Classifier myClassifier = null;

  private int mySnapshotObjectCount = 0;
  private int myDeltaAllocations = 0;
  private int myDeltaDeallocations = 0;
  // TODO switch to tracking both delta and total for all native/shallow and retained sizes.
  private long myTotalNativeSize = 0L;
  private long myTotalShallowSize = 0L;
  private long myTotalRetainedSize = 0L;
  private int myInstancesWithStackInfoCount = 0;

  protected boolean myIsFiltered;
  protected boolean myIsMatched;
  // We need to apply filter to ClassifierSet again after any updates (insertion, deletion etc.)
  protected boolean myNeedsRefiltering;

  public ClassifierSet(@NotNull String name) {
    myName = name;
  }

  // Supports lazy-loading the ClassifierSet's name in case it is expensive. The name is fetched and cached the first time
  // getName() is called.
  public ClassifierSet(@NotNull Supplier<String> nameSupplier) {
    myNameSupplier = nameSupplier;
  }

  @Override
  @NotNull
  public String getName() {
    if (myName == null) {
      assert myNameSupplier != null;
      myName = myNameSupplier.get();
    }
    return myName;
  }

  public boolean isEmpty() {
    return mySnapshotObjectCount == 0 && myDeltaAllocations == 0 && myDeltaDeallocations == 0;
  }

  public int getTotalObjectCount() {
    return mySnapshotObjectCount + myDeltaAllocations - myDeltaDeallocations;
  }

  public int getDeltaAllocationCount() {
    return myDeltaAllocations;
  }

  public int getDeltaDeallocationCount() {
    return myDeltaDeallocations;
  }

  public long getTotalRetainedSize() {
    return myTotalRetainedSize;
  }

  public long getTotalShallowSize() {
    return myTotalShallowSize;
  }

  public long getTotalNativeSize() {
    return myTotalNativeSize;
  }

  /**
   * Add an instance to the baseline snapshot and update the accounting of the "total" values.
   * Note that instances at the baseline must be an allocation event.
   */
  public void addSnapshotInstanceObject(@NotNull InstanceObject instanceObject) {
    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      myClassifier.getClassifierSet(instanceObject, true).addSnapshotInstanceObject(instanceObject);
    }
    else {
      assert !mySnapshotInstances.contains(instanceObject);
      mySnapshotInstances.add(instanceObject);
    }

    mySnapshotObjectCount++;
    myTotalNativeSize += instanceObject.getNativeSize() == INVALID_VALUE ? 0 : instanceObject.getNativeSize();
    myTotalShallowSize += instanceObject.getShallowSize() == INVALID_VALUE ? 0 : instanceObject.getShallowSize();
    myTotalRetainedSize += instanceObject.getRetainedSize() == INVALID_VALUE ? 0 : instanceObject.getRetainedSize();
    if (instanceObject.getCallStackDepth() > 0) {
      myInstancesWithStackInfoCount++;
    }
    myNeedsRefiltering = true;
  }

  /**
   * Remove an instance from the baseline snapshot and update the accounting of the "total" values.
   */
  public void removeSnapshotInstanceObject(@NotNull InstanceObject instanceObject) {
    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      ClassifierSet classifierSet = myClassifier.getClassifierSet(instanceObject, false);
      assert classifierSet != null;
      classifierSet.removeSnapshotInstanceObject(instanceObject);
    }
    else {
      assert mySnapshotInstances.contains(instanceObject);
      mySnapshotInstances.remove(instanceObject);
    }

    mySnapshotObjectCount--;
    myTotalNativeSize -= instanceObject.getNativeSize() == INVALID_VALUE ? 0 : instanceObject.getNativeSize();
    myTotalShallowSize -= instanceObject.getShallowSize() == INVALID_VALUE ? 0 : instanceObject.getShallowSize();
    myTotalRetainedSize -= instanceObject.getRetainedSize() == INVALID_VALUE ? 0 : instanceObject.getRetainedSize();
    if (instanceObject.getCallStackDepth() > 0) {
      myInstancesWithStackInfoCount--;
    }
    myNeedsRefiltering = true;
  }

  // Add delta alloc information into the ClassifierSet
  // Return true if the set did not contain the instance prior to invocation
  public boolean addDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return addDeltaInstanceInformation(instanceObject, true);
  }

  // Add delta dealloc information into the ClassifierSet
  // Return true if the set did not contain the instance prior to invocation
  public boolean freeDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return addDeltaInstanceInformation(instanceObject, false);
  }

  // Remove delta instance alloc information
  // Remove instance when it neither has alloc nor dealloc information
  // Return true if the instance is removed
  public boolean removeAddedDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return removeDeltaInstanceInformation(instanceObject, true);
  }

  // Remove delta instance dealloc information
  // Remove instance when it neither has alloc nor dealloc information
  // Return true if the instance is removed
  public boolean removeFreedDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return removeDeltaInstanceInformation(instanceObject, false);
  }

  // Add delta information into the ClassifierSet when correspondent alloc event is inside selection range
  // Return true if the set did not contain the instance prior to invocation
  private boolean addDeltaInstanceInformation(@NotNull InstanceObject instanceObject, boolean isAllocation) {
    boolean instanceAdded = false;

    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      instanceAdded = myClassifier.getClassifierSet(instanceObject, true).addDeltaInstanceInformation(instanceObject, isAllocation);
    }
    else {
      if (!myDeltaInstances.contains(instanceObject)) {
        instanceAdded = true;
        myDeltaInstances.add(instanceObject);
      }
    }

    if (isAllocation) {
      myDeltaAllocations++;
    }
    else {
      myDeltaDeallocations++;
    }

    // TODO update deltas instead.
    myTotalNativeSize +=
      (isAllocation ? 1 : -1) * (instanceObject.getNativeSize() == INVALID_VALUE ? 0 : instanceObject.getNativeSize());
    myTotalShallowSize +=
      (isAllocation ? 1 : -1) * (instanceObject.getShallowSize() == INVALID_VALUE ? 0 : instanceObject.getShallowSize());
    myTotalRetainedSize +=
      (isAllocation ? 1 : -1) * (instanceObject.getRetainedSize() == INVALID_VALUE ? 0 : instanceObject.getRetainedSize());

    if (instanceAdded && instanceObject.getCallStackDepth() > 0) {
      myInstancesWithStackInfoCount++;
      myNeedsRefiltering = true;
    }

    return instanceAdded;
  }

  // Remove delta information from the ClassifierSet
  // Return true if the instance is removed
  private boolean removeDeltaInstanceInformation(@NotNull InstanceObject instanceObject, boolean isAllocation) {
    boolean instanceRemoved = false;
    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      ClassifierSet classifierSet = myClassifier.getClassifierSet(instanceObject, false);
      assert classifierSet != null;
      instanceRemoved = classifierSet.removeDeltaInstanceInformation(instanceObject, isAllocation);
    }
    else {
      if (!instanceObject.hasTimeData() && myDeltaInstances.contains(instanceObject)) {
        myDeltaInstances.remove(instanceObject);
        instanceRemoved = true;
      }
    }

    if (isAllocation) {
      myDeltaAllocations--;
    }
    else {
      myDeltaDeallocations--;
    }

    // TODO update deltas instead.
    myTotalNativeSize -=
      (isAllocation ? 1 : -1) * (instanceObject.getNativeSize() == INVALID_VALUE ? 0 : instanceObject.getNativeSize());
    myTotalShallowSize -=
      (isAllocation ? 1 : -1) * (instanceObject.getShallowSize() == INVALID_VALUE ? 0 : instanceObject.getShallowSize());
    myTotalRetainedSize -=
      (isAllocation ? 1 : -1) * (instanceObject.getRetainedSize() == INVALID_VALUE ? 0 : instanceObject.getRetainedSize());
    if (instanceRemoved && instanceObject.getCallStackDepth() > 0) {
      myInstancesWithStackInfoCount--;
      myNeedsRefiltering = true;
    }

    return instanceRemoved;
  }

  public void clearClassifierSets() {
    mySnapshotInstances.clear();
    myDeltaInstances.clear();
    myClassifier = createSubClassifier();
    mySnapshotObjectCount = 0;
    myDeltaAllocations = 0;
    myDeltaDeallocations = 0;
    myTotalShallowSize = 0;
    myTotalRetainedSize = 0;
    myInstancesWithStackInfoCount = 0;
  }

  public int getInstancesCount() {
    if (myClassifier == null) {
      Set total = new HashSet(mySnapshotInstances);
      total.addAll(myDeltaInstances);
      return total.size();
    }
    else {
      return (int)getInstancesStream().count();
    }
  }

  /**
   * Gets a stream of all instances (including all descendants) in this ClassifierSet.
   */
  @NotNull
  public Stream<InstanceObject> getInstancesStream() {
    Stream<InstanceObject> total = Stream.concat(mySnapshotInstances.stream(), myDeltaInstances.stream()).distinct();
    if (myClassifier == null) {
      return total;
    }
    else {
      return Stream.concat(myClassifier.getAllClassifierSets().stream().flatMap(ClassifierSet::getInstancesStream), total);
    }
  }

  /**
   * Return the stream of instance objects that contribute to the delta.
   * Note that there can be duplicated entries as {@link #getSnapshotInstanceStream()}.
   */
  @NotNull
  protected Stream<InstanceObject> getDeltaInstanceStream() {
    if (myClassifier == null) {
      return myDeltaInstances.stream();
    }
    else {
      return Stream
        .concat(myClassifier.getAllClassifierSets().stream().flatMap(ClassifierSet::getDeltaInstanceStream), myDeltaInstances.stream());
    }
  }

  /**
   * Return the stream of instance objects that contribute to the baseline snapshot.
   * Note that there can duplicated entries as {@link #getDeltaInstanceStream()}.
   */
  @NotNull
  protected Stream<InstanceObject> getSnapshotInstanceStream() {
    if (myClassifier == null) {
      return mySnapshotInstances.stream();
    }
    else {
      return Stream
        .concat(myClassifier.getAllClassifierSets().stream().flatMap(ClassifierSet::getSnapshotInstanceStream),
                mySnapshotInstances.stream());
    }
  }

  public boolean hasStackInfo() {
    return myInstancesWithStackInfoCount > 0;
  }

  @NotNull
  public List<ClassifierSet> getChildrenClassifierSets() {
    ensurePartition();
    assert myClassifier != null;
    return myClassifier.getFilteredClassifierSets();
  }

  /**
   * O(N) search through all descendant ClassifierSet.
   * Note - calling this method would cause the full ClassifierSet tree to be built if it has not been done already, and all InstanceObjects
   * would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that contains the {@code target}, or null otherwise.
   */
  @Nullable
  public ClassifierSet findContainingClassifierSet(@NotNull InstanceObject target) {
    boolean instancesContainsTarget =
      Stream.concat(mySnapshotInstances.stream(), myDeltaInstances.stream()).filter(instance -> target.equals(instance)).findAny()
        .isPresent();
    if (instancesContainsTarget && myClassifier != null) {
      return this;
    }
    else if (instancesContainsTarget || myClassifier != null) {
      List<ClassifierSet> childrenClassifierSets = getChildrenClassifierSets();
      // mySnapshotInstances/myDeltaInstances can be updated after getChildrenClassiferSets so rebuild the stream.
      boolean stillContainsTarget =
        Stream.concat(mySnapshotInstances.stream(), myDeltaInstances.stream()).filter(instance -> target.equals(instance)).findAny()
          .isPresent();
      if (instancesContainsTarget && stillContainsTarget) {
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
   * Determines if {@code this} ClassifierSet's descendant children forms a superset (could be equivalent) of the given
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

  /**
   * Force the instances of this node to be partitioned.
   */
  protected void ensurePartition() {
    if (myClassifier == null) {
      myClassifier = createSubClassifier();
      myClassifier.partition(mySnapshotInstances, myDeltaInstances);
    }
  }

  /**
   * Gets the classifier this class will use to classify its instances.
   */
  @NotNull
  protected abstract Classifier createSubClassifier();

  public boolean getIsFiltered() {
    return isEmpty() || myIsFiltered;
  }

  public boolean getIsMatched() {
    return myIsMatched;
  }

  // Apply filter and update allocation information
  // Filter children classifierSets that neither match the pattern nor have any matched ancestors
  // Update information base on unfiltered children classifierSets
  protected void applyFilter(@Nullable Pattern filter, boolean hasMatchedAncestor, boolean filterChanged) {
    if (!filterChanged && !myNeedsRefiltering) {
      return;
    }

    myIsFiltered = true;
    ensurePartition();
    mySnapshotObjectCount = 0;
    myDeltaAllocations = 0;
    myDeltaDeallocations = 0;
    myTotalShallowSize = 0;
    myTotalNativeSize = 0;
    myTotalRetainedSize = 0;
    myInstancesWithStackInfoCount = 0;

    myIsMatched = matches(filter);

    assert myClassifier != null;
    for (ClassifierSet classifierSet : myClassifier.getAllClassifierSets()) {
      classifierSet.applyFilter(filter, hasMatchedAncestor || myIsMatched, filterChanged);

      if (!classifierSet.getIsFiltered()) {
        myIsFiltered = false;
        mySnapshotObjectCount += classifierSet.mySnapshotObjectCount;
        myDeltaAllocations += classifierSet.myDeltaAllocations;
        myDeltaDeallocations += classifierSet.myDeltaDeallocations;
        myTotalShallowSize += classifierSet.myTotalShallowSize;
        myTotalNativeSize += classifierSet.myTotalNativeSize;
        myTotalRetainedSize += classifierSet.myTotalRetainedSize;
        myInstancesWithStackInfoCount += classifierSet.myInstancesWithStackInfoCount;
      }
    }

    myNeedsRefiltering = false;
  }

  protected boolean matches(Pattern filter) {
    return filter != null && filter.matcher(getName()).matches();
  }

  /**
   * The base index for holding child {@link ClassifierSet}s.
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  public static abstract class Classifier {
    public static final Classifier IDENTITY_CLASSIFIER = new Classifier() {
      @Override
      public boolean isTerminalClassifier() {
        return true;
      }

      @NotNull
      @Override
      public ClassifierSet getClassifierSet(@NotNull InstanceObject instance, boolean createIfAbsent) {
        throw new NotImplementedException(); // not used
      }

      // Return child classifier sets which is non-empty and not filtered out
      @NotNull
      @Override
      public List<ClassifierSet> getFilteredClassifierSets() {
        return Collections.emptyList();
      }

      // Return all child classifier sets
      @NotNull
      @Override
      protected List<ClassifierSet> getAllClassifierSets() {
        return Collections.emptyList();
      }
    };

    /**
     * @return true if this Classifier is a terminal classifier, and instances will not be further classified.
     */
    public boolean isTerminalClassifier() {
      return false;
    }

    /**
     * Retrieve the next-level ClassifierSet that the given {@code instance} belongs to. If none exists and {@code createIfAbsent} is true,
     * then create and return the new ClassiferSet.
     */
    @Nullable
    public abstract ClassifierSet getClassifierSet(@NotNull InstanceObject instance, boolean createIfAbsent);

    /**
     * Gets a {@link List} of the child ClassifierSets.
     */
    @NotNull
    public abstract List<ClassifierSet> getFilteredClassifierSets();

    @NotNull
    protected abstract List<ClassifierSet> getAllClassifierSets();

    /**
     * Partitions {@link InstanceObject}s in {@code snapshotInstances} and {@code myDeltaInstances} according to the current
     * {@link ClassifierSet}'s strategy. This will consume the instances from the input.
     */
    public final void partition(@NotNull Collection<InstanceObject> snapshotInstances, @NotNull Collection<InstanceObject> deltaInstances) {
      if (isTerminalClassifier()) {
        return;
      }

      snapshotInstances.forEach(instance -> getClassifierSet(instance, true).addSnapshotInstanceObject(instance));
      deltaInstances.forEach(instance -> {
        if (instance.hasTimeData()) {
          // Note - we only add the instance allocation to our delta set if it is not already accounted for in the baseline snapshot.
          // Otherwise we would be double counting allocations.
          if (instance.hasAllocTime() && !snapshotInstances.contains(instance)) {
            getClassifierSet(instance, true).addDeltaInstanceObject(instance);
          }
          if (instance.hasDeallocTime()) {
            getClassifierSet(instance, true).freeDeltaInstanceObject(instance);
          }
        }
        else {
          getClassifierSet(instance, true).addDeltaInstanceObject(instance);
        }
      });
      snapshotInstances.clear();
      deltaInstances.clear();
    }
  }
}
