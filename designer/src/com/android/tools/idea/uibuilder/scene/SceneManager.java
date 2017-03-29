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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.handlers.constraint.targets.DragDndTarget;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A facility for creating and updating {@link Scene}s based on {@link NlModel}s.
 */
abstract public class SceneManager implements Disposable {
  private final NlModel myModel;
  final private DesignSurface myDesignSurface;
  private Scene myScene;

  public SceneManager(NlModel model, DesignSurface surface) {
    myModel = model;
    myDesignSurface = surface;
    Disposer.register(model, this);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public Scene build() {
    assert myScene == null;
    myScene = new Scene(myDesignSurface);
    return myScene;
  }

  /**
   * Update the Scene with the components in the given NlModel. This method needs to be called in the dispatch thread.
   * {@link #build()} must have been invoked already.
   */
  public void update() {
    List<NlComponent> components = getModel().getComponents();
    Scene scene = getScene();
    if (components.size() == 0) {
      scene.removeAllComponents();
      scene.setRoot(null);
      return;
    }
    Set<SceneComponent> usedComponents = new HashSet<>();
    Set<SceneComponent> oldComponents = new HashSet<>(scene.getSceneComponents());

    NlComponent rootComponent = components.get(0).getRoot();

    SceneComponent root = updateFromComponent(rootComponent, usedComponents);
    oldComponents.removeAll(usedComponents);
    oldComponents.forEach(scene::removeComponent);

    SelectionModel selectionModel = getDesignSurface().getSelectionModel();
    scene.setRoot(root);
    if (root != null && selectionModel.isEmpty()) {
      addTargets(root);
    }
    scene.needsRebuildList();
  }

  abstract protected void addTargets(@NotNull SceneComponent component);

  /**
   * Update (and if necessary, create) the SceneComponent paired to the given NlComponent
   *
   * @param component      a given NlComponent
   * @param seenComponents Collector of components that were seen during NlComponent tree traversal.
   * @return the SceneComponent paired with the given NlComponent
   */
  protected SceneComponent updateFromComponent(@NotNull NlComponent component, Set<SceneComponent> seenComponents) {
    SceneComponent sceneComponent = getScene().getSceneComponent(component);
    if (sceneComponent == null) {
      sceneComponent = new SceneComponent(getScene(), component);
    }
    seenComponents.add(sceneComponent);

    updateFromComponent(component, sceneComponent);

    for (NlComponent nlChild : component.getChildren()) {
      SceneComponent child = updateFromComponent(nlChild, seenComponents);
      if (child.getParent() != sceneComponent) {
        sceneComponent.addChild(child);
      }
    }
    return sceneComponent;
  }

  /**
   * Creates a {@link TemporarySceneComponent} in our Scene.
   */
  @NotNull
  public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component) {
    Scene scene = getScene();

    assert scene.getRoot() != null;

    TemporarySceneComponent tempComponent = new TemporarySceneComponent(getScene(), component);
    tempComponent.addTarget(new DragDndTarget());
    scene.setAnimated(false);
    scene.getRoot().addChild(tempComponent);
    updateFromComponent(component, tempComponent);
    scene.setAnimated(true);

    return tempComponent;
  }

  abstract protected void updateFromComponent(@NotNull NlComponent component, SceneComponent sceneComponent);

  @NotNull
  protected DesignSurface getDesignSurface() {
    return myDesignSurface;
  }

  @NotNull
  protected NlModel getModel() {
    return myModel;
  }

  @NotNull
  protected Scene getScene() {
    assert myScene != null;
    return myScene;
  }

  public abstract void requestRender();

  public abstract void layout(boolean animate);
}
