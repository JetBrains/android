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
package com.android.tools.adtui.model.updater;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a subset of {@link Updatable} belonging to an {@link Updater}.
 */
public class UpdatableManager {

  @NotNull
  private final Updater myUpdater;

  @NotNull
  private final List<Updatable> myManagedUpdatables;

  public UpdatableManager(@NotNull Updater updater) {
    myUpdater = updater;
    myManagedUpdatables = new ArrayList<>();
  }

  public void register(Updatable updatable) {
    myUpdater.register(updatable);
    myManagedUpdatables.add(updatable);
  }

  public void unregister(Updatable updatable) {
    myUpdater.unregister(updatable);
    myManagedUpdatables.remove(updatable);
  }

  public void releaseAll() {
    for (Updatable updatable : myManagedUpdatables) {
      myUpdater.unregister(updatable);
    }
    myManagedUpdatables.clear();
  }
}
