/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link NavSceneLayoutAlgorithm} that puts screens in locations specified in the model, falling back to some other method if no location
 * is specified.
 */
@State(name = "navEditor-manualLayoutAlgorithm", storages = @Storage(file = "navEditor.xml"))
public class ManualLayoutAlgorithm implements NavSceneLayoutAlgorithm, PersistentStateComponent<ManualLayoutAlgorithm.LayoutPositions> {
  private NavSceneLayoutAlgorithm myFallback;
  private NavigationSchema mySchema;
  private LayoutPositions myState;
  private Module myModule;

  @NotNull
  public static ManualLayoutAlgorithm getInstance(@NotNull AndroidFacet facet) {
    return facet.getModule().getComponent(ManualLayoutAlgorithm.class);
  }

  @SuppressWarnings("unused")  // invoked by reflection
  private ManualLayoutAlgorithm(@NotNull Module module) {
    myModule = module;
  }

  @VisibleForTesting
  ManualLayoutAlgorithm(@NotNull NavSceneLayoutAlgorithm fallback,
                        @NotNull NavigationSchema schema,
                        @NotNull LayoutPositions state) {
    myFallback = fallback;
    mySchema = schema;
    myState = state;
  }

  private NavigationSchema getSchema() {
    if (mySchema == null) {
      AndroidFacet instance = AndroidFacet.getInstance(myModule);
      assert instance != null;
      mySchema = NavigationSchema.getOrCreateSchema(instance);
    }
    return mySchema;
  }

  private NavSceneLayoutAlgorithm getFallback() {
    if (myFallback == null) {
      myFallback = new DummyAlgorithm(getSchema());
    }
    return myFallback;
  }

  @Override
  public void layout(@NotNull SceneComponent component) {
    NavigationSchema.DestinationType type = getSchema().getDestinationType(component.getNlComponent().getTagName());
    if (type == NavigationSchema.DestinationType.NAVIGATION && component.getParent() == null) {
      return;
    }
    Point location = getState().getPositions().get(component.getId());
    if (location != null) {
      component.setPosition(location.x, location.y);
    }
    else {
      getFallback().layout(component);
    }
  }

  public void save(@NotNull SceneComponent component) {
    getState().getPositions().put(component.getId(), new Point(component.getDrawX(), component.getDrawY()));
  }

  @NotNull
  @Override
  public LayoutPositions getState() {
    if (myState == null) {
      myState = new LayoutPositions();
    }
    return myState;
  }

  @Override
  public void loadState(LayoutPositions state) {
    myState = state;
  }

  public static class Point {
    public int x;
    public int y;

    public Point() {}

    public Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  public static class LayoutPositions {
    // Map of id to layout position
    public Map<String, Point> myPositions = new HashMap<>();

    public Map<String, Point> getPositions() {
      return myPositions;
    }
  }
}
