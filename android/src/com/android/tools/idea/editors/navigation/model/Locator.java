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
import com.android.annotations.Nullable;

public class Locator {
  @NonNull
  public final State state;
  public final String fragmentName;
  public final String viewName;

  private Locator(@NonNull State state, @Nullable String fragmentName, @Nullable String viewName) {
    this.state = state;
    this.fragmentName = fragmentName;
    this.viewName = viewName;
  }

  public static Locator of(@NonNull State state) {
    return new Locator(state, null, null);
  }

  public static Locator of(@NonNull State state, @Nullable String viewName) {
    return new Locator(state, null, viewName);
  }

  public static Locator of(@NonNull State state, @Nullable String fragmentName, @Nullable String viewName) {
    return new Locator(state, fragmentName, viewName);
  }

  @NonNull
  public State getState() {
    return state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Locator locator = (Locator)o;

    if (fragmentName != null ? !fragmentName.equals(locator.fragmentName) : locator.fragmentName != null) return false;
    if (!state.equals(locator.state)) return false;
    if (viewName != null ? !viewName.equals(locator.viewName) : locator.viewName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = state.hashCode();
    result = 31 * result + (fragmentName != null ? fragmentName.hashCode() : 0);
    result = 31 * result + (viewName != null ? viewName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Locator{" +
           "state=" + state +
           ", viewName='" + viewName + '\'' +
           '}';
  }
}
