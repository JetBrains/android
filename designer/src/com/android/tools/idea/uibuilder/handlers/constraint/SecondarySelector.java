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
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This is a small selection component used in supporting components
 */
public final class SecondarySelector {
  private static boolean DEBUG = false; // can be used to trace leaking objects
  NlComponent myComponent;
  Constraint myConstraint;
  String myTrace;

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

  private static ArrayList<SecondarySelector> freeList = new ArrayList<>();
  private static HashSet<SecondarySelector> freeSet = new HashSet<>();
  private static ArrayList<SecondarySelector> unFreed;

  static {
    if (DEBUG) {
      unFreed = new ArrayList<>();
    }
  }

  private SecondarySelector() {
  }

  public static void debug() {
    System.out.println("freelist size = " + unFreed.size() + " ");
    for (SecondarySelector selector : unFreed) {
      System.out.println("created at  .(" + selector.myTrace + ") but not freed");
    }
    System.out.println("freelist size = " + unFreed.size() + " ");
  }

  public static SecondarySelector get(NlComponent component, Constraint constraint) {
    SecondarySelector selector = freeList.isEmpty() ? new SecondarySelector() : freeList.remove(0);
    freeSet.remove(selector);
    selector.myComponent = component;
    selector.myConstraint = constraint;
    if (DEBUG) {
      StackTraceElement st = new Throwable().getStackTrace()[1];
      selector.myTrace = st.getFileName() + ":" + st.getLineNumber();
      unFreed.add(selector);
    }
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
    if (DEBUG) {
      unFreed.remove(this);
    }
    if (freeSet.add(this)) {
      freeList.add(this);
    }
  }

}
