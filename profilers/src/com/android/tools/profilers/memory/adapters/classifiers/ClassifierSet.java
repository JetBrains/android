/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers;

import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.profilers.CachedFunction;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private int myObjectSetCount = 0;
  private int myFilteredObjectSetCount = 0;
  private int mySnapshotObjectCount = 0;
  private int myDeltaAllocations = 0;
  private int myDeltaDeallocations = 0;
  private long myDeltaAllocationsSize = 0;
  private long myDeltaDeallocationsSize = 0;
  private long myTotalNativeSize = 0L;
  private long myTotalShallowSize = 0L;
  private long myTotalRetainedSize = 0L;
  private long myDeltaShallowSize = 0L;
  private int myInstancesWithStackInfoCount = 0;

  // Number of ClassifierSet that match the filter.
  protected int myFilterMatchCount = 0;

  private final CachedFunction<CaptureObjectInstanceFilter, Integer> myInstanceFilterMatchCounter =
    new CachedFunction<>(this::countInstanceFilterMatch, new IdentityHashMap<>());

  protected boolean myIsFiltered;
  // FIXME: `myIsMatched` should be `true` initially, as "no filter" means "trivially matched".
  //        But at the moment that would break one test in `HeapSetNodeHRendererTest`
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

  final public boolean isEmpty() {
    return mySnapshotObjectCount == 0 && myDeltaAllocations == 0 && myDeltaDeallocations == 0;
  }

  final public int getTotalObjectCount() {
    return mySnapshotObjectCount + myDeltaAllocations - myDeltaDeallocations;
  }

  final public int getTotalObjectSetCount() {
    return myObjectSetCount;
  }

  final public int getFilteredObjectSetCount() {
    return myFilteredObjectSetCount;
  }

  final public int getDeltaAllocationCount() {
    return myDeltaAllocations;
  }

  final public int getDeltaDeallocationCount() {
    return myDeltaDeallocations;
  }

  final public long getTotalRetainedSize() {
    return myTotalRetainedSize;
  }

  final public long getTotalShallowSize() {
    return myTotalShallowSize;
  }

  final public long getTotalNativeSize() {
    return myTotalNativeSize;
  }

  final public long getDeltaShallowSize() {
    return myDeltaShallowSize;
  }

  final public int getFilterMatchCount() {
    return myFilterMatchCount;
  }

  final public long getAllocationSize() {
    return myDeltaAllocationsSize;
  }

  final public long getDeallocationSize() {
    return myDeltaDeallocationsSize;
  }

  final public long getTotalRemainingSize() {
    return getAllocationSize() - getDeallocationSize();
  }

  final public int getInstanceFilterMatchCount(CaptureObjectInstanceFilter filter) {
    return myInstanceFilterMatchCounter.invoke(filter);
  }

  /**
   * Add an instance to the baseline snapshot and update the accounting of the "total" values.
   * Note that instances at the baseline must be an allocation event.
   */
  public void addSnapshotInstanceObject(@NotNull InstanceObject instanceObject) {
    changeSnapshotInstanceObject(instanceObject, SetOperation.ADD);
  }

  /**
   * Remove an instance from the baseline snapshot and update the accounting of the "total" values.
   */
  public void removeSnapshotInstanceObject(@NotNull InstanceObject instanceObject) {
    changeSnapshotInstanceObject(instanceObject, SetOperation.REMOVE);
  }

  private void changeSnapshotInstanceObject(@NotNull InstanceObject instanceObject, @NotNull SetOperation op) {
    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      ClassifierSet classifierSet = myClassifier.getClassifierSet(instanceObject, op == SetOperation.ADD);
      assert classifierSet != null;
      classifierSet.changeSnapshotInstanceObject(instanceObject, op);
    }
    else {
      assert (op == SetOperation.ADD) != mySnapshotInstances.contains(instanceObject);
      op.action.accept(mySnapshotInstances, instanceObject);
    }

    mySnapshotObjectCount += op.countChange;
    myTotalNativeSize   += op.countChange * validOrZero(instanceObject.getNativeSize());
    myTotalShallowSize  += op.countChange * validOrZero(instanceObject.getShallowSize());
    myTotalRetainedSize += op.countChange * validOrZero(instanceObject.getRetainedSize());
    if (!instanceObject.isCallStackEmpty()) {
      myInstancesWithStackInfoCount += op.countChange;
    }
    myInstanceFilterMatchCounter.invalidate();
    myNeedsRefiltering = true;
  }

  // Add delta alloc information into the ClassifierSet
  // Return true if the set did not contain the instance prior to invocation
  public boolean addDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return changeDeltaInstanceInformation(instanceObject, true, SetOperation.ADD);
  }

  // Add delta dealloc information into the ClassifierSet
  // Return true if the set did not contain the instance prior to invocation
  public boolean freeDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return changeDeltaInstanceInformation(instanceObject, false, SetOperation.ADD);
  }

  // Remove delta instance alloc information
  // Remove instance when it neither has alloc nor dealloc information
  // Return true if the instance is removed
  public boolean removeAddedDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return changeDeltaInstanceInformation(instanceObject, true, SetOperation.REMOVE);
  }

  // Remove delta instance dealloc information
  // Remove instance when it neither has alloc nor dealloc information
  // Return true if the instance is removed
  public boolean removeFreedDeltaInstanceObject(@NotNull InstanceObject instanceObject) {
    return changeDeltaInstanceInformation(instanceObject, false, SetOperation.REMOVE);
  }

  private boolean changeDeltaInstanceInformation(@NotNull InstanceObject instanceObject, boolean isAllocation, @NotNull SetOperation op) {
    boolean instanceChanged = false;

    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      ClassifierSet classifierSet = myClassifier.getClassifierSet(instanceObject, op == SetOperation.ADD);
      assert classifierSet != null;
      instanceChanged = classifierSet.changeDeltaInstanceInformation(instanceObject, isAllocation, op);
    }
    else if ((op == SetOperation.ADD || !instanceObject.hasTimeData()) &&
             // `contains` is more expensive, so deferred to after above test fails.
             // This line is run often enough to make a difference.
             ((op == SetOperation.ADD) != myDeltaInstances.contains(instanceObject))) {
      op.action.accept(myDeltaInstances, instanceObject);
      instanceChanged = true;
    }

    if (isAllocation) {
      myDeltaAllocations += op.countChange * instanceObject.getInstanceCount();
      myDeltaAllocationsSize += op.countChange * instanceObject.getShallowSize();
    } else {
      myDeltaDeallocations += op.countChange * instanceObject.getInstanceCount();
      myDeltaDeallocationsSize += op.countChange * instanceObject.getShallowSize();
    }

    int factor = op.countChange * (isAllocation ? 1 : -1);
    long deltaNativeSize = factor * validOrZero(instanceObject.getNativeSize());
    long deltaShallowSize = factor * validOrZero(instanceObject.getShallowSize());
    long deltaRetainedSize = factor * validOrZero(instanceObject.getRetainedSize());
    myTotalNativeSize   += deltaNativeSize;
    myDeltaShallowSize  += deltaShallowSize;
    myTotalShallowSize  += deltaShallowSize;
    myTotalRetainedSize += deltaRetainedSize;

    if (instanceChanged && !instanceObject.isCallStackEmpty()) {
      myInstancesWithStackInfoCount += op.countChange;
      myNeedsRefiltering = true;
    }

    if (instanceChanged) {
      myInstanceFilterMatchCounter.invalidate();
    }

    return instanceChanged;
  }

  public void clearClassifierSets() {
    mySnapshotInstances.clear();
    myDeltaInstances.clear();
    myClassifier = createSubClassifier();
    mySnapshotObjectCount = 0;
    myDeltaAllocations = 0;
    myDeltaDeallocations = 0;
    myTotalShallowSize = 0;
    myTotalNativeSize = 0;
    myTotalRetainedSize = 0;
    myDeltaShallowSize = 0;
    myInstancesWithStackInfoCount = 0;
    myObjectSetCount = 0;
    myFilteredObjectSetCount = 0;
    myFilterMatchCount = 0;
  }

  public int getInstancesCount() {
    return (int)getInstancesStream().count();
  }

  /**
   * Gets a stream of all instances (including all descendants) in this ClassifierSet.
   */
  @NotNull
  public Stream<InstanceObject> getInstancesStream() {
    return getStreamOf(me -> Stream.concat(me.mySnapshotInstances.stream(), me.myDeltaInstances.stream()).distinct());
  }

  /**
   * Return the stream of instance objects that contribute to the delta.
   * Note that there can be duplicated entries as {@link #getSnapshotInstanceStream()}.
   */
  @NotNull
  protected Stream<InstanceObject> getDeltaInstanceStream() {
    return getStreamOf(me -> me.myDeltaInstances.stream());
  }

  /**
   * Return the stream of instance objects that contribute to the baseline snapshot.
   * Note that there can duplicated entries as {@link #getDeltaInstanceStream()}.
   */
  @NotNull
  protected Stream<InstanceObject> getSnapshotInstanceStream() {
    return getStreamOf(me -> me.mySnapshotInstances.stream());
  }

  public Stream<InstanceObject> getFilterMatches() {
    return getStreamOf(me -> me.getIsMatched() ? me.getInstancesStream() : Stream.empty());
  }

  private Stream<InstanceObject> getStreamOf(Function<ClassifierSet, Stream<InstanceObject>> extractor) {
    return myClassifier == null ?
           extractor.apply(this) :
           Stream.concat(myClassifier.getAllClassifierSets().stream().flatMap(sub -> sub.getStreamOf(extractor)),
                         extractor.apply(this));
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
      Stream.concat(mySnapshotInstances.stream(), myDeltaInstances.stream()).anyMatch(target::equals);
    if (instancesContainsTarget && myClassifier != null) {
      return this;
    }
    else if (instancesContainsTarget || myClassifier != null) {
      List<ClassifierSet> childrenClassifierSets = getChildrenClassifierSets();
      // mySnapshotInstances/myDeltaInstances can be updated after getChildrenClassiferSets so rebuild the stream.
      boolean stillContainsTarget =
        Stream.concat(mySnapshotInstances.stream(), myDeltaInstances.stream()).anyMatch(target::equals);
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
   * O(N) search through all descendant ClassifierSet.
   * Note - calling this method would cause the full ClassifierSet tree to be built if it has not been done already, and all InstanceObjects
   * would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that satisfies {@code pred}, or null otherwise.
   */
  @Nullable
  public ClassifierSet findClassifierSet(@NotNull Predicate<ClassifierSet> pred) {
    if (pred.test(this)) {
      return this;
    } else if (myClassifier != null) {
      return getChildrenClassifierSets().stream()
        .map(s -> s.findClassifierSet(pred))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
    } else {
      return null;
    }
  }

  /**
   * Determines if {@code this} ClassifierSet's descendant children forms a superset (could be equivalent) of the given
   * {@code targetSet}'s immediate children.
   */
  public boolean isSupersetOf(Set<InstanceObject> targetSet) {
    Set<InstanceObject> clone = Collections.newSetFromMap(new IdentityHashMap<>());
    clone.addAll(targetSet);
    filterOutInstances(clone);
    return clone.isEmpty();
  }

  /**
   * Remove this node's and its children's instances from the given set
   */
  private void filterOutInstances(Set<InstanceObject> remainders) {
    // Filter out from current node
    remainders.removeAll(myDeltaInstances);
    remainders.removeAll(mySnapshotInstances);

    // Filter out from children
    if (myClassifier != null && !remainders.isEmpty()) {
      for (ClassifierSet child : myClassifier.getAllClassifierSets()) {
        child.filterOutInstances(remainders);
        if (remainders.isEmpty()) {
          return;
        }
      }
    }
  }

  /**
   * @return Whether the node's immediate instances overlap with `targetSet`
   */
  public boolean immediateInstancesOverlapWith(Set<InstanceObject> targetSet) {
    return overlaps(myDeltaInstances, targetSet) || overlaps(mySnapshotInstances, targetSet);
  }

  /**
   * @return Whether the node and its descendants' instances overlap with `targetSet`
   */
  public boolean overlapsWith(Set<InstanceObject> targetSet) {
    return immediateInstancesOverlapWith(targetSet) ||
           (myClassifier != null && myClassifier.getAllClassifierSets().stream().anyMatch(c -> c.overlapsWith(targetSet)));
  }

  private static boolean overlaps(Set<InstanceObject> set1, Set<InstanceObject> set2) {
    Set<InstanceObject> iter = set1.size() < set2.size() ? set1 : set2;
    Set<InstanceObject> test = iter == set1 ? set2 : set1;
    return iter.stream().anyMatch(test::contains);
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
  protected void applyFilter(@NotNull Filter filter, boolean hasMatchedAncestor, boolean filterChanged) {
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
    myObjectSetCount = myClassifier.getAllClassifierSets().size();
    myFilteredObjectSetCount = 0;

    myIsMatched = matches(filter);
    myFilterMatchCount = myIsMatched ? 1 : 0;

    assert myClassifier != null;
    for (ClassifierSet classifierSet : myClassifier.getAllClassifierSets()) {
      classifierSet.applyFilter(filter, hasMatchedAncestor || myIsMatched, filterChanged);
      myObjectSetCount += classifierSet.myObjectSetCount;
      if (!classifierSet.getIsFiltered()) {
        myIsFiltered = false;
        mySnapshotObjectCount += classifierSet.mySnapshotObjectCount;
        myDeltaAllocations += classifierSet.myDeltaAllocations;
        myDeltaDeallocations += classifierSet.myDeltaDeallocations;
        myTotalShallowSize += classifierSet.myTotalShallowSize;
        myTotalNativeSize += classifierSet.myTotalNativeSize;
        myTotalRetainedSize += classifierSet.myTotalRetainedSize;
        myDeltaShallowSize += classifierSet.myDeltaShallowSize;
        myInstancesWithStackInfoCount += classifierSet.myInstancesWithStackInfoCount;
        myFilterMatchCount += classifierSet.myFilterMatchCount;
        myFilteredObjectSetCount++;
      }
    }

    myNeedsRefiltering = false;
  }

  protected boolean matches(@NotNull Filter filter) {
    return filter.matches(getName());
  }

  private int countInstanceFilterMatch(CaptureObjectInstanceFilter filter) {
    if (myClassifier != null && !myClassifier.isTerminalClassifier()) {
      return myClassifier.getAllClassifierSets().stream()
        .mapToInt(s -> s.getInstanceFilterMatchCount(filter))
        .sum();
    } else {
      // Spell out the counting of distinct instances satisfying filter
      // without using streams, because this is a bottleneck
      int total = 0;
      for (InstanceObject inst : myDeltaInstances) {
        if (filter.getInstanceTest().invoke(inst)) {
          ++total;
        }
      }
      for (InstanceObject inst : mySnapshotInstances) {
        if (!myDeltaInstances.contains(inst) && filter.getInstanceTest().invoke(inst)) {
          ++total;
        }
      }
      return total;
    }
  }

  private static long validOrZero(long value) {
    return value == INVALID_VALUE ? 0L : value;
  }

  private enum SetOperation {
    ADD(Set::add, 1),
    REMOVE(Set::remove, -1);

    public final BiConsumer<Set<InstanceObject>, InstanceObject> action;
    public final int countChange;
    SetOperation(BiConsumer<Set<InstanceObject>, InstanceObject> action, int countChange) {
      this.action = action;
      this.countChange = countChange;
    }
  }
}
