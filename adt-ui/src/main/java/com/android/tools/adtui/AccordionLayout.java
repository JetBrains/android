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

package com.android.tools.adtui;

import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * Custom LayoutManager that simulates an animated Accordion UI. Each component within the layout
 * can be set to the MINIMIZED, MAXIMIZED and PREFERRED states.
 *
 * In MINIMIZED state, the component occupies fixed space as defined by its getMinSize() dimension.
 *
 * In MAXIMIZED state, the component shares the available space with other maximized components
 * based on the ratio between their max sizes, while respecting its own getMinSize()/getMaxSize()
 * dimensions. Note that the amount of available space is defined by the parent container's size,
 * taking into account its insets and the minimal dimensions of other components currently in the
 * MINIMIZED/PREFERRED states.
 *
 * IN PREFERRED state, the component shares any remaining space with other preferred-sized
 * components based on the ratio between their preferred sizes, while respecting its own
 * getMinSize()/getMaxSize() dimensions.
 */
public class AccordionLayout implements LayoutManager2, Animatable {

  private static final float DEFAULT_LERP_FRACTION = 0.9999f;
  private static final float DEFAULT_LERP_THRESHOLD_PIXEL = 1;

  public enum Orientation {
    HORIZONTAL,
    VERTICAL
  }

  public enum AccordionState {
    MAXIMIZE,
    MINIMIZE,
    PREFERRED
  }

  private static class ComponentInfo implements Comparable<ComponentInfo> {

    Component component;
    float currentSize;
    AccordionState state;

    ComponentInfo(Component component, float size, AccordionState state) {
      this.component = component;
      this.currentSize = size;
      this.state = state;
    }

    @Override
    public int compareTo(ComponentInfo other) {
      int ret = this.state.compareTo(other.state);
      if (ret == 0) {
        ret = this.component.hashCode() - other.component.hashCode();
      }

      return ret;
    }
  }

  @NotNull
  private final HashMap<Component, ComponentInfo> mComponentInfoMap;

  @NotNull
  private final TreeSet<ComponentInfo> mComponentInfoSet;

  @NotNull
  private final Orientation mOrientation;

  @NotNull
  private final Container mParent;

  /**
   * The summation of space by all components currently in the MAXIMIZE states - used for
   * calculating the ratio of available space that maximized components will occupy.
   */
  private float mMaxTotal;

  /**
   * The summation of space by all components currently in the PREFERRED states- used for
   * calculating the ratio of available space that preferred-sized components will occupy.
   */
  private float mPreferredTotal;

  /**
   * Space preoccupied by the minimal dimensions of MINIMIZED/PREFERRED components - used for
   * determining the available space that MAXIMIZED components can occupy.
   */
  private float mPreoccupiedSpace;

  private float mLerpFraction;

  private float mLerpThreshold;

  public AccordionLayout(@NotNull Container parent, @NotNull Orientation orientation) {
    mParent = parent;
    mOrientation = orientation;
    mComponentInfoMap = new HashMap<>();
    mComponentInfoSet = new TreeSet<>();

    mLerpFraction = DEFAULT_LERP_FRACTION;
    mLerpThreshold = DEFAULT_LERP_THRESHOLD_PIXEL;
  }

  @Override
  public void setLerpFraction(float fraction) {
    mLerpFraction = fraction;
  }

  @Override
  public void setLerpThreshold(float threshold) {
    mLerpThreshold = threshold;
  }

  public AccordionState getState(@NotNull Component comp) {
    assert mComponentInfoMap.containsKey(comp);

    return mComponentInfoMap.get(comp).state;
  }

  public void setState(@NotNull Component comp, @NotNull AccordionState state) {
    assert mComponentInfoMap.containsKey(comp);

    ComponentInfo info = mComponentInfoMap.get(comp);
    if (info.state != state) {
      setStateInternal(comp, info, state);
    }
  }

  private void setStateInternal(@NotNull Component comp, @NotNull ComponentInfo info, @NotNull AccordionState state) {
    // Remove/Re-add info into set to force a sort.
    mComponentInfoSet.remove(info);
    info.state = state;
    // If minimized, ensure a smooth transition by getting the component's current size.
    // MAXIMIZED/PREFERRED components use ratios to determine their size so their
    // transition will be smooth no matter what the previous dimension is.
    if (state == AccordionState.MINIMIZE) {
      info.currentSize = mOrientation == Orientation.VERTICAL ? comp.getHeight() : comp.getWidth();
    }
    mComponentInfoSet.add(info);
  }

  /**
   * Toggle the component's maximized state.
   * If the component is already maximized, it will switch back to the preferred state.
   */
  public void toggleMaximize(@NotNull Component comp) {
    assert mComponentInfoMap.containsKey(comp);

    ComponentInfo info = mComponentInfoMap.get(comp);
    setStateInternal(comp, info, info.state == AccordionState.MAXIMIZE ? AccordionState.PREFERRED : AccordionState.MAXIMIZE);
  }

  /**
   * Toggle the component's minimized state.
   * If the component is already minimized, it will switch back to the preferred state.
   */
  public void toggleMinimize(@NotNull Component comp) {
    assert mComponentInfoMap.containsKey(comp);

    ComponentInfo info = mComponentInfoMap.get(comp);
    setStateInternal(comp, info, info.state == AccordionState.MINIMIZE ? AccordionState.PREFERRED : AccordionState.MINIMIZE);
  }

  public void resetComponents() {
    for (ComponentInfo info : mComponentInfoSet) {
      info.state = AccordionState.PREFERRED;
    }
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    ComponentInfo info = new ComponentInfo(comp, 0f, AccordionState.PREFERRED);
    mComponentInfoMap.put(comp, info);
    mComponentInfoSet.add(info);
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraints) {
    ComponentInfo info = new ComponentInfo(comp, 0f, AccordionState.PREFERRED);
    mComponentInfoMap.put(comp, info);
    mComponentInfoSet.add(info);
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    assert mComponentInfoMap.containsKey(comp);

    ComponentInfo info = mComponentInfoMap.get(comp);
    mComponentInfoMap.remove(comp);
    mComponentInfoSet.remove(info);
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    throw new NotImplementedException();
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    throw new NotImplementedException();
  }

  @Override
  public void invalidateLayout(Container target) {
    // Reset any cached states here if any.
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return calculateSizes(parent, AccordionState.MINIMIZE);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    return calculateSizes(parent, AccordionState.PREFERRED);
  }

  @Override
  public Dimension maximumLayoutSize(Container parent) {
    return calculateSizes(parent, AccordionState.MAXIMIZE);
  }

  @Override
  public void layoutContainer(Container parent) {
    // Account for parent's insets.
    Insets insets = parent.getInsets();
    int maxWidth = parent.getWidth() - (insets.left + insets.right);
    int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
    int remainingSpace = mOrientation == Orientation.VERTICAL ? maxHeight : maxWidth;

    // Temp cache to track components that have been clamped to their getMinSize(), so they can
    // be taken out of the fluid size calculations.
    HashMap<Component, Float> clampedComponents = new HashMap<>();

    // Based on whether components are clamped, we might need to reiterate the loop. Saving
    // the dimension info until the loop is done so setBounds is only called once.
    HashMap<Component, Point> layoutInfos = new HashMap<>();

    float totalUsedSpace = 0;
    float otherStatesPreoccupiedSpace = 0;
    float currentStateUsedSpace = 0;
    float currentStateFixedSpace = 0;
    float currentStateFluidSpace = 0;
    int currentStateStartIndex = -1;
    AccordionState currentState = null;

    // Caching the first component that has not been clamped and assign the remaining pixels to it.
    Component componentToFill = null;

    // The mComponentInfos list is sorted based on the Accordion.State enum order. So first we
    // determine the bounds for the MAXIMIZED components, followed by MINIMIZED then PREFERRED.
    List<ComponentInfo> infos = new ArrayList<>(mComponentInfoSet);
    for (int i = 0; i < infos.size(); i++) {
      ComponentInfo info = infos.get(i);
      if (currentState != info.state) {
        // Reached the next state group, keep track of total used space from last group.
        currentState = info.state;
        currentStateStartIndex = i;
        totalUsedSpace += currentStateUsedSpace;
        currentStateUsedSpace = 0;
        currentStateFixedSpace = 0;

        switch (currentState) {
          case MAXIMIZE:
            // Account for minimum space that will be occupied by MINIMIZE/PREFERRED components so that
            // we do not maximize components beyond what is available.
            otherStatesPreoccupiedSpace = mPreoccupiedSpace;
            currentStateFluidSpace = mMaxTotal;
            break;
          case PREFERRED:
            // Once we reach the PREFERRED components (the last state group), all used space by any
            // MAXIMIZED/MINIMIZED components is accounted for in totalUsedSpace, so no remaining space
            // would be pre-occupied.
            otherStatesPreoccupiedSpace = 0;
            currentStateFluidSpace = mPreferredTotal;
            break;
          default:
            // MINIMIZED components do not require knowledge of total space.
            break;
        }
      }

      Float size = clampedComponents.get(info.component);
      if (size == null) {
        if (info.state == AccordionState.MINIMIZE) {
          size = info.currentSize;
        }
        else {
          float fluidSpace = mOrientation == Orientation.VERTICAL ?
                             maxHeight - (totalUsedSpace + otherStatesPreoccupiedSpace + currentStateFixedSpace) :
                             maxWidth - (totalUsedSpace + otherStatesPreoccupiedSpace + currentStateFixedSpace);

          size = fluidSpace * info.currentSize / currentStateFluidSpace;
          float clampedSize = clampSize(info.component, size);
          if (clampedSize != size) {
            // If the size got clamped, then we need to redistribute more/less
            // available space amongst the unclamped MAXIMIZED/PREFERRED components,
            // so return to the beginning of the current state group to re-calculate
            // sizes for any components that have not been clamped.
            clampedComponents.put(info.component, clampedSize);
            currentStateFluidSpace -= info.currentSize;
            currentStateFixedSpace += clampedSize;
            currentStateUsedSpace = 0;
            i = currentStateStartIndex - 1;
            continue;
          } else if (componentToFill == null) {
            // Cache only the first unclamped component.
            componentToFill = info.component;
          }
        }
      } else if (info.component == componentToFill) {
        // If the cached first unclamped component has been clamped, reset the cache.
        componentToFill = null;
      }

      currentStateUsedSpace += size;
      switch (mOrientation) {
        case HORIZONTAL:
          if (layoutInfos.containsKey(info.component)) {
            remainingSpace += layoutInfos.get(info.component).x;
          }
          int width = Math.round(size);
          layoutInfos.put(info.component, new Point(width, maxHeight));
          remainingSpace -= width;
          break;
        case VERTICAL:
          if (layoutInfos.containsKey(info.component)) {
            remainingSpace += layoutInfos.get(info.component).y;
          }
          int height = Math.round(size);
          layoutInfos.put(info.component, new Point(maxWidth, height));
          remainingSpace -= height;
          break;
      }
    }

    int currX = insets.left;
    int currY = insets.top;
    synchronized (parent.getTreeLock()) {
      for (Component comp : parent.getComponents()) {
        // Assign remaining space to the last component that has not been clamped.
        Point size = layoutInfos.get(comp);
        if (componentToFill == comp) {
          switch (mOrientation) {
            case HORIZONTAL:
              size.x += remainingSpace;
              break;
            case VERTICAL:
              size.y += remainingSpace;
              break;
          }
        }

        comp.setBounds(currX, currY, size.x, size.y);

        switch (mOrientation) {
          case HORIZONTAL:
            currX += size.x;
            break;
          case VERTICAL:
            currY += size.y;
            break;
        }
      }
    }
  }

  @Override
  public void animate(float frameLength) {
    boolean hasMaximizedComponents = false;
    mPreferredTotal = 0;
    mMaxTotal = 0;
    mPreoccupiedSpace = 0;
    for (ComponentInfo info : mComponentInfoSet) {
      float minSize = 0, maxSize = 0, preferredSize = 0, targetSize = 0, currentSize = 0;
      switch (mOrientation) {
        case HORIZONTAL:
          minSize = info.component.getMinimumSize().width;
          maxSize = info.component.getMaximumSize().width;
          preferredSize = info.component.getPreferredSize().width;
          currentSize = info.component.getSize().width;
          break;
        case VERTICAL:
          minSize = info.component.getMinimumSize().height;
          maxSize = info.component.getMaximumSize().height;
          preferredSize = info.component.getPreferredSize().height;
          currentSize = info.component.getSize().height;
          break;
      }

      switch (info.state) {
        case MINIMIZE:
          targetSize = minSize;
          break;
        case MAXIMIZE:
          targetSize = maxSize;
          hasMaximizedComponents = true;
          break;
        case PREFERRED:
          targetSize = preferredSize;
          break;
      }

      if (info.currentSize != targetSize) {
        info.currentSize = Choreographer.lerp(info.currentSize, targetSize, mLerpFraction, frameLength, mLerpThreshold);
      }

      if (currentSize != minSize) {
        currentSize = Choreographer.lerp(currentSize, minSize, mLerpFraction, frameLength, mLerpThreshold);
      }

      switch (info.state) {
        case MINIMIZE:
          mPreoccupiedSpace += currentSize;
          break;
        case MAXIMIZE:
          mMaxTotal += info.currentSize;
          break;
        case PREFERRED:
          mPreferredTotal += info.currentSize;
          if (hasMaximizedComponents) {
            mPreoccupiedSpace += currentSize;
          }
          break;
      }
    }

    mParent.revalidate();
  }

  private Dimension calculateSizes(Container parent, AccordionState state) {
    Dimension dim = new Dimension();

    synchronized (parent.getTreeLock()) {
      Dimension childSize = new Dimension();
      for (Component child : parent.getComponents()) {
        switch (state) {
          case MINIMIZE:
            childSize = child.getMinimumSize();
            break;
          case MAXIMIZE:
            childSize = child.getMaximumSize();
            break;
          case PREFERRED:
            childSize = child.getPreferredSize();
            break;
        }

        switch (mOrientation) {
          case HORIZONTAL:
            dim.width += childSize.width;
            dim.height = Math.max(dim.height, childSize.height);
            break;
          case VERTICAL:
            dim.width = Math.max(dim.width, childSize.width);
            dim.height += childSize.height;
            break;
        }
      }
    }

    // Account for parent's insets.
    Insets insets = parent.getInsets();
    int insetWidth = insets.left + insets.right;
    int insetHeight = insets.top + insets.bottom;
    dim.width += insetWidth;
    dim.height += insetHeight;

    return dim;
  }

  private float clampSize(Component c, float preferred) {
    return mOrientation == Orientation.VERTICAL ?
           Math.min(c.getMaximumSize().height, Math.max(c.getMinimumSize().height, preferred)) :
           Math.min(c.getMaximumSize().width, Math.max(c.getMinimumSize().width, preferred));
  }
}

