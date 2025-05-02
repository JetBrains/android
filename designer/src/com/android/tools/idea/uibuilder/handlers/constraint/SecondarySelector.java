/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.model.NlComponent;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This is a small selection component used in supporting components
 */
public class SecondarySelector {
  NlComponent myComponent;
  Constraint myConstraint;

  public enum Constraint {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    BASELINE
  }

  @Override
  public String toString() {
    return " " + hashCode() + " " + myComponent + " : " + myConstraint;
  }

  private static final Queue<SecondarySelector> selectorsToReuse = new ConcurrentLinkedQueue<>();

  private SecondarySelector() {
  }

  public static SecondarySelector get(NlComponent component, Constraint constraint) {
    SecondarySelector selector = selectorsToReuse.poll();
    if (selector == null) {
      selector = new SecondarySelector();
    }
    selector.myComponent = component;
    selector.myConstraint = constraint;
    return selector;
  }

  public Constraint getConstraint() {
    return myConstraint;
  }

  public NlComponent getComponent() {
    return myComponent;
  }

  public void release() {
    myComponent = null;
    myConstraint = null;
    selectorsToReuse.add(this);
  }
}
