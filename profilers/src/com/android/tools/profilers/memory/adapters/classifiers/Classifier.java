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

import com.android.tools.profilers.memory.adapters.InstanceObject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The base index for holding child {@link ClassifierSet}s.
 */
public abstract class Classifier {
  public static final Classifier IDENTITY_CLASSIFIER = new Classifier() {
    @Override
    public boolean isTerminalClassifier() {
      return true;
    }

    @NotNull
    @Override
    public ClassifierSet getClassifierSet(@NotNull InstanceObject instance, boolean createIfAbsent) {
      throw new UnsupportedOperationException(); // not used
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