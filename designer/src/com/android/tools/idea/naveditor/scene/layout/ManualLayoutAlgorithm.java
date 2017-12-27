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
import com.android.tools.idea.common.scene.SceneComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
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
public class ManualLayoutAlgorithm implements NavSceneLayoutAlgorithm {
  private NavSceneLayoutAlgorithm myFallback;
  private NavigationSchema mySchema;
  private Module myModule;
  private Storage myStorage;

  public ManualLayoutAlgorithm(@NotNull Module module) {
    myModule = module;
  }

  @VisibleForTesting
  ManualLayoutAlgorithm(@NotNull NavSceneLayoutAlgorithm fallback,
                        @NotNull NavigationSchema schema,
                        @NotNull LayoutPositions state) {
    myFallback = fallback;
    mySchema = schema;
    myStorage = new Storage();
    myStorage.myState = state;
  }

  @NotNull
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
    Point location = getStorage().getState().getPositions().get(component.getId());
    if (location != null) {
      component.setPosition(location.x, location.y);
    }
    else {
      getFallback().layout(component);
    }
  }

  public void save(@NotNull SceneComponent component) {
    getStorage().getState().getPositions().put(component.getId(), new Point(component.getDrawX(), component.getDrawY()));
  }

  @NotNull
  private Storage getStorage() {
    if (myStorage == null) {
      myStorage = myModule.getProject().getComponent(Storage.class);
    }
    return myStorage;
  }

  @VisibleForTesting
  static class Point {
    public int x;
    public int y;

    @SuppressWarnings("unused")  // Invoked by reflection
    public Point() {}

    public Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  @VisibleForTesting
  static class LayoutPositions {
    // Map of id to layout position

    @SuppressWarnings("CanBeFinal")  // Somehow making it final breaks persistence
    public Map<String, Point> myPositions = new HashMap<>();

    public Map<String, Point> getPositions() {
      return myPositions;
    }
  }

  @State(name = "navEditor-manualLayoutAlgorithm", storages = @com.intellij.openapi.components.Storage(file = "navEditor.xml"))
  private static class Storage implements PersistentStateComponent<ManualLayoutAlgorithm.LayoutPositions> {
    private LayoutPositions myState;

    @NotNull
    @Override
    public LayoutPositions getState() {
      if (myState == null) {
        myState = new LayoutPositions();
      }
      return myState;
    }

    @Override
    public void loadState(@NotNull LayoutPositions state) {
      myState = state;
    }

  }
}
