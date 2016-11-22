/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * A Scene contains a hierarchy of SceneComponent representing the bounds
 * of the widgets being layed out. Multiple NlModel can be used to populate
 * a Scene.
 */
public class Scene implements ModelListener {

  private final float myDpiFactor;
  private HashMap<NlComponent, SceneComponent> mySceneComponents = new HashMap<>();
  private SceneComponent myRoot;
  private boolean myAnimate = true; // animate layout changes

  /**
   * Helper static function to create a Scene instance given a NlModel
   *
   * @param model the NlModel instance used to populate the Scene
   * @return a newly initialized Scene instance populated using the given NlModel
   */
  public static Scene createScene(@NotNull NlModel model) {
    int dpiFactor = model.getConfiguration().getDensity().getDpiValue();
    Scene scene = new Scene(dpiFactor / 160f);
    scene.add(model);
    return scene;
  }

  /**
   * Default constructor
   *
   * @param dpiFactor
   */
  @VisibleForTesting
  Scene(float dpiFactor) {
    myDpiFactor = dpiFactor;
  }

  /////////////////////////////////////////////////////////////////////////////
  // Dp / Pixels conversion utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Convert from Android pixels to Dp
   *
   * @param px the pixel amount
   * @return the converted Dp amount
   */
  public int pxToDp(@AndroidCoordinate int px) {
    return (int)(0.5f + px / myDpiFactor);
  }

  /**
   * Convert from Dp to Android pixels
   *
   * @param dp the Dp amount
   * @return the converted Android pixels amount
   */
  public int dpToPx(@AndroidDpCoordinate int dp) {
    return (int)(0.5f + dp * myDpiFactor);
  }

  /////////////////////////////////////////////////////////////////////////////
  // Accessors
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Return the current animation status
   *
   * @return true if layout updates will animate
   */
  public boolean getAnimate() {
    return myAnimate;
  }

  /**
   * Set the layout updates to animate or not
   *
   * @param animate true to animate the changes
   */
  public void setAnimate(boolean animate) {
    myAnimate = animate;
  }

  /**
   * Return the SceneComponent corresponding to the given NlComponent, if existing
   *
   * @param component the NlComponent to use
   * @return the SceneComponent paired to the given NlComponent, if found
   */
  @Nullable
  public SceneComponent getSceneComponent(@NotNull NlComponent component) {
    return mySceneComponents.get(component);
  }

  /**
   * Return the current SceneComponent root in the Scene
   *
   * @return the root SceneComponent
   */
  @Nullable
  public SceneComponent getRoot() {
    return myRoot;
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update / Maintenance of the tree
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Add the NlComponents contained in the given NlModel to the Scene
   *
   * @param model the NlModel to use
   */
  public void add(@NotNull NlModel model) {
    List<NlComponent> components = model.getComponents();
    if (components.size() == 0) {
      return;
    }
    NlComponent rootComponent = components.get(0).getRoot();
    myRoot = updateFromComponent(rootComponent);
    model.addListener(this);
  }

  /**
   * Add the given SceneComponent to the Scene
   *
   * @param component
   */
  public void addComponent(@NotNull SceneComponent component) {
    mySceneComponents.put(component.getNlComponent(), component);
  }

  /**
   * Update the Scene with the components in the given NlModel
   *
   * @param model the NlModel to udpate from
   */
  public void updateFrom(@NotNull NlModel model) {
    List<NlComponent> components = model.getComponents();
    if (components.size() == 0) {
      mySceneComponents.clear();
      myRoot = null;
      return;
    }
    for (SceneComponent component : mySceneComponents.values()) {
      component.used = false;
    }
    NlComponent rootComponent = components.get(0).getRoot();
    myRoot = updateFromComponent(rootComponent);
    Iterator<SceneComponent> it = mySceneComponents.values().iterator();
    while (it.hasNext()) {
      SceneComponent component = it.next();
      if (!component.used) {
        component.removeFromParent();
        it.remove();
      }
    }
  }

  /**
   * Update (and if necessary, create) the SceneComponent paired to the given NlComponent
   *
   * @param component a given NlComponent
   * @return the SceneComponent paired with the given NlComponent
   */
  private SceneComponent updateFromComponent(@NotNull NlComponent component) {
    SceneComponent sceneComponent = mySceneComponents.get(component);
    if (sceneComponent != null) {
      sceneComponent.used = true;
      sceneComponent.updateFrom(component);
    }
    else {
      sceneComponent = new SceneComponent(this, component);
    }
    int numChildren = component.getChildCount();
    for (int i = 0; i < numChildren; i++) {
      SceneComponent child = updateFromComponent(component.getChild(i));
      if (child.getParent() != sceneComponent) {
        sceneComponent.addChild(child);
      }
    }
    return sceneComponent;
  }

  /////////////////////////////////////////////////////////////////////////////
  // NlModel listeners callbacks
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void modelChanged(@NotNull NlModel model) {
    // Do nothing
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    updateFrom(model);
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    boolean previous = myAnimate;
    myAnimate = animate;
    updateFrom(model);
    myAnimate = previous;
  }
}
