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
package com.android.tools.idea.uibuilder.statelist;

import android.view.View;
import android.widget.ImageView;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.primitives.Ints;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ToggleStateAction extends ToggleAction {
  private final State myState;
  private final DesignSurface mySurface;

  ToggleStateAction(@NotNull State state, @NotNull DesignSurface surface) {
    super(state.getText(), null, EmptyIcon.ICON_0);

    myState = state;
    mySurface = surface;
  }

  @Override
  public boolean isSelected(@Nullable AnActionEvent event) {
    View image = getImageView();
    return image != null && Ints.contains(image.getDrawableState(), myState.getIntValue());
  }

  @Override
  public void setSelected(@Nullable AnActionEvent event, boolean selected) {
    ImageView image = getImageView();

    if (image == null) {
      return;
    }

    int[] states = image.getDrawableState();
    int state = myState.getIntValue();

    // image.setImageState(states, true) didn't work as expected. So I'm doing it this way.

    if (selected) {
      assert !Ints.contains(states, state);
      image.setImageState(ArrayUtil.append(states, state), false);
    }
    else {
      assert Ints.contains(states, state);

      int i = Ints.indexOf(states, state);
      image.setImageState(ArrayUtil.remove(states, i), false);
    }

    SceneManager manager = mySurface.getSceneManager();

    if (manager == null) {
      return;
    }

    manager.requestRender();
  }

  @Nullable
  private ImageView getImageView() {
    LayoutlibSceneManager manager = (LayoutlibSceneManager)mySurface.getSceneManager();

    if (manager == null) {
      return null;
    }

    RenderResult result = manager.getRenderResult();

    if (result == null) {
      return null;
    }

    List<ViewInfo> views = result.getRootViews();

    if (views.isEmpty()) {
      return null;
    }

    Object view = views.get(0).getViewObject();

    if (!(view instanceof ImageView)) {
      return null;
    }

    return (ImageView)view;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }
}
