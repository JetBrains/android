/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class AspectModel<T extends Enum<T>> extends AspectObserver {

  private Collection<Dependency<T>> myDependencies = Collections.newSetFromMap(new WeakHashMap<Dependency<T>, Boolean>());

  public void changed(T aspect) {
    ArrayList<Dependency> deps = new ArrayList<>(myDependencies.size());
    deps.addAll(myDependencies);
    deps.forEach(dependency -> dependency.changed(aspect));
  }

  /**
   * {@link Dependency}s are added as weak references. These {@link Dependency}s are owned by {@link AspectObserver}s.
   * When an {@link AspectObserver} object is collected, all its {@link Dependency}s are collected,
   * and are removed from the {@link AspectModel}
   */
  public Dependency<T> addDependency(@NotNull AspectObserver observer) {
    Dependency<T> dependency = new Dependency<>();
    observer.addDependency(dependency);
    myDependencies.add(dependency);
    return dependency;
  }

  @TestOnly
  int getDependenciesSize() {
    return myDependencies.size();
  }

  /**
   * Reverse the operations in addDependency. This makes sure we don't leave any dependencies
   * behind in the target {@link AspectObserver} that we had added in the first place.
   *
   * @param observer target {@link AspectObserver}
   */
  public void removeDependencies(AspectObserver observer) {
    List<Dependency<?>> observerDependencies = new ArrayList<>(observer.getDependencies());
    observer.getDependencies().removeAll(myDependencies);
    // Safe to call removeAll after type erasure, since we only care about removing by instance equality.
    //noinspection SuspiciousMethodCalls
    myDependencies.removeAll(observerDependencies);
  }

  public static class Dependency<U extends Enum<U>> {

    private Multimap<U, Runnable> myListeners = HashMultimap.create();

    // Should only be created by AspectModel.
    private Dependency() {
    }

    public Dependency<U> onChange(U aspect, Runnable runnable) {
      myListeners.put(aspect, runnable);
      return this;
    }

    private void changed(U aspect) {
      myListeners.get(aspect).forEach(Runnable::run);
    }
  }
}
