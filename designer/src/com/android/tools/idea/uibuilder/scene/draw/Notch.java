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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public abstract class Notch {

  public interface Action {
    void apply(@NotNull AttributesTransaction attributes);
  }

  public interface Provider {
    void fill(@NotNull SceneComponent component, @NotNull SceneComponent target,
              @NotNull ArrayList<Notch> horizontalNotches, @NotNull ArrayList<Notch> verticalNotches);
  }

  SceneComponent myOwner;

  int myNotchValue;
  int myDisplayValue;
  int myGap = 8;
  Action myAction;
  boolean myDidApply = false;

  private Notch() {
  }

  private Notch(@NotNull SceneComponent owner, int value, int displayValue) {
    myOwner = owner;
    myNotchValue = value;
    myDisplayValue = displayValue;
  }

  private Notch(@NotNull SceneComponent owner, int value, int displayValue, @NotNull Action action) {
    myOwner = owner;
    myNotchValue = value;
    myDisplayValue = displayValue;
    myAction = action;
  }

  public void setAction(@Nullable Action action) {
    myAction = action;
  }

  public void apply(AttributesTransaction attributes) {
    if (myDidApply && myAction != null) {
      myAction.apply(attributes);
    }
  }

  public boolean didApply() { return myDidApply; }

  public int apply(int value) {
    myDidApply = false;
    if (Math.abs(value - myNotchValue) <= myGap) {
      myDidApply = true;
      return myNotchValue;
    }
    return value;
  }

  public abstract void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component);

  public static class Horizontal extends Notch {
    public Horizontal(@NotNull SceneComponent owner, int value, int displayValue) {
      super(owner, value, displayValue);
    }

    public Horizontal(@NotNull SceneComponent owner, int value, int displayValue, @NotNull Action action) {
      super(owner, value, displayValue, action);
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      DrawVerticalNotch.add(list, context, myDisplayValue, parent.getDrawY(),
                            parent.getDrawY() + parent.getDrawHeight());
    }
  }

  public static class Vertical extends Notch {
    public Vertical(@NotNull SceneComponent owner, int value, int displayValue) {
      super(owner, value, displayValue);
    }

    public Vertical(@NotNull SceneComponent owner, int value, int displayValue, @NotNull Action action) {
      super(owner, value, displayValue, action);
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      DrawHorizontalNotch.add(list, context, parent.getDrawX(), myDisplayValue,
                              parent.getDrawX() + parent.getDrawWidth());
    }
  }

  public static class SmallHorizontal extends Notch {
    public SmallHorizontal(@NotNull SceneComponent owner, int value, int displayValue) {
      super(owner, value, displayValue);
      myGap = 6;
    }

    public SmallHorizontal(@NotNull SceneComponent owner, int value, int displayValue, @NotNull Action action) {
      super(owner, value, displayValue, action);
      myGap = 6;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      int gap = 16;
      int y1 = Math.min(myOwner.getDrawY(), component.getDrawY()) - gap;
      int y2 = Math.max(myOwner.getDrawY() + myOwner.getDrawHeight(), component.getDrawY() + component.getDrawHeight()) + gap;
      DrawVerticalNotch.add(list, context, myDisplayValue, y1, y2);
    }
  }

  public static class SmallVertical extends Notch {
    public SmallVertical(@NotNull SceneComponent owner, int value, int displayValue) {
      super(owner, value, displayValue);
      myGap = 6;
    }

    public SmallVertical(@NotNull SceneComponent owner, int value, int displayValue, @NotNull Action action) {
      super(owner, value, displayValue, action);
      myGap = 6;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      int gap = 16;
      int x1 = Math.min(myOwner.getDrawX(), component.getDrawX()) - gap;
      int x2 = Math.max(myOwner.getDrawX() + myOwner.getDrawWidth(), component.getDrawX() + component.getDrawWidth()) + gap;
      DrawHorizontalNotch.add(list, context, x1, myDisplayValue, x2);
    }
  }
}