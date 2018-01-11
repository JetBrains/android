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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
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
      mySchema = NavigationSchema.get(instance);
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
    Deque<String> stack = getParentStack(component);

    LayoutPositions positions = getStorage().getState();
    String name = getFileName(component);
    if (name == null) {
      // should only happen in tests.
      return;
    }
    positions = positions.get(name);
    while (!stack.isEmpty()) {
      if (positions == null) {
        break;
      }
      positions = positions.get(stack.pop());
    }

    if (positions != null) {
      Point location = positions.myPosition;
      if (location != null) {
        component.setPosition(location.x, location.y);
        return;
      }
    }

    getFallback().layout(component);
    save(component);
  }

  @NotNull
  private static Deque<String> getParentStack(@NotNull SceneComponent component) {
    Deque<String> stack = new LinkedList<>();
    NlComponent current = component.getNlComponent();
    while (current != null && !current.isRoot()) {
      String id = current.getId();
      if (id == null) {
        NlModel model = current.getModel();
        Document doc = FileDocumentManager.getInstance().getDocument(model.getVirtualFile());
        int line = -1;
        if (doc != null) {
          line = doc.getLineNumber(current.getTag().getTextRange().getStartOffset()) + 1;
        }
        // TODO: surface this
        Logger.getInstance(ManualLayoutAlgorithm.class).warn("Element with null id encountered" + (line != -1 ? " on line " + line : ""));
      }
      stack.push(id);
      current = current.getParent();
    }
    return stack;
  }

  public void save(@NotNull SceneComponent component) {
    LayoutPositions positions = getStorage().getState();

    Deque<String> stack = getParentStack(component);
    stack.push(getFileName(component));
    while (!stack.isEmpty()) {
      String element = stack.pop();
      if (element == null) {
        // We encountered an element with no id. This shouldn't happen (we shouldn't be able to build and thus shouldn't be showing the
        // editor at all), but if it does, just abort.
        return;
      }
      LayoutPositions newPositions = positions.get(element);
      if (newPositions == null) {
        newPositions = new LayoutPositions();
        positions.put(element, newPositions);
      }
      positions = newPositions;
    }
    positions.myPosition = new Point(component.getDrawX(), component.getDrawY());
  }

  @NotNull
  private static String getFileName(@NotNull SceneComponent component) {
    return component.getNlComponent().getModel().getVirtualFile().getName();
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
    public Point() {
    }

    public Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  @VisibleForTesting
  static class LayoutPositions {
    // Map of id to layout position

    @SuppressWarnings("CanBeFinal")  // Somehow making it final breaks persistence
    public Map<String, LayoutPositions> myPositions;

    public Point myPosition;

    @Nullable
    public LayoutPositions get(@Nullable String id) {
      return myPositions == null ? null : myPositions.get(id);
    }

    public void put(@NotNull String id, @NotNull LayoutPositions sub) {
      if (myPositions == null) {
        myPositions = new HashMap<>();
      }
      myPositions.put(id, sub);
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
