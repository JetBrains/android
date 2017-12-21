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
package com.android.tools.idea.common.scene;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Hit listener implementation (used for hover / click detection)
 */
class SceneHitListener implements ScenePicker.HitElementListener {
  @NotNull private SelectionModel mySelectionModel;
  private ScenePicker myPicker = new ScenePicker();
  double myClosestComponentDistance = Double.MAX_VALUE;
  double myClosestTargetDistance = Double.MAX_VALUE;
  ArrayList<SceneComponent> myHitComponents = new ArrayList<>();
  ArrayList<Target> myHitTargets = new ArrayList<>();
  Target mySkipTarget = null;

  public SceneHitListener(@NotNull SelectionModel selectionModel) {
    mySelectionModel = selectionModel;
    myPicker.setSelectListener(this);
  }

  public void skipTarget(Target target) {
    mySkipTarget = target;
  }

  public void find(@NotNull SceneContext transform,
                   @NotNull SceneComponent root,
                   @AndroidDpCoordinate int x,
                   @AndroidDpCoordinate int y) {
    myHitComponents.clear();
    myHitTargets.clear();
    myClosestComponentDistance = Double.MAX_VALUE;
    myClosestTargetDistance = Double.MAX_VALUE;
    myPicker.reset();
    root.addHit(transform, myPicker);
    myPicker.find(transform.getSwingXDip(x), transform.getSwingYDip(y));
  }

  @Override
  public void over(Object over, double dist) {
    if (over instanceof Target) {
      if (mySkipTarget == over) {
        return;
      }
      Target target = (Target)over;
      if (dist < myClosestTargetDistance) {
        myHitTargets.clear();
        myHitTargets.add(target);
        myClosestTargetDistance = dist;
      }
      else if (dist == myClosestTargetDistance) {
        myHitTargets.add(target);
      }
    }
    else if (over instanceof SceneComponent) {
      SceneComponent component = (SceneComponent)over;
      if (dist < myClosestComponentDistance) {
        myHitComponents.clear();
        myHitComponents.add(component);
        myClosestComponentDistance = dist;
      }
      else if (dist == myClosestComponentDistance) {
        myHitComponents.add(component);
      }
    }
  }

  /**
   * Return the "best" target among the list of targets found by the hit detector.
   * If more than one target have been found, we pick the top-level target preferably,
   * unless there's a component selected (in that case, we pick the best top-level target belonging
   * to a component in the selection)
   *
   * @return preferred target
   */
  public Target getClosestTarget() {
    int count = myHitTargets.size();
    if (count == 0) {
      return null;
    }
    if (count == 1) {
      return myHitTargets.get(0);
    }
    List<NlComponent> selection = mySelectionModel.getSelection();
    if (selection.isEmpty()) {
      Target candidate = myHitTargets.get(count - 1);
      for (int i = count - 2; i >= 0; i--) {
        Target target = myHitTargets.get(i);
        if (target.getPreferenceLevel() > candidate.getPreferenceLevel()) {
          candidate = target;
        }
      }
      return candidate;
    }
    Target candidate = myHitTargets.get(count - 1);
    boolean inSelection = selection.contains(candidate.getComponent().getNlComponent());

    for (int i = count - 2; i >= 0; i--) {
      Target target = myHitTargets.get(i);
      if (!selection.contains(target.getComponent().getNlComponent())) {
        continue;
      }
      if (!inSelection || target.getPreferenceLevel() > candidate.getPreferenceLevel()) {
        candidate = target;
        inSelection = true;
      }
    }
    return candidate;
  }

  /**
   * Return a target out of the list of hit targets that doesn't
   * include filteredTarget -- unless that's the only choice. The idea is that when dealing with targets,
   * if there's overlap, you don't want to pick on mouseRelease the same one you already clicked on in mouseDown.
   *
   * @param filteredTarget
   * @return the preferred target out of the list
   */
  public Target getFilteredTarget(Target filteredTarget) {
    Target hit = null;
    boolean found = false;
    for (Target target : myHitTargets) {
      if (target == filteredTarget) {
        found = true;
        continue;
      }
      if (filteredTarget.getClass().isAssignableFrom(target.getClass())) {
        hit = target;
      }
    }
    if (hit == null && found) {
      hit = filteredTarget;
    }
    return hit;
  }

  /**
   * We want to get the best component, defined as the top-level one (in the draw order) and
   * a preference to the selected one (in case multiple components overlap)
   *
   * @return the best component to pick
   */
  public SceneComponent getClosestComponent() {
    int count = myHitComponents.size();
    if (count == 0) {
      return null;
    }
    if (count == 1) {
      return myHitComponents.get(0);
    }
    List<NlComponent> selection = mySelectionModel.getSelection();
    if (selection.isEmpty()) {
      return myHitComponents.get(count - 1);
    }
    SceneComponent candidate = myHitComponents.get(count - 1);
    boolean inSelection = selection.contains(candidate.getNlComponent());
    if (inSelection) {
      return candidate;
    }
    for (int i = count - 1; i >= 0; i--) {
      SceneComponent target = myHitComponents.get(i);
      if (parentInSelection(target, selection)) {
        return target;
      }
    }
    return candidate;
  }

  private static boolean parentInSelection(@NotNull SceneComponent component, @NotNull List<NlComponent> selection) {
    while (component != null) {
      if (selection.contains(component.getNlComponent())) {
        return true;
      }
      component = component.getParent();
    }
    return false;
  }
}
