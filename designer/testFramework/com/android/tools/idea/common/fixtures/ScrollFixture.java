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

import static org.junit.Assert.assertNotNull;

import com.android.sdklib.AndroidCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.InteractionNonInputEvent;
import com.android.tools.idea.common.surface.MouseWheelMovedEvent;
import com.android.tools.idea.common.surface.MouseWheelStopEvent;
import com.android.tools.idea.common.surface.ScrollInteraction;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import java.awt.event.ActionEvent;
import java.awt.event.MouseWheelEvent;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

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
    myCurrentX = startX;
    myCurrentY = startY;
    myInteraction.begin(new MouseWheelMovedEvent(new MouseWheelEventBuilder(myCurrentX, myCurrentY).build(),
                                                 new InteractionInformation(myCurrentX, myCurrentY, 0)));
  }

  @NotNull
  public ScrollFixture scroll(@AndroidCoordinate int scrollAmount) {
    /**
     * Create a MouseWheelEvent which scrolls one time and the units of scroll is scrollAmount.
     */
    MouseWheelEvent event = new MouseWheelEventBuilder(myCurrentX, myCurrentY)
      .withAmount(1)
      .withScrollType(MouseWheelEvent.WHEEL_UNIT_SCROLL)
      .withUnitToScroll(scrollAmount)
      .build();
    myInteraction.update(new MouseWheelMovedEvent(event, new InteractionInformation(myCurrentX, myCurrentY, 0)));
    return this;
  }

  @NotNull
  public ComponentFixture release() {
    myInteraction.commit(new MouseWheelStopEvent(Mockito.mock(ActionEvent.class),
                                                 new InteractionInformation(myCurrentX, myCurrentY, 0)));
    return myComponentFixture;
  }

  @NotNull
  public ComponentFixture cancel() {
    myInteraction.cancel(new InteractionNonInputEvent(new InteractionInformation(myCurrentX, myCurrentY, 0)));
    return myComponentFixture;
  }
}
