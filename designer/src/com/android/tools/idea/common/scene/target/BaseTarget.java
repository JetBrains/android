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

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

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
  public boolean canChangeSelection() {
    return true;
  }

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  @Override
  public SceneComponent getComponent() {
    return myComponent;
  }

  @Override
  public void setOver(boolean over) {
    if (over != mIsOver && myComponent != null) {
      myComponent.getScene().repaint();
    }
    mIsOver = over;
  }

  @Override
  public void setExpandSize(boolean expand) {
    //do nothing
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
  public Cursor getMouseCursor() {
    return Cursor.getDefaultCursor();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation adds a hittable rectangle with the same bounds as this target.
   * </p>
   */
  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    picker.addRect(this, 0, transform.getSwingX(myLeft), transform.getSwingY(myTop),
                   transform.getSwingX(myRight), transform.getSwingY(myBottom));
  }

  @Override
  public String getToolTipText() {
    String str = myComponent.getNlComponent().getId();
    if (str == null) {
      str = myComponent.getComponentClassName();
      if (str != null) {
        str = str.substring(str.lastIndexOf('.') + 1);
      }
    }
    return str;
  }

  /**
   * Apply live and commit a list of AttributesTransaction
   */
  protected void applyAndCommit(@NotNull AttributesTransaction attributes, @NotNull String label) {
    attributes.apply();
    NlWriteCommandAction.run(attributes.getComponent(), label, attributes::commit);

    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
  }

  @Override
  public void setComponentSelection(boolean selection) {

  }

  //endregion
}
