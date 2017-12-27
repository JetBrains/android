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

import android.widget.ImageView;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ToggleStateActionTest {
  @Test
  public void isSelectedImageIsNull() {
    ToggleAction action = new ToggleStateAction(State.PRESSED, mockSurface(null));
    assertFalse(action.isSelected(null));
  }

  @Test
  public void isSelectedImageIsNotPressed() {
    ImageView image = mockImage(new int[0]);
    ToggleAction action = new ToggleStateAction(State.PRESSED, mockSurface(image));

    assertFalse(action.isSelected(null));
  }

  @Test
  public void isSelectedImageIsPressed() {
    ImageView image = mockImage(new int[]{State.PRESSED.getIntValue()});
    ToggleAction action = new ToggleStateAction(State.PRESSED, mockSurface(image));

    assertTrue(action.isSelected(null));
  }

  @Test
  public void setSelectedImageIsPressed() {
    ImageView image = mockImage(new int[]{State.PRESSED.getIntValue()});
    ToggleAction action = new ToggleStateAction(State.PRESSED, mockSurface(image));

    action.setSelected(null, false);
    Mockito.verify(image).setImageState(new int[0], false);
  }

  @Test
  public void setSelectedImageIsNotPressed() {
    ImageView image = mockImage(new int[0]);
    ToggleAction action = new ToggleStateAction(State.PRESSED, mockSurface(image));

    action.setSelected(null, true);
    Mockito.verify(image).setImageState(new int[]{State.PRESSED.getIntValue()}, false);
  }

  @NotNull
  private static ImageView mockImage(@NotNull int[] states) {
    ImageView image = Mockito.mock(ImageView.class);
    Mockito.when(image.getDrawableState()).thenReturn(states);

    return image;
  }

  @NotNull
  private static DesignSurface mockSurface(@Nullable ImageView image) {
    DesignSurface surface = Mockito.mock(DesignSurface.class);

    if (image == null) {
      return surface;
    }

    ViewInfo imageViewInfo = Mockito.mock(ViewInfo.class);
    Mockito.when(imageViewInfo.getViewObject()).thenReturn(image);

    RenderResult result = Mockito.mock(RenderResult.class);
    Mockito.when(result.getRootViews()).thenReturn(Collections.singletonList(imageViewInfo));

    LayoutlibSceneManager manager = Mockito.mock(LayoutlibSceneManager.class);
    Mockito.when(manager.getRenderResult()).thenReturn(result);

    Mockito.when(surface.getSceneManager()).thenReturn(manager);
    return surface;
  }
}
