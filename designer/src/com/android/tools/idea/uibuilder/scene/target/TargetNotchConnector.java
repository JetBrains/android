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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.Notch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

/**
 * {@link Target} that are linked with {@link SceneComponent} providing {@link Notch}s ({@link Notch.Provider})
 * shall use this class to interact with the {@link Notch} and shall implement {@link NotchConnectable} to
 * show that they are using {@link Notch}
 */
public class TargetNotchConnector {

  private final ArrayList<Notch> myHorizontalNotches = new ArrayList<>();
  private final ArrayList<Notch> myVerticalNotches = new ArrayList<>();
  @Nullable private Notch myCurrentNotchY;
  @Nullable private Notch myCurrentNotchX;


  public Point applyNotches(SceneComponent component, AttributesTransaction attributes, int mouseX, int mouseY) {
    return applyNotches(component, attributes, new Point(mouseX, mouseY));
  }

  public Point applyNotches(SceneComponent component, AttributesTransaction attributes, Point snapLocation) {
    if (myCurrentNotchX != null) {
      snapLocation.x = myCurrentNotchX.trySnap(snapLocation.x);
      if (component.allowsAutoConnect()) {
        myCurrentNotchX.applyAction(attributes);
      }
      myCurrentNotchX = null;
    }
    if (myCurrentNotchY != null) {
      snapLocation.y = myCurrentNotchY.trySnap(snapLocation.y);
      if (component.allowsAutoConnect()) {
        myCurrentNotchY.applyAction(attributes);
      }
      myCurrentNotchY = null;
    }
    return snapLocation;
  }

  public int trySnap(int dx) {
    int count = myHorizontalNotches.size();
    for (int i = 0; i < count; i++) {
      Notch notch = myHorizontalNotches.get(i);
      int x = notch.trySnap(dx);
      if (notch.didApply()) {
        myCurrentNotchX = notch;
        return x;
      }
    }
    myCurrentNotchX = null;
    return dx;
  }

  public int trySnapY(int dy) {
    int count = myVerticalNotches.size();
    for (int i = 0; i < count; i++) {
      Notch notch = myVerticalNotches.get(i);
      int y = notch.trySnap(dy);
      if (notch.didApply()) {
        myCurrentNotchY = notch;
        return y;
      }
    }
    myCurrentNotchY = null;
    return dy;
  }

  public void gatherNotches(SceneComponent component) {
    myCurrentNotchX = null;
    myCurrentNotchY = null;
    myHorizontalNotches.clear();
    myVerticalNotches.clear();
    SceneComponent parent = component.getParent();
    if (parent == null) {
      return;
    }
    Notch.Provider notchProvider = parent.getNotchProvider();
    if (notchProvider != null) {
      notchProvider.fill(parent, component, myHorizontalNotches, myVerticalNotches);
    }
    int count = parent.getChildCount();
    for (int i = 0; i < count; i++) {
      SceneComponent child = parent.getChild(i);
      if (child == component) {
        continue;
      }
      Notch.Provider provider = child.getNotchProvider();
      if (provider != null) {
        provider.fill(child, component, myHorizontalNotches, myVerticalNotches);
      }
    }
  }

  /**
   * Render the notches if one of them has been selected when calling {@link #trySnap(int)}
   * or {@link #trySnapY(int)}
   *
   * @param list The {@link DisplayList} used to render the node
   * @param sceneContext The current {@link SceneContext} where the Notches will be shown
   * @param component The component used to measure the Notch rendering dimensions
   */
  public void renderCurrentNotches(DisplayList list, SceneContext sceneContext, SceneComponent component) {
    if (myCurrentNotchX != null) {
      myCurrentNotchX.render(list, sceneContext, component);
    }
    if (myCurrentNotchY != null) {
      myCurrentNotchY.render(list, sceneContext, component);
    }
  }

  /**
   * A target which interacts with {@link Notch}es should implement this class
   * to provide a {@link TargetNotchConnector}.
   *
   * This is not mandatory but it helps inheriting classes to know that a parent class can provide a
   * Notches interaction that might be useful.
   */
  public interface NotchConnectable {

    @NotNull
    TargetNotchConnector getTargetNotchConnector();
  }
}
