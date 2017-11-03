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
package com.android.tools.idea.common.fixtures;

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.common.surface.ScrollInteraction;
import org.jetbrains.annotations.NotNull;

import static org.junit.Assert.assertNotNull;

public class ScrollFixture {
  @NotNull private final ComponentFixture myComponentFixture;
  @NotNull private final ScrollInteraction myInteraction;
  @SwingCoordinate private int myCurrentX;
  @SwingCoordinate private int myCurrentY;

  public ScrollFixture(@NotNull ScreenFixture screenView, @NotNull ComponentFixture componentFixture) {
    ScreenView screen = screenView.getScreen();
    myComponentFixture = componentFixture;

    NlComponent component = myComponentFixture.getComponent();
    //noinspection ConstantConditions
    myInteraction = ScrollInteraction.createScrollInteraction(screen, component);

    assertNotNull(myInteraction);
    // Scroll from center of component
    int startX = Coordinates.getSwingX(screen, NlComponentHelperKt.getX(component) + NlComponentHelperKt.getW(component) / 2);
    int startY = Coordinates.getSwingY(screen, NlComponentHelperKt.getY(component) + NlComponentHelperKt.getH(component) / 2);
    myInteraction.begin(startX, startY, 0);
    myCurrentX = startX;
    myCurrentY = startY;
  }

  @NotNull
  public ScrollFixture scroll(@AndroidCoordinate int scrollAmount) {
    myInteraction.scroll(myCurrentX, myCurrentY, scrollAmount);
    return this;
  }

  @NotNull
  public ComponentFixture release() {
    myInteraction.end(myCurrentX, myCurrentY, 0, false);
    return myComponentFixture;
  }

  @NotNull
  public ComponentFixture cancel() {
    myInteraction.end(myCurrentX, myCurrentY, 0, true);
    return myComponentFixture;
  }
}