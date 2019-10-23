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

import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AspectModelTest {

  enum Aspect {
    ASPECT,
    ANOTHER_ASPECT,
  }

  private static class Observer extends AspectObserver {

    private final Collection<Aspect> myObserved;
    public int changes;
    @Nullable public Aspect lastAspect;
    private AspectModel<Aspect> myModel;

    public Observer(AspectModel<Aspect> model, Aspect... observedAspects) {
      if (observedAspects.length > 0) {
        myObserved = Arrays.asList(observedAspects);
      }
      else {
        myObserved = Collections.singletonList(Aspect.ASPECT);
      }
      setModel(model);
    }

    private void changed(Aspect aspect) {
      changes++;
      lastAspect = aspect;
    }

    public void setModel(AspectModel<Aspect> model) {
      if (myModel != null) {
        myModel.removeDependencies(this);
      }
      myModel = model;
      AspectModel.Dependency dependency = myModel.addDependency(this);
      for (Aspect aspect : myObserved) {
        dependency.onChange(aspect, () -> changed(aspect));
      }
    }
  }

  @Test
  public void removeDependenciesShouldRemoveOnlyIntersectionBetweenObserverAndModel() {
    AspectObserver observer1 = new AspectObserver();
    AspectModel<Aspect> aspectModel1 = new AspectModel<>();
    AspectModel<Aspect> aspectModel2 = new AspectModel<>();
    aspectModel1.addDependency(observer1).onChange(Aspect.ASPECT, () -> {});
    aspectModel2.addDependency(observer1).onChange(Aspect.ANOTHER_ASPECT, () -> {});

    assertEquals(1, aspectModel1.getDependenciesSize());
    assertEquals(1, aspectModel2.getDependenciesSize());

    aspectModel1.removeDependencies(observer1);
    aspectModel2.removeDependencies(observer1);

    assertEquals(0, aspectModel1.getDependenciesSize());
    assertEquals(0, aspectModel2.getDependenciesSize());
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
  public void testMultipleAspects() {
    AspectModel<Aspect> model = new AspectModel<>();
    Observer observer1 = new Observer(model, Aspect.ASPECT);
    Observer observer2 = new Observer(model, Aspect.ANOTHER_ASPECT);
    Observer observerBoth = new Observer(model, Aspect.ASPECT, Aspect.ANOTHER_ASPECT);

    assertEquals(0, observer1.changes);
    assertNull(observer1.lastAspect);
    assertEquals(0, observer2.changes);
    assertNull(observer2.lastAspect);
    assertEquals(0, observerBoth.changes);
    assertNull(observerBoth.lastAspect);

    model.changed(Aspect.ASPECT);
    assertEquals(1, observer1.changes);
    assertEquals(Aspect.ASPECT, observer1.lastAspect);
    assertEquals(0, observer2.changes);
    assertNull(observer2.lastAspect);
    assertEquals(1, observerBoth.changes);
    assertEquals(Aspect.ASPECT, observerBoth.lastAspect);

    model.changed(Aspect.ANOTHER_ASPECT);
    assertEquals(1, observer1.changes);
    assertEquals(Aspect.ASPECT, observer1.lastAspect);
    assertEquals(1, observer2.changes);
    assertEquals(Aspect.ANOTHER_ASPECT, observer2.lastAspect);
    assertEquals(2, observerBoth.changes);
    assertEquals(Aspect.ANOTHER_ASPECT, observerBoth.lastAspect);
  }

  @Test
  public void testRemoveDependencies() {
    AspectModel<Aspect> model1 = new AspectModel<>();
    AspectModel<Aspect> model2 = new AspectModel<>();
    Observer observer = new Observer(model1);

    assertEquals(1, observer.getDependencies().size());
    assertEquals(0, observer.changes);
    model1.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);
    model2.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);

    observer.setModel(model2);
    assertEquals(1, observer.getDependencies().size());
    assertEquals(1, observer.changes);
    model1.changed(Aspect.ASPECT);
    assertEquals(1, observer.changes);
    model2.changed(Aspect.ASPECT);
    assertEquals(2, observer.changes);
  }
}