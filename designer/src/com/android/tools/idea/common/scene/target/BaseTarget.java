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
package com.android.tools.idea.common.scene.target;

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import java.awt.Cursor;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base implementation of a Target
 */
public abstract class BaseTarget implements Target {

  protected SceneComponent myComponent;
  @AndroidDpCoordinate protected float myLeft = 0;
  @AndroidDpCoordinate protected float myTop = 0;
  @AndroidDpCoordinate protected float myRight = 0;
  @AndroidDpCoordinate protected float myBottom = 0;
  protected boolean mIsOver = false;

  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  @Override
  public SceneComponent getComponent() {
    return myComponent;
  }

  @Override
  public void setMouseHovered(boolean over) {
    if (over != mIsOver && myComponent != null) {
      myComponent.getScene().repaint();
    }
    mIsOver = over;
  }

  @Override
  public boolean isMouseHovered() {
    return mIsOver;
  }

  @Override
  @AndroidDpCoordinate
  public float getCenterX() {
    return myLeft + (myRight - myLeft) / 2;
  }

  @Override
  @AndroidDpCoordinate
  public float getCenterY() {
    return myTop + (myBottom - myTop) / 2;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public Cursor getMouseCursor(@JdkConstants.InputEventMask int modifiersEx) {
    return Cursor.getDefaultCursor();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation adds a hittable rectangle with the same bounds as this target.
   * </p>
   */
  @Override
  public void addHit(@NotNull SceneContext transform,
                     @NotNull ScenePicker picker,
                     @JdkConstants.InputEventMask int modifiersEx) {
    if (isHittable()) {
      picker.addRect(this, 0, transform.getSwingXDip(myLeft), transform.getSwingYDip(myTop),
                     transform.getSwingXDip(myRight), transform.getSwingYDip(myBottom));
    }
  }

  /**
   * @return True if this {@link Target} is hittable, false otherwise.
   */
  protected boolean isHittable() {
    return getComponent().getScene().getFilterType() == Scene.FilterType.ALL;
  }

  @Override
  @Nullable
  public String getToolTipText() {
    return null;
  }

  //endregion
}
