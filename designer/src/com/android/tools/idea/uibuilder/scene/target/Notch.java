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

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawHorizontalNotch;
import com.android.tools.idea.uibuilder.scene.draw.DrawVerticalNotch;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Used to snap component during a drag
 */
public abstract class Notch {

  protected static final int DEFAULT_GAP = 8;

  /**
   * Indicate that this {@link Notch} is vertical and only cares about the value on X-axis
   * Component snap to this notch when distance of X-axis is smaller than gap.
   *
   * @see #setGap(int)
   */
  static final int TYPE_HORIZONTAL = 1;

  /**
   * Indicate that this {@link Notch} is vertical and only cares about the value on Y-axis.
   * Component snap to this notch when distance of Y-axis is smaller than gap.
   *
   * @see #setGap(int)
   */
  static final int TYPE_VERTICAL = 1 << 1;

  /**
   * Indicate that this {@link Notch} is circle and it cares about the value on X-axis and Y-axis.
   * Component snap to this notch when distance to (x, y) point is smaller than gap.
   *
   * @see #setGap(int)
   */
  static final int TYPE_CIRCLE = 1 << 2;

  public interface Action {
    void apply(@NotNull NlAttributesHolder attributes);
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
     * @param notchBuilder       Builder to collect Notches. Provider should fill up its Notches into this builder.
     */
    void fill(@NotNull SceneComponent owner,
              @NotNull SceneComponent snappableComponent,
              @NotNull ImmutableList.Builder<Notch> notchBuilder);
  }

  @NotNull protected SceneComponent myOwner;
  @MagicConstant(flags = {TYPE_HORIZONTAL, TYPE_VERTICAL}) protected int myType;
  protected int myGap = DEFAULT_GAP;
  @Nullable Action myAction;
  @Nullable Target myTarget;

  /**
   * Create a new notch associated with the provided {@link SceneComponent} owner.
   *
   * @param owner         The {@link SceneComponent} holding the notch
   * @param type          The Notch type, should be one of {@link #TYPE_HORIZONTAL} or {@link #TYPE_VERTICAL}
   * @param action        The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called
   */
  protected Notch(@NotNull SceneComponent owner,
                  @MagicConstant(flags = {TYPE_HORIZONTAL, TYPE_VERTICAL, TYPE_CIRCLE}) int type,
                  @Nullable Action action) {
    myOwner = owner;
    myType = type;
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
  public void applyAction(@NotNull NlAttributesHolder attributes) {
    if (myAction != null) {
      myAction.apply(attributes);
    }
  }

  /**
   * @param valueX   The value on x-axis to try to snap
   * @param valueY   The value on y-axis to try to snap
   * @param retPoint The point given by caller to fill up the snapped point
   *
   * @return true if (valueX, valueY) is snappable to this Notch, false otherwise
   */
  abstract boolean isSnappable(@AndroidDpCoordinate int valueX,
                               @AndroidDpCoordinate int valueY,
                               @AndroidDpCoordinate @NotNull Point retPoint);

  @MagicConstant(flags = {TYPE_HORIZONTAL, TYPE_VERTICAL})
  public final int getType() {
    return myType;
  }

  public final void setTarget(@Nullable Target target) {
    myTarget = target;
  }

  @Nullable
  public final Target getTarget() {
    return myTarget;
  }

  public abstract void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component);

  /**
   * A notch snapping on a motion on the <B>X (horizontal) axis</B>, so the line will be <b>vertical</b>.
   */
  public static class Horizontal extends Notch {

    @AndroidDpCoordinate protected int myValueX;
    @AndroidDpCoordinate protected int myDisplayValueX;

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner         The {@link SceneComponent} holding the notch
     * @param valueX        The position where element will be snapped on x-axis
     * @param displayValueX The position where the Notch will be displayed on x-axis
     */
    public Horizontal(@NotNull SceneComponent owner, @AndroidDpCoordinate int valueX, @AndroidDpCoordinate int displayValueX) {
      this(owner, valueX, displayValueX, null);
    }

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner         The {@link SceneComponent} holding the notch
     * @param valueX        The position where element will be snapped on x-axis
     * @param displayValueX The position where the Notch will be displayed on x-axis
     * @param action        The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called.
     */
    public Horizontal(@NotNull SceneComponent owner,
                      @AndroidDpCoordinate int valueX,
                      @AndroidDpCoordinate int displayValueX,
                      @Nullable Action action) {
      super(owner, TYPE_HORIZONTAL, action);
      myValueX = valueX;
      myDisplayValueX = displayValueX;
    }

    @Override
    public boolean isSnappable(@AndroidDpCoordinate int valueX,
                               @AndroidDpCoordinate int valueY,
                               @AndroidDpCoordinate @NotNull Point retPoint) {
      if (Math.abs(valueX - myValueX) <= myGap) {
        retPoint.x = myValueX;
        retPoint.y = valueY;
        return true;
      }
      return false;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      if (parent != null) {
        DrawVerticalNotch.add(list, context, myDisplayValueX, parent.getDrawY(), parent.getDrawY() + parent.getDrawHeight());
      }
    }
  }

  /**
   * A notch snapping on a motion on the <B>Y (vertical) axis</B>, so the line will be <b>horizontal</b>.
   */
  public static class Vertical extends Notch {

    @AndroidDpCoordinate protected int myValueY;
    @AndroidDpCoordinate protected int myDisplayValueY;

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner         The {@link SceneComponent} holding the notch
     * @param valueY        The position where element will be snapped on y-axis
     * @param displayValueY The position where the Notch will be displayed on y-axis
     */
    public Vertical(@NotNull SceneComponent owner, @AndroidDpCoordinate int valueY, @AndroidDpCoordinate int displayValueY) {
      this(owner, valueY, displayValueY, null);
    }

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner         The {@link SceneComponent} holding the notch
     * @param valueY        The position where element will be snapped on y-axis
     * @param displayValueY The position where the Notch will be displayed on y-axis
     * @param action        The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called.
     */
    public Vertical(@NotNull SceneComponent owner,
                    @AndroidDpCoordinate int valueY,
                    @AndroidDpCoordinate int displayValueY,
                    @Nullable Action action) {
      super(owner, TYPE_VERTICAL, action);
      myValueY = valueY;
      myDisplayValueY = displayValueY;
    }

    @Override
    public boolean isSnappable(@AndroidDpCoordinate int valueX,
                               @AndroidDpCoordinate int valueY,
                               @AndroidDpCoordinate @NotNull Point retPoint) {
      if (Math.abs(valueY - myValueY) <= myGap) {
        retPoint.x = valueX;
        retPoint.y = myValueY;
        return true;
      }
      return false;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      SceneComponent parent = component.getParent();
      if (parent != null) {
        DrawHorizontalNotch.add(list, context, parent.getDrawX(), myDisplayValueY, parent.getDrawX() + parent.getDrawWidth());
      }
    }
  }

  public static class Circle extends Notch {
    @AndroidDpCoordinate private int myValueX;
    @AndroidDpCoordinate private int myValueY;

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner  The {@link SceneComponent} holding the notch
     * @param valueX The position where element will be snapped on x-axis
     * @param valueY The position where element will be snapped on y-axis
     */
    public Circle(@NotNull SceneComponent owner,
                  @AndroidDpCoordinate int valueX,
                  @AndroidDpCoordinate int valueY) {
      this(owner, valueX, valueY, null);
    }

    /**
     * Create a new notch associated with the provided {@link SceneComponent} owner.
     *
     * @param owner  The {@link SceneComponent} holding the notch
     * @param valueX The position where element will be snapped on x-axis
     * @param valueY The position where element will be snapped on y-axis
     * @param action The {@link Action} to execute when {@link #applyAction(AttributesTransaction)} is called.
     */
    public Circle(@NotNull SceneComponent owner,
                  @AndroidDpCoordinate int valueX,
                  @AndroidDpCoordinate int valueY,
                  @Nullable Action action) {
      super(owner, TYPE_CIRCLE, action);
      myValueX = valueX;
      myValueY = valueY;
    }

    @Override
    public boolean isSnappable(@AndroidDpCoordinate int valueX,
                               @AndroidDpCoordinate int valueY,
                               @AndroidDpCoordinate @NotNull Point retPoint) {
      int dx = valueX - myValueX;
      int dy = valueY - myValueY;
      if (dx * dx + dy * dy <= myGap * myGap) {
        retPoint.x = myValueX;
        retPoint.y = myValueY;
        return true;
      }
      return false;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      // Do nothing.
    }
  }

  public static class SmallHorizontal extends Horizontal {
    public SmallHorizontal(@NotNull SceneComponent owner, @AndroidDpCoordinate int valueX, @AndroidDpCoordinate int displayValueX) {
      super(owner, valueX, displayValueX, null);
      myGap = 6;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      int gap = 16;
      int y1 = Math.min(myOwner.getDrawY(), component.getDrawY()) - gap;
      int y2 = Math.max(myOwner.getDrawY() + myOwner.getDrawHeight(), component.getDrawY() + component.getDrawHeight()) + gap;
      DrawVerticalNotch.add(list, context, myDisplayValueX, y1, y2);
    }
  }

  public static class SmallVertical extends Vertical {
    public SmallVertical(@NotNull SceneComponent owner, @AndroidDpCoordinate int valueY, @AndroidDpCoordinate int displayValueY) {
      super(owner, valueY, displayValueY, null);
      myGap = 6;
    }

    @Override
    public void render(@NotNull DisplayList list, @NotNull SceneContext context, @NotNull SceneComponent component) {
      int gap = 16;
      int x1 = Math.min(myOwner.getDrawX(), component.getDrawX()) - gap;
      int x2 = Math.max(myOwner.getDrawX() + myOwner.getDrawWidth(), component.getDrawX() + component.getDrawWidth()) + gap;
      DrawHorizontalNotch.add(list, context, x1, myDisplayValueY, x2);
    }
  }
}
