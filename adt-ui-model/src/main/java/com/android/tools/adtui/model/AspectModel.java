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

import java.util.*;

public class AspectModel<T extends Enum<T>> extends AspectObserver {
  private Collection<Dependency> myDependencies = Collections.newSetFromMap(new WeakHashMap<Dependency, Boolean>());

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
  public Dependency addDependency(@NotNull AspectObserver observer) {
    Dependency dependency = new Dependency();
    observer.addDependency(dependency);
    myDependencies.add(dependency);
    return dependency;
  }

  public void removeDependencies(AspectObserver observer) {
    myDependencies.removeAll(observer.getDependencies());
  }

  public class Dependency {

    private Multimap<T, Runnable> myListeners = HashMultimap.create();

    public Dependency onChange(T aspect, Runnable runnable) {
      myListeners.put(aspect, runnable);
      return this;
    }

    private void changed(T aspect) {
      myListeners.get(aspect).forEach(Runnable::run);
    }
  }
}
