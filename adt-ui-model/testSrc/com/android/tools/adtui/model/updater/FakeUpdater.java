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

import com.android.tools.adtui.model.StopwatchTimer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FakeUpdater extends Updater {
  private List<Updatable> myUpdatables;

  public FakeUpdater(@NotNull StopwatchTimer timer) {
    super(timer);
    myUpdatables = new ArrayList<>();
  }

  @Override
  public void register(Updatable updatable) {
    super.register(updatable);
    myUpdatables.add(updatable);
  }

  @Override
  public void unregister(@NotNull Updatable updatable) {
    super.unregister(updatable);
    myUpdatables.remove(updatable);
  }

  public List<Updatable> getUpdatables() {
    return myUpdatables;
  }
}
