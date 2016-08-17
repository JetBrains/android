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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A {@link ThreeComponentsSplitter} that can open and close its first and last component
 * with a sliding animation.
 */
@SuppressWarnings("unused")
public class AnimatedComponentSplitter extends ThreeComponentsSplitter {

  private static final int DEFAULT_ANIMATION_DURATION = 150;

  private boolean myIsFirstShowing;
  private boolean myIsLastShowing;
  private int myFirstClosedSize = 0;
  private int myLastClosedSize = 0;
  private int myAnimationDuration = DEFAULT_ANIMATION_DURATION;

  /**
   * {@inheritDoc}
   */
  public AnimatedComponentSplitter() {
    super();
  }

  /**
   * {@inheritDoc}
   */
  public AnimatedComponentSplitter(boolean vertical) {
    super(vertical);
  }

  /**
   * {@inheritDoc}
   */
  public AnimatedComponentSplitter(boolean vertical, boolean onePixelDividers) {
    super(vertical, onePixelDividers);
  }

  /**
   * Is the first component is shown (its size > myFirstClosedSize)
   *
   * @return true if first component size > myFirtstClosedSize
   * @see AnimatedComponentSplitter#setFirstClosedSize(int)
   */
  public boolean isFirstShowing() {
    return myIsFirstShowing;
  }

  /**
   * Is the first component is shown (its size > myLastClosedSize)
   *
   * @return true if first component size > myFirtstClosedSize
   * @see AnimatedComponentSplitter#setLastClosedSize(int)
   */
  public boolean isLastShowing() {
    return myIsLastShowing;
  }

  /**
   * Show or hide the first component with an sliding animation
   *
   * @param show true to show, false to hide
   */
  public void showAnimateFirst(boolean show) {
    final int targetSize = show ? getFirstOpenSize() : myLastClosedSize;
    final int startSize = getFirstSize();
    if (show && startSize > getFirstOpenSize()
        || !show && startSize < myFirstClosedSize) {
      return;
    }
    showAnimate(this::setFirstSize, targetSize, startSize);
  }

  /**
   * Show or hide the last component with an sliding animation
   *
   * @param show true to show, false to hide
   */
  public void showAnimateLast(boolean show) {
    final int targetSize = show ? getLastOpenSize() : myLastClosedSize;
    final int startSize = getLastSize();
    if (show && startSize > getLastOpenSize()
        || !show && startSize < myLastClosedSize) {
      return;
    }
    showAnimate(this::setLastSize, targetSize, startSize);
  }

  /**
   * Create a timer that handle the animation by calling the provided setSizeMethod
   * from startSize to targetSize
   *
   * @param setSizeMethod a reference to the method needed to call to resize the component
   * @param targetSize    the final size of the component after the animation
   * @param startSize     the initial size of the component before the animation
   */
  private void showAnimate(@NotNull Consumer<Integer> setSizeMethod, int targetSize, int startSize) {
    final long startTime = System.currentTimeMillis();

    final Timer timer = new Timer(20, e -> {
      final int size;
      float t = (System.currentTimeMillis() - startTime) / (float)myAnimationDuration;
      if (t >= 1) {
        t = 1;
        ((Timer)e.getSource()).stop();
      }
      final int delta = targetSize - startSize;
      size = Math.round(startSize + delta * t);
      setSizeMethod.consume(size);
    });
    timer.setRepeats(true);
    timer.start();
  }

  /**
   * Show or hide the provided child if it is one of first or last component
   *
   * @param child the child to animate if present
   * @param show  true to show, false to hide
   */
  public void showAnimateChild(@NotNull JComponent child, boolean show) {
    if (getFirstComponent() == child) {
      showAnimateFirst(show);
    }
    else if (getLastComponent() == child) {
      showAnimateLast(show);
    }
  }

  @Override
  public void setFirstSize(int size) {
    myIsFirstShowing = size > 1;
    super.setFirstSize(size);
  }

  @Override
  public void setLastSize(int size) {
    myIsLastShowing = size > 1;
    super.setLastSize(size);
  }

  protected int getFirstOpenSize() {
    return Math.round(getWidth() / 3f);
  }

  protected int getLastOpenSize() {
    return getFirstOpenSize();
  }

  /**
   * Set the size of the first component when in closed state
   *
   * @param firstClosedSize
   */
  public void setFirstClosedSize(int firstClosedSize) {
    myFirstClosedSize = firstClosedSize;
  }

  /**
   * Set the size of the last component when in closed state
   *
   * @param lastClosedSize
   */
  public void setLastClosedSize(int lastClosedSize) {
    myLastClosedSize = lastClosedSize;
  }
}
