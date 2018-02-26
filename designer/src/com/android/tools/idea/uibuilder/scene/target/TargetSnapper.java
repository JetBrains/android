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

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * {@link Target} that are linked with {@link SceneComponent} providing {@link Notch}s ({@link Notch.Provider})
 * shall use this class to interact with the {@link Notch} and use {@link TargetSnapper} to snap to {@link Notch}
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

  private final List<Notch> myHorizontalNotches = new ArrayList<>();
  private final List<Notch> myVerticalNotches = new ArrayList<>();
  @Nullable private Notch myCurrentNotchY;
  @Nullable private Notch myCurrentNotchX;
  private int myNotchesSourcesMask = ALL;

  /**
   * Runs the {@link Notch.Action} associated with the snapped Notch(es).
   *
   * @param attributes The component's attribute
   */
  public void applyNotches(@NotNull AttributesTransaction attributes) {
    if (myCurrentNotchX != null) {
      myCurrentNotchX.applyAction(attributes);
      myCurrentNotchX = null;
    }
    if (myCurrentNotchY != null) {
      myCurrentNotchY.applyAction(attributes);
      myCurrentNotchY = null;
    }
  }

  /**
   * Try to find a Notch that can snap the given x coordinate.
   *
   * @param x The coordinate to snap
   * @return x if the coordinate has not been snapped or the snapped {@link Notch} value if a Notch was snapped
   */
  @AndroidDpCoordinate
  public int trySnapX(@AndroidDpCoordinate int x) {
    for (Notch notch : myHorizontalNotches) {
      x = notch.trySnap(x);
      if (notch.didApply()) {
        myCurrentNotchX = notch;
        return x;
      }
    }
    myCurrentNotchX = null;
    return x;
  }

  /**
   * Try to find a Notch that can snap the given y coordinate.
   *
   * @param y The coordinate to snap
   * @return x if the coordinate has not been snapped or the snapped {@link Notch} value if a Notch was snapped
   */
  @AndroidDpCoordinate
  public int trySnapY(@AndroidDpCoordinate int y) {
    for (Notch notch : myVerticalNotches) {
      y = notch.trySnap(y);
      if (notch.didApply()) {
        myCurrentNotchY = notch;
        return y;
      }
    }
    myCurrentNotchY = null;
    return y;
  }

  /**
   * Gather all possible Notches for the given {@link SceneComponent}.
   *
   * The {@link Notch}es are gathered from the parent, parent's targets, siblings and siblings' target.
   * The sources from which {@link Notch}es are gathered can be filtered using {@link #setSources(int)}
   *
   * @param snappable The component to gateher the {@link Notch} for
   */
  public void gatherNotches(@NotNull SceneComponent snappable) {
    myCurrentNotchX = null;
    myCurrentNotchY = null;
    myHorizontalNotches.clear();
    myVerticalNotches.clear();
    SceneComponent parent = snappable.getParent();
    if (parent == null) {
      return;
    }
    if ((myNotchesSourcesMask & PARENT) > 0) {
      Notch.Provider notchProvider = parent.getNotchProvider();
      if (notchProvider != null) {
        notchProvider.fill(parent, snappable, myHorizontalNotches, myVerticalNotches);
      }
    }

    if ((myNotchesSourcesMask & PARENT_TARGET) > 0) {
      gatherNotchFromTargets(parent, snappable, myHorizontalNotches, myVerticalNotches);
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
            provider.fill(child, snappable, myHorizontalNotches, myVerticalNotches);
          }
        }

        if ((myNotchesSourcesMask & CHILD_TARGET) > 0) {
          gatherNotchFromTargets(child, snappable, myHorizontalNotches, myVerticalNotches);
        }
      }
    }
  }

  /**
   * Set the sources from which the Notches should be gathered.
   * By default, all sources are considered.
   *
   * @param sourceMask A mask made from {@link #PARENT}, {@link #PARENT_TARGET},
   *                   {@link #CHILD},{@link #CHILD_TARGET},
   */
  public void setSources(int sourceMask) {
    myNotchesSourcesMask = sourceMask;
  }

  private static void gatherNotchFromTargets(@NotNull SceneComponent owner,
                                             @NotNull SceneComponent snappableComponent,
                                             @NotNull List<Notch> horizontalNotches,
                                             @NotNull List<Notch> verticalNotches) {
    for (Target target : owner.getTargets()) {
      if (target instanceof Notch.Provider) {
        ((Notch.Provider)target).fill(owner, snappableComponent, horizontalNotches, verticalNotches);
      }
    }
  }

  /**
   * Render the notches if one of them has been selected when calling {@link #trySnapX(int)}
   * or {@link #trySnapY(int)}
   *
   * @param list         The {@link DisplayList} used to render the node
   * @param sceneContext The current {@link SceneContext} where the Notches will be shown
   * @param component    The component used to measure the Notch rendering dimensions
   */
  public void renderCurrentNotches(@NotNull DisplayList list, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    if (myCurrentNotchX != null) {
      myCurrentNotchX.render(list, sceneContext, component);
    }
    if (myCurrentNotchY != null) {
      myCurrentNotchY.render(list, sceneContext, component);
    }
  }

  /**
   * @return The snapped {@link Notch} after a call to {@link #trySnapX(int)} or null if no {@link Notch} was selected.
   * <p> If {@link #applyNotches(AttributesTransaction)} was called, it will return null
   */
  @Nullable
  public Notch getSnappedNotchX() {
    return myCurrentNotchX;
  }

  /**
   * @return The snapped {@link Notch} after a call to {@link #trySnapY(int)} or null if no {@link Notch} was selected.
   * <p> If {@link #applyNotches(AttributesTransaction)} was called, it will return null
   */
  @Nullable
  public Notch getSnappedNotchY() {
    return myCurrentNotchY;
  }

  public void cleanNotch() {
    myCurrentNotchX = null;
    myCurrentNotchY = null;
  }
}
