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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AspectModelTest {

  enum Aspect {
    ASPECT
  }

  static class Observer extends AspectObserver {

    private final Runnable myRunnable;
    public int changes;
    private AspectModel<Aspect> myModel;

    public Observer(AspectModel<Aspect> model, Runnable run) {
      myRunnable = run;
      setModel(model);
    }

    public Observer(AspectModel<Aspect> model) {
      this(model, null);
    }

    private void changed() {
      changes++;
      if (myRunnable != null) {
        myRunnable.run();
      }
    }

    public void setModel(AspectModel<Aspect> model) {
      if (myModel != null) {
        myModel.removeDependencies(this);
      }
      myModel = model;
      myModel.addDependency(this).onChange(Aspect.ASPECT, this::changed);
    }
  }

  @Test
  public void testAspectFired() {
    AspectModel<Aspect> model = new AspectModel<>();
    Observer observer = new Observer(model);

    assertEquals(0, observer.changes);
    model.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);
  }

  @Test
  public void testMultipleObservers() {
    AspectModel<Aspect> model = new AspectModel<>();
    Observer observer1 = new Observer(model);
    Observer observer2 = new Observer(model);

    assertEquals(0, observer1.changes);
    assertEquals(0, observer2.changes);
    model.changed(Aspect.ASPECT);
    assertEquals(1, observer1.changes);
    assertEquals(1, observer2.changes);
  }

  @Test
  public void testMultipleFires() {
    AspectModel<Aspect> model = new AspectModel<>();
    Observer observer = new Observer(model);

    assertEquals(0, observer.changes);
    model.changed(Aspect.ASPECT);
    model.changed(Aspect.ASPECT);
    assertEquals(2, observer.changes);
  }

  @Test
  public void testRemoveDependencies() {
    AspectModel<Aspect> model1 = new AspectModel<>();
    AspectModel<Aspect> model2 = new AspectModel<>();
    Observer observer = new Observer(model1);

    assertEquals(0, observer.changes);
    model1.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);
    model2.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);

    observer.setModel(model2);

    assertEquals(1, observer.changes);
    model1.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);
    model2.changed(Aspect.ASPECT);
    assertEquals(2, observer.changes);
  }
}