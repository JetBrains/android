/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation.model;

import com.android.annotations.NonNull;
import com.android.tools.idea.editors.navigation.annotations.Property;

public abstract class State {
  private final String className;

  public abstract static class Visitor {
    public abstract void visit(ActivityState state);
    public abstract void visit(MenuState state);
  }

  public static class BaseVisitor extends Visitor {
    @Override
    public void visit(ActivityState state) {
    }

    @Override
    public void visit(MenuState state) {
    }
  }

  protected State(@Property("className") String className) {
    this.className = className;
  }

  public abstract void accept(Visitor visitor);

  @NonNull
  public String getClassName() {
    return className;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    State that = (State)o;

    if (!className.equals(that.className)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return className.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + className + '}';
  }
}
