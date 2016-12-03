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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Base implementation of a Target
 */
public abstract class BaseTarget implements Target {

  protected SceneComponent myComponent;
  protected float myLeft = 0;
  protected float myTop = 0;
  protected float myRight = 0;
  protected float myBottom = 0;
  protected boolean mIsOver = false;

  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  @Override
  public SceneComponent getComponent() { return myComponent; }

  @Override
  public void setOver(boolean over) {
    if (over != mIsOver && myComponent != null) {
      myComponent.getScene().repaint();
    }
    mIsOver = over;
  }

  public float getCenterX() {
    return myLeft + (myRight - myLeft) / 2;
  }

  public float getCenterY() {
    return myTop + (myBottom - myTop) / 2;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getMouseCursor() {
    return Cursor.DEFAULT_CURSOR;
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    picker.addRect(this, 0, transform.getSwingX(myLeft), transform.getSwingY(myTop),
                   transform.getSwingX(myRight), transform.getSwingY(myBottom));
  }

  //endregion
}
