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

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawHorizontalNotch;
import com.android.tools.idea.uibuilder.scene.draw.DrawVerticalNotch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Used to snap component during a drag
 */
public abstract class Notch {

  public interface Action {
    void apply(@NotNull AttributesTransaction attributes);
  }

  /**
   * A provider that will add Notches to component.
   */
  public interface Provider {
    /**
     * The implementing class should override this method to add {@link Notch} to the given component.
     *
     * <p>
     * The <i>target</i> is the component that will be snapped and <i>owner</i> is a reference to {@link SceneComponent}
     * holding the reference to this provider. It can be used to compute the position of the provider from its position or to access some other target.
     * </p>
     *
     * @param owner              {@link SceneComponent} holding the reference to this {@link Provider}.
     *                           It can be use to compute the position of the {@link Notch}
     * @param snappableComponent {@link SceneComponent} that will be snapped
     * @param horizontalNotches
     * @param verticalNotches
     */
    void fill(@NotNull SceneComponent owner, @NotNull SceneComponent snappableComponent,
              @NotNull ArrayList<Notch> horizontalNotches, @NotNull ArrayList<Notch> verticalNotches);
  }

  /**
   * A {@link Target} that can be snapped to a {@link Notch}es should implement this class
   * to provide a {@link TargetSnapper}.
   */
  public interface Snappable {

    @NotNull
    TargetSnapper getTargetNotchSnapper();
  }

  SceneComponent myOwner;
  int myNotchValue;
  int myDisplayValue;
  int myGap = 8;
  @Nullable Action myAction;
  boolean myDidApply = false;
  @Nullable Target myTarget;

  /**
   * Create a new notch associated with the provided {@link SceneComponent} owner.
   *
   * @param owner        The {@link SceneComponent} holding the notch
   * @param value        The position where element will be snapped
   * @param displayValue The position where the Notch will be displayed
   */
  private Notch(@NotNull SceneComponent owner, int value, int displayValue) {
    this(owner, value, displayValue, null);
  }

  /**
   * Create a new notch associated with the provided {@link SceneComponent} owner.
   *
   * @param owner        The {@link SceneComponent} holding the notch
   * @param value        The position where element will be snapped
   * @param displayValue The position where the Notch will be displayed
   * @param action       The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called.
   */
  private Notch(@NotNull SceneComponent owner, int value, int displayValue, @Nullable Action action) {
    myOwner = owner;
    myNotchValue = value;
    myDisplayValue = displayValue;
    myAction = action;
  }

  public void setGap(int gap) {
    myGap = gap;
  }

  public void setAction(@Nullable Action action) {
    myAction = action;
  }

  /**
   * Run the action provided in the constructor or by @{{@link #setAction(Action)}} if any.
   *
   * @param attributes The transaction that the {@link Action} can use to modify the component
   */
  public void applyAction(AttributesTransaction attributes) {
    if (myDidApply && myAction != null) {
      myAction.apply(attributes);
    }
  }

  public boolean didApply() {
    return myDidApply;
  }

  /**
   * Check if the provided value is close enough to this {@link Notch} coordinates and returns the
   * {@link Notch} snapping coordinate. If value is not close enough then the provided value is returned
   *
   * @param value The value to try to snap
   * @return The provided value if it is too far from the notch or the notch vlau
   */
  public int trySnap(int value) {
    myDidApply = false;
    if (Math.abs(value - myNotchValue) <= myGap) {
      myDidApply = true;
      return myNotchValue;
    }
    return value;
  }

  public void setTarget(@Nullable Target target) {
    myTarget = target;
  }

  @Nullable
  public Target getTarget() {
    return myTarget;
  }

  public abstract void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component);

  /**
   * A notch snapping on a motion on the <B>X (horizontal) axis</B>, so the line will be <b>vertical</b>.
   */
  public static class Horizontal extends Notch {
    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner        The {@link SceneComponent} holding the notch
     * @param value        The position where element will be snapped
     * @param displayValue The position where the Notch will be displayed
     */
    public Horizontal(@NotNull SceneComponent owner, int value, int displayValue) {
      this(owner, value, displayValue, null);
    }

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner        The {@link SceneComponent} holding the notch
     * @param value        The position where element will be snapped
     * @param displayValue The position where the Notch will be displayed
     * @param action       The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called.
     */
    public Horizontal(@NotNull SceneComponent owner, int value, int displayValue, @Nullable Action action) {
      super(owner, value, displayValue, action);
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      if (parent != null) {
        DrawVerticalNotch.add(list, context, myDisplayValue, parent.getDrawY(),
                              parent.getDrawY() + parent.getDrawHeight());
      }
    }
  }

  /**
   * A notch snapping on a motion on the <B>Y (vertical) axis</B>, so the line will be <b>horizontal</b>.
   */
  public static class Vertical extends Notch {

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner        The {@link SceneComponent} holding the notch
     * @param value        The position where element will be snapped
     * @param displayValue The position where the Notch will be displayed
     */
    public Vertical(@NotNull SceneComponent owner, int value, int displayValue) {
      this(owner, value, displayValue, null);
    }

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner        The {@link SceneComponent} holding the notch
     * @param value        The position where element will be snapped
     * @param displayValue The position where the Notch will be displayed
     * @param action       The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called.
     */
    public Vertical(@NotNull SceneComponent owner, int value, int displayValue, @Nullable Action action) {
      super(owner, value, displayValue, action);
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      if (parent != null) {
        DrawHorizontalNotch.add(list, context, parent.getDrawX(), myDisplayValue,
                                parent.getDrawX() + parent.getDrawWidth());
      }
    }
  }

  public static class SmallHorizontal extends Notch {
    public SmallHorizontal(@NotNull SceneComponent owner, int value, int displayValue) {
      this(owner, value, displayValue, null);
    }

    public SmallHorizontal(@NotNull SceneComponent owner, int value, int displayValue, @Nullable Action action) {
      super(owner, value, displayValue, action);
      myGap = 6;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      int gap = 16;
      int y1 = Math.min(myOwner.getDrawY(), component.getDrawY()) - gap;
      int y2 = Math.max(myOwner.getDrawY() + myOwner.getDrawHeight(), component.getDrawY() + component.getDrawHeight()) + gap;
      DrawVerticalNotch.add(list, context, myDisplayValue, y1, y2);
    }
  }

  public static class SmallVertical extends Notch {
    public SmallVertical(@NotNull SceneComponent owner, int value, int displayValue) {
      this(owner, value, displayValue, null);
    }

    public SmallVertical(@NotNull SceneComponent owner, int value, int displayValue, @Nullable Action action) {
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