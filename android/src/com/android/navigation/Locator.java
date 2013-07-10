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
package com.android.navigation;

import com.android.annotations.NonNull;
import com.android.annotations.Property;

public class Locator {
  @NonNull
  private final State state;
  private String viewName;

  public Locator(@NonNull @Property("state") State state) {
    this.state = state;
  }

  @NonNull
  public State getState() {
    return state;
  }

  public String getViewName() {
    return viewName;
  }

  public void setViewName(String viewName) {
    this.viewName = viewName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Locator locator = (Locator)o;

    if (!state.equals(locator.state)) return false;
    if (viewName != null ? !viewName.equals(locator.viewName) : locator.viewName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = state.hashCode();
    result = 31 * result + (viewName != null ? viewName.hashCode() : 0);
    return result;
  }
}
