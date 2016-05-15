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
package com.android.tools.idea.uibuilder.surface;

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.api.ScrollHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link Interaction} that provides support for scrollable components like ScrollView
 */
public class ScrollInteraction extends Interaction {
  // This handles the max scroll speed
  private static final int MAX_SCROLL_MULTIPLIER = 5;

  private final ScrollHandler myHandler;
  private int myScrolledAmount;
  private short myLastScrollSign;
  /**
   * The scroll multiplier will increment in every scroll call. This allows that, when bundling multiple scroll events, the scroll
   * accelerates until it reaches {@link #MAX_SCROLL_MULTIPLIER}
   */
  private int myScrollMultiplier = 1;
  private ScreenView myScreenView;

  private ScrollInteraction(@NonNull ScreenView screenView, @NonNull ScrollHandler scrollHandler) {
    myScreenView = screenView;
    myHandler = scrollHandler;
  }

  /**
   * Creates a new {@link ScrollInteraction} if any of the components in the component hierarchy can handle scrolling.
   * @return the {@link ScrollInteraction} or null if none of the components handle the scrolling
   */
  @Nullable
  public static ScrollInteraction createScrollInteraction(@NonNull ScreenView screenView, @NonNull NlComponent component) {
    NlComponent currentComponent = component;
    ScrollHandler scrollHandler = null;
    ViewEditor editor = new ViewEditorImpl(screenView);
    
    // Find the component that is the lowest in the hierarchy and can take the scrolling events
    while (currentComponent != null) {
      ViewGroupHandler viewGroupHandler = currentComponent.getViewGroupHandler();
      scrollHandler = viewGroupHandler != null ? viewGroupHandler.createScrollHandler(editor, currentComponent) : null;

      if (scrollHandler != null) {
        break;
      }
      currentComponent = currentComponent.getParent();
    }

    if (scrollHandler == null) {
      return null;
    }
    return new ScrollInteraction(screenView, scrollHandler);
  }

  @Override
  public void scroll(@SwingCoordinate int x, @SwingCoordinate int y, int scrollAmount) {
    short currentScrollSign = (short)(scrollAmount < 0 ? -1 : 0);

    if (myLastScrollSign != currentScrollSign) {
      // The scroll has changed direction so reset the fast scrolling
      myScrollMultiplier = 1;
      myLastScrollSign = currentScrollSign;
    }
    else if (myScrollMultiplier < MAX_SCROLL_MULTIPLIER) {
      myScrollMultiplier += 1;
    }

    int newScrolledAmount = myScrolledAmount + scrollAmount * myScrollMultiplier;
    int scrolled = myHandler.update(newScrolledAmount);

    if (scrolled != 0) {
      myScrolledAmount += scrollAmount;
      myScreenView.getModel().requestRender();
    }
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, int modifiers, boolean canceled) {
    if (canceled) {
      // Make sure we reset the scroll to where it was
      myHandler.update(0);
      myScreenView.getModel().requestRender();
      return;
    }

    // Reset scroll multiplier back to 1
    myScrollMultiplier = 1;
    myHandler.commit(myScrolledAmount);
    myScrolledAmount = 0;
  }
}
