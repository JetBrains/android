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
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link Target} that are linked with {@link SceneComponent} providing {@link Notch}s ({@link Notch.Provider})
 * shall use this class to interact with the {@link Notch} and use {@link TargetSnapper} to snap to {@link Notch}
 *
 * @see Notch
 */
public class TargetSnapper {

  /**
   * Mask to gather the {@link Notch}es from the parent of the snappable component
   * when calling {@link #gatherNotches(SceneComponent)}
   */
  public static final int PARENT = 1;

  /**
   * Mask to gather the {@link Notch}es from the parent's targets of the snappable component
   * when calling {@link #gatherNotches(SceneComponent)}
   *
   * @see SceneComponent#getTargets()
   */
  public static final int PARENT_TARGET = 1 << 1;

  /**
   * Mask to gather the {@link Notch}es from the siblings of the snappable component
   * when calling {@link #gatherNotches(SceneComponent)}
   */
  public static final int CHILD = 1 << 2;

  /**
   * Mask to gather the {@link Notch}es from the siblings's targets of the snappable component
   * when calling {@link #gatherNotches(SceneComponent)}
   *
   * @see SceneComponent#getTargets()
   */
  public static final int CHILD_TARGET = 1 << 3;

  public static final int ALL = PARENT | PARENT_TARGET | CHILD | CHILD_TARGET;

  @NotNull private ImmutableList<Notch> myNotchesForSnapping = ImmutableList.of();
  @Nullable private Notch myHorizontalNotch = null;
  @Nullable private Notch myVerticalNotch = null;
  @Nullable private Notch myCircularNotch = null;
  private int myNotchesSourcesMask = ALL;

  public void gatherNotches(@NotNull SceneComponent snappable) {
    ImmutableList.Builder<Notch> builder = new ImmutableList.Builder<>();
    SceneComponent parent = snappable.getParent();
    if (parent == null) {
      return;
    }
    if ((myNotchesSourcesMask & PARENT) > 0) {
      Notch.Provider notchProvider = parent.getNotchProvider();
      if (notchProvider != null) {
        notchProvider.fill(parent, snappable, builder);
      }
    }

    if ((myNotchesSourcesMask & PARENT_TARGET) > 0) {
      gatherNotchFromTargets(parent, snappable, builder);
    }

    if ((myNotchesSourcesMask & (CHILD_TARGET | CHILD)) > 0) {
      int count = parent.getChildCount();
      for (int i = 0; i < count; i++) {
        SceneComponent child = parent.getChild(i);
        if (child == snappable) {
          continue;
        }
        if ((myNotchesSourcesMask & CHILD) > 0) {
          Notch.Provider provider = child.getNotchProvider();
          if (provider != null) {
            provider.fill(child, snappable, builder);
          }
        }

        if ((myNotchesSourcesMask & CHILD_TARGET) > 0) {
          gatherNotchFromTargets(child, snappable, builder);
        }
      }
    }

    myNotchesForSnapping = builder.build();
  }

  private static void gatherNotchFromTargets(@NotNull SceneComponent owner,
                                             @NotNull SceneComponent snappableComponent,
                                             @NotNull ImmutableList.Builder<Notch> builder) {
    for (Target target : owner.getTargets()) {
      if (target instanceof Notch.Provider) {
        ((Notch.Provider)target).fill(owner, snappableComponent, builder);
      }
    }
  }

  /**
   * Try to find the closest horizontal {@link Notch} that can snap the given x coordinate
   *
   * @param x The coordinate to snap
   * @return Snapped x value if present
   */
  @NotNull
  @AndroidDpCoordinate
  public OptionalInt trySnapHorizontal(@AndroidDpCoordinate int x) {
    myHorizontalNotch = findSnappedNotch(Notch.TYPE_HORIZONTAL, x, -1);
    if (myHorizontalNotch != null) {
      Point p = new Point();
      myHorizontalNotch.isSnappable(x, -1, p);
      return OptionalInt.of(p.x);
    }
    return OptionalInt.empty();
  }

  /**
   * Try to find the closest vertical {@link Notch} that can snap the given y coordinate
   *
   * @param y The coordinate to snap
   * @return Snapped y value if present
   */
  @NotNull
  @AndroidDpCoordinate
  public OptionalInt trySnapVertical(@AndroidDpCoordinate int y) {
    myVerticalNotch = findSnappedNotch(Notch.TYPE_VERTICAL, -1, y);
    if (myVerticalNotch != null) {
      Point p = new Point();
      myVerticalNotch.isSnappable(-1, y, p);
      return OptionalInt.of(p.y);
    }
    return OptionalInt.empty();
  }

  /**
   * Try to find the closest circle {@link Notch} that can snap the given y coordinate.
   *
   * @param x The x coordinate to snap
   * @param y The y coordinate to snap
   * @return Snapped {@link Point} if present
   */
  @NotNull
  @AndroidDpCoordinate
  public Optional<Point> trySnapCircle(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myCircularNotch = findSnappedNotch(Notch.TYPE_CIRCLE, x, y);
    if (myCircularNotch != null) {
      Point p = new Point();
      myCircularNotch.isSnappable(x, y, p);
      return Optional.of(p);
    }
    return Optional.empty();
  }

  /**
   * Helper function to find the closest snappeble Notch for the given type.
   */
  @Nullable
  private Notch findSnappedNotch(@MagicConstant(flags = {Notch.TYPE_HORIZONTAL, Notch.TYPE_VERTICAL, Notch.TYPE_CIRCLE}) int type,
                                 @AndroidDpCoordinate int x,
                                 @AndroidDpCoordinate int y) {
    double distance = Double.MAX_VALUE;
    Notch ret = null;
    Point p = new Point();
    for (Notch notch : myNotchesForSnapping) {
      if (notch.getType() == type && notch.isSnappable(x, y, p)) {
        double d = p.distanceSq(x, y);
        if (d < distance) {
          distance = d;
          ret = notch;
        }
      }
    }
    return ret;
  }

  public void applyNotches(@NotNull NlAttributesHolder attributes) {
    if (myHorizontalNotch != null) {
      myHorizontalNotch.applyAction(attributes);
    }
    if (myVerticalNotch != null) {
      myVerticalNotch.applyAction(attributes);
    }
    if (myCircularNotch != null) {
      myCircularNotch.applyAction(attributes);
    }
  }

  public void renderSnappedNotches(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (myHorizontalNotch != null) {
      myHorizontalNotch.render(list, sceneContext, component);
    }
    if (myVerticalNotch != null) {
      myVerticalNotch.render(list, sceneContext, component);
    }
    if (myCircularNotch != null) {
      myCircularNotch.render(list, sceneContext, component);
    }
  }

  public void clearSnappedNotches() {
    myHorizontalNotch = null;
    myVerticalNotch = null;
    myCircularNotch = null;
  }

  public void reset() {
    myNotchesForSnapping = ImmutableList.of();
    clearSnappedNotches();
  }

  /**
   * Set the sources from which the Notches should be gathered.
   * By default, all sources are considered.
   *
   * @param sourceMask A mask made from {@link #PARENT}, {@link #PARENT_TARGET}, {@link #CHILD}, and {@link #CHILD_TARGET}
   */
  public void setSources(int sourceMask) {
    myNotchesSourcesMask = sourceMask;
  }
}
