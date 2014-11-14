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
import com.android.annotations.Property;

public class MenuState extends State {
  private final String xmlResourceName;

  public MenuState(@NonNull @Property("xmlResourceName") String xmlResourceName) {
    this.xmlResourceName = xmlResourceName;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }

  @Nullable
  @Override
  public String getClassName() {
    return null;
  }

  @NonNull
  @Override
  public String getXmlResourceName() {
    return xmlResourceName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MenuState menuState = (MenuState)o;

    if (!xmlResourceName.equals(menuState.xmlResourceName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return xmlResourceName.hashCode();
  }

  @Override
  public String toString() {
    return "MenuState{" + xmlResourceName + '}';
  }

}
