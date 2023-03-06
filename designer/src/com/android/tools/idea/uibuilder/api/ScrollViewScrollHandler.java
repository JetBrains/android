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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ScrollViewScrollHandler implements ScrollHandler {
  private final int myMaxScrollableSize;
  private final int myScrollUnitSize;
  private final int myStartScrollPosition;
  @NotNull private final IntConsumer myScrollSetter;
  @NotNull private final IntSupplier myScrollGetter;
  @NotNull private final Runnable myScrollHandler;

  ScrollViewScrollHandler(
    int maxScrollableSize,
    int scrollUnitSize,
    @NotNull IntConsumer scrollSetter,
    @NotNull IntSupplier scrollGetter,
    @NotNull Runnable scrollHandler) {
    myMaxScrollableSize = maxScrollableSize;
    myScrollUnitSize = scrollUnitSize;
    myScrollSetter = scrollSetter;
    myScrollGetter = scrollGetter;
    myStartScrollPosition = scrollGetter.getAsInt();
    myScrollHandler = scrollHandler;
  }

  /**
   * Creates a new {@link ScrollViewScrollHandler}
   * @param viewGroup The scrollable {@link android.view.ViewGroup}
   * @param maxScrollableSize The maximum number of pixels the viewGroup can be scrolled
   * @param scrollUnitSize The number of pixels to scroll in every scroll step
   * @param orientation The scroll orientation
   */
  @NotNull
  public static ScrollViewScrollHandler createHandler(@NotNull ViewGroup viewGroup,
                                                      NlComponent component,
                                                      int maxScrollableSize,
                                                      int scrollUnitSize,
                                                      @NotNull Orientation orientation) {
    return new ScrollViewScrollHandler(
      maxScrollableSize,
      scrollUnitSize,
      orientation == Orientation.VERTICAL ?
      i -> {
        viewGroup.setScrollY(i);
        NlComponentHelperKt.setScrollY(component, i);
      } :
      i -> {
        viewGroup.setScrollX(i);
        NlComponentHelperKt.setScrollX(component, i);
      },
      orientation == Orientation.VERTICAL ? viewGroup::getScrollY : viewGroup::getScrollX,
      () -> handleScrolling(viewGroup)
    );
  }

  /**
   * Set the scroll position on all the components with the "scrollX" and "scrollY" attribute. If
   * the component supports nested scrolling attempt that first, then use the unconsumed scroll
   * part to scroll the content in the component.
   */
  private static void handleScrolling(@NotNull View view) {
    RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
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
        // This is required for layoutlib native to invalidate and redraw correctly
        view.requestLayout();
      }

      if (!(view instanceof ViewGroup)) {
        return;
      }
      ViewGroup group = (ViewGroup)view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View child = group.getChildAt(i);
        handleScrolling(child);
      }
    });
  }

  /**
   * Sets the scroll position to scrollAmount pixels
   *
   * @return number of pixels that the scroll position has changed or 0 if the scroll hasn't moved
   */
  @Override
  public int update(int scrollAmount) {
    final int currentScrollPosition = myScrollGetter.getAsInt();

    // new scroll y within [0, myMaxScrollableSize]
    final int newScrollPos =
      Math.min(myMaxScrollableSize,
               Math.max(0, myStartScrollPosition + scrollAmount * myScrollUnitSize));

    if (newScrollPos == currentScrollPosition) {
      return 0;
    }

    myScrollSetter.accept(newScrollPos);
    myScrollHandler.run();
    return newScrollPos - currentScrollPosition;
  }

  @Override
  public void commit(int scrollAmount) {
    // To make sure we correctly update the view hierarchy scrolling values
    update(scrollAmount);
  }

  @Override
  public boolean canScroll(int scrollAmount) {
    if (myScrollGetter.getAsInt() == 0 && scrollAmount < 0) {
      return false;
    }
    if (myScrollGetter.getAsInt() == myMaxScrollableSize && scrollAmount > 0) {
      return false;
    }
    return true;
  }

  /**
   * Returns the maximum distance that the passed view group could scroll
   *
   * @param measureGroup    {@link Function} used to measure the passed viewGroup (for example {@link ViewGroup#getHeight()})
   * @param measureChildren {@link Function} used to measure the children of the viewGroup (for example {@link View#getMeasuredHeight()})
   */
  public static int getMaxScrollable(@NotNull ViewGroup viewGroup,
                                     @NotNull Function<ViewGroup, Integer> measureGroup,
                                     @NotNull Function<View, Integer> measureChildren) {
    int maxScrollable = 0;
    for (int i = 0; i < viewGroup.getChildCount(); i++) {
      maxScrollable += measureChildren.apply(viewGroup.getChildAt(i));
    }

    // Subtract the viewport height from the scrollable size
    maxScrollable -= measureGroup.apply(viewGroup);

    if (maxScrollable < 0) {
      maxScrollable = 0;
    }

    return maxScrollable;
  }

  /**
   * Scroll orientation
   */
  public enum Orientation {
    VERTICAL,
    HORIZONTAL
  }
}
