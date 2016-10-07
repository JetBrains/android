/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.fixtures;

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class ComponentListFixture {
  private final ScreenFixture myScreenFixture;
  private List<ComponentFixture> myComponents;

  public ComponentListFixture(@NotNull ScreenFixture screenFixture, @NotNull List<ComponentFixture> components) {
    myScreenFixture = screenFixture;
    myComponents = components;
  }

  @NotNull
  public ComponentListFixture primary(@NotNull String description) {
    ComponentFixture primary = myScreenFixture.get(description);
    assertTrue(myComponents.contains(primary));
    myComponents = Lists.newArrayList(myComponents); // ensure mutable
    myComponents.remove(primary); // move to front
    myComponents.add(0, primary);
    return this;
  }

  @NotNull
  public ComponentFixture primary() {
    return myComponents.get(0);
  }

  @NotNull
  public ScreenView getScreen() {
    return myScreenFixture.getScreen();
  }

  @NotNull
  public List<NlComponent> getComponents() {
    List<NlComponent> list = Lists.newArrayList();
    for (ComponentFixture fixture : myComponents) {
      list.add(fixture.getComponent());
    }
    return list;
  }
}
