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

import com.android.tools.adtui.model.FakeTimer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UpdatableManagerTest {

  private FakeUpdater myUpdater;

  private UpdatableManager myUpdatableManager;

  @Before
  public void setUp() {
    myUpdater = new FakeUpdater(new FakeTimer());
    myUpdatableManager = new UpdatableManager(myUpdater);
  }

  @Test
  public void registerAndUnregisterShouldReflectOnUpdater() {
    myUpdater.register(new FakeUpdatable());
    assertEquals(1, myUpdater.getUpdatables().size());

    FakeUpdatable updatable = new FakeUpdatable();
    myUpdatableManager.register(updatable);
    assertEquals(2, myUpdater.getUpdatables().size());

    myUpdatableManager.unregister(updatable);
    assertEquals(1, myUpdater.getUpdatables().size());
  }

  @Test
  public void releaseAllOnlyAffectsUpdatablesAddedByUpdatableManager() {
    myUpdater.register(new FakeUpdatable());
    myUpdater.register(new FakeUpdatable());
    assertEquals(2, myUpdater.getUpdatables().size());

    myUpdatableManager.register(new FakeUpdatable());
    assertEquals(3, myUpdater.getUpdatables().size());

    myUpdatableManager.releaseAll();
    assertEquals(2, myUpdater.getUpdatables().size());
  }

  private static class FakeUpdatable implements Updatable {
    @Override
    public void update(long elapsedNs) {
      // Nothing to do.
    }
  }
}
