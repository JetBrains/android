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
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AspectModel<T extends Enum<T>> {
  private List<Dependency> myDependencies = new LinkedList<>();


  public void changed(T aspect) {
    myDependencies.forEach(dependency -> dependency.changed(aspect));
  }

  public Dependency addDependency() {
    Dependency dependency = new Dependency();
    myDependencies.add(dependency);
    return dependency;
  }

  public class Dependency {

    private Multimap<T, Runnable> myListeners = HashMultimap.create();
    private Consumer<Runnable> myExecutor = Runnable::run;

    public Dependency setExecutor(Consumer<Runnable> executor) {
      myExecutor = executor;
      return this;
    }

    /**
     * Sets an executor as an instance method of a target object.
     * If the target is null the current executor is not changed.
     */
    public <E> Dependency setExecutor(@Nullable E target, BiConsumer<E, Runnable> executor) {
      if (target != null) {
        return setExecutor(r -> executor.accept(target, r));
      }
      return this;
    }

    public Dependency onChange(T aspect, Runnable runnable) {
      myListeners.put(aspect, runnable);
      return this;
    }

    public void changed(T aspect) {
      myListeners.get(aspect).forEach(myExecutor);
    }
  }
}
