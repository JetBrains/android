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
package com.android.tools.idea.uibuilder.api;

import android.view.View;
import android.view.ViewGroup;
import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

public class ScrollHandler {
  private final View myScrollView;
  private final int myMaxScrollableHeight;
  private final int myScrollUnitSize;
  private final int myStartScrollPosition;

  public ScrollHandler(@NonNull NlComponent component,
                       int maxScrollableHeight,
                       int scrollUnitSize) {
    myMaxScrollableHeight = maxScrollableHeight;
    myScrollUnitSize = scrollUnitSize;
    myScrollView = (View)component.viewInfo.getViewObject();
    myStartScrollPosition = myScrollView.getScrollY();
  }

  /**
   * Set the scroll position on all the components with the "scrollX" and "scrollY" attribute. If
   * the component supports nested scrolling attempt that first, then use the unconsumed scroll
   * part to scroll the content in the component.
   */
  private static void handleScrolling(@NotNull View view) {
    int scrollPosX = view.getScrollX();
    int scrollPosY = view.getScrollY();
    if (scrollPosX != 0 || scrollPosY != 0) {
      if (view.isNestedScrollingEnabled()) {
        int[] consumed = new int[2];
        int axis = scrollPosX != 0 ? View.SCROLL_AXIS_HORIZONTAL : 0;
        axis |= scrollPosY != 0 ? View.SCROLL_AXIS_VERTICAL : 0;
        if (view.startNestedScroll(axis)) {
          view.dispatchNestedPreScroll(scrollPosX, scrollPosY, consumed, null);
          view.dispatchNestedScroll(consumed[0], consumed[1], scrollPosX, scrollPosY,
                                    null);
          view.stopNestedScroll();
          scrollPosX -= consumed[0];
          scrollPosY -= consumed[1];
        }
      }
      if (scrollPosX != 0 || scrollPosY != 0) {
        view.scrollTo(scrollPosX, scrollPosY);
      }
    }

    if (!(view instanceof ViewGroup)) {
      return;
    }
    ViewGroup group = (ViewGroup) view;
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      handleScrolling(child);
    }
  }

  /**
   * Sets the scroll position to scrollAmount pixels
   * @return number of pixels that the scroll position has changed or 0 if the scroll hasn't moved
   */
  public int update(int scrollAmount) {
    final int currentScrollPosition = myScrollView.getScrollY();

    // new scroll y within [0, myMaxScrollableHeight]
    final int newScrollY =
      Math.min(myMaxScrollableHeight,
               Math.max(0, myStartScrollPosition + scrollAmount * myScrollUnitSize));

    if (newScrollY == currentScrollPosition) {
      return 0;
    }

    myScrollView.setScrollY(newScrollY);
    handleScrolling(myScrollView);
    return newScrollY - currentScrollPosition;
  }

  public void commit(int scrollAmount) {
    // To make sure we correctly update the view hierarchy scrolling values
    update(scrollAmount);

    // TODO: layoutlib currently handles the scroll attributes both on inflate and render.
    //       If a scroll value is set in the XML, it will override any changes we've made to the
    //       View object. Uncomment this after scroll attributes are only handled during inflate.
    //View scrollView = (View)myComponent.viewInfo.getViewObject();
    //final int currentScrollY = scrollView.getScrollY();
    //
    //new WriteCommandAction.Simple<Void>(myComponent.getModel().getProject(), "Updating scroll position",
    //                                    myComponent.getTag().getContainingFile()) {
    //  @Override
    //  protected void run() throws Throwable {
    //    if (currentScrollY <= 0) {
    //      myComponent.setAttribute(TOOLS_URI, "scrollY", null);
    //    }
    //    else {
    //      myComponent.getRoot().ensureNamespace(TOOLS_PREFIX, TOOLS_URI);
    //      myComponent.setAttribute(TOOLS_URI, "scrollY", currentScrollY + "px");
    //    }
    //  }
    //}.execute();
  }
}
