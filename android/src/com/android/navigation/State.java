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

public class State {
  private final String controllerClassName;
  private String xmlResourceName;
  private Point location = Point.ORIGIN;

  public State(@Property("controllerClassName") String controllerClassName) {
    this.controllerClassName = controllerClassName;
  }

  public String getControllerClassName() {
    return controllerClassName;
  }

  public String getXmlResourceName() {
    return xmlResourceName;
  }

  public void setXmlResourceName(String xmlResourceName) {
    this.xmlResourceName = xmlResourceName;
  }

  @NonNull
  public Point getLocation() {
    return location;
  }

  public void setLocation(@NonNull Point location) {
    this.location = location;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    State state = (State)o;

    if (!controllerClassName.equals(state.controllerClassName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return controllerClassName.hashCode();
  }
}
