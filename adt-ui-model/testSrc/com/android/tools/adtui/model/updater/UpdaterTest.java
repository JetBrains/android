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
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class UpdaterTest {
  private Updater myUpdater;

  @Before
  public void setUp() {
    myUpdater = new Updater(new FakeTimer());
  }

  @Test
  public void testRegister() {
    List<Updatable> updated = new ArrayList<>();
    FakeUpdatable updatableA = new FakeUpdatable(updated);
    FakeUpdatable updatableB = new FakeUpdatable(updated);
    myUpdater.register(Arrays.asList(updatableA, updatableB));

    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB), updated);
  }

  @Test
  public void updatableRegistersAnotherUpdatable() {
    final List<Updatable> updated = new ArrayList<>();
    FakeUpdatable updatableD = new FakeUpdatable(updated);

    FakeUpdatable updatableA = new FakeUpdatable(updated) {
      @Override
      public void update(long elapsedNs) {
        super.update(elapsedNs);
        myUpdater.register(updatableD);
      }
    };

    FakeUpdatable updatableB = new FakeUpdatable(updated);
    FakeUpdatable updatableC = new FakeUpdatable(updated);

    myUpdater.register(updatableA);
    myUpdater.register(updatableB);
    myUpdater.register(updatableC);

    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB, updatableC), updated);

    myUpdater.unregister(updatableA);
    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableB, updatableC, updatableD), updated);
  }

  @Test
  public void testUnregister() {
    List<Updatable> updated = new ArrayList<>();
    FakeUpdatable updatableA = new FakeUpdatable(updated);
    FakeUpdatable updatableB = new FakeUpdatable(updated);
    myUpdater.register(updatableA);
    myUpdater.register(updatableB);

    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB), updated);

    myUpdater.unregister(updatableA);
    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Collections.singletonList(updatableB), updated);
  }

  @Test
  public void updatableUnregistersUpdatable() {
    List<Updatable> updated = new ArrayList<>();
    FakeUpdatable updatableA = new FakeUpdatable(updated);
    FakeUpdatable updatableC = new FakeUpdatable(updated);
    FakeUpdatable updatableB = new FakeUpdatable(updated) {
      @Override
      public void update(long elapsedNs) {
        super.update(elapsedNs);
        myUpdater.unregister(updatableC);
      }
    };

    myUpdater.register(Arrays.asList(updatableA, updatableB, updatableC));

    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB, updatableC), updated);

    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB), updated);
  }

  @Test
  public void testReset() {
    List<Updatable> reset = new ArrayList<>();
    List<Updatable> updated = new ArrayList<>();
    Updatable updatableA = new FakeUpdatable(updated) {
      @Override
      public void reset() {
        reset.add(this);
      }
    };
    Updatable updatableB = new FakeUpdatable(updated) {
      @Override
      public void reset() {
        reset.add(this);
      }
    };
    
    myUpdater.register(updatableA);
    myUpdater.register(updatableB);

    updated.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB), updated);

    myUpdater.reset();
    reset.clear();
    myUpdater.getTimer().tick(1);
    assertEquals(Arrays.asList(updatableA, updatableB), reset);
  }

  private static class FakeUpdatable implements Updatable {
    private final List<Updatable> myUpdated;

    private FakeUpdatable(List<Updatable> updated) {
      myUpdated = updated;
    }

    @Override
    public void update(long elapsedNs) {
      myUpdated.add(this);
    }
  }
}
