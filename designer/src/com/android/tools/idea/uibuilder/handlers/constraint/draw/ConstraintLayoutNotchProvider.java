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
package com.android.tools.idea.uibuilder.handlers.constraint.draw;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Supply notches behavior to ConstraintLayout
 */
public class ConstraintLayoutNotchProvider implements Notch.Provider {

  // TODO: refactor with ConstraintComponentUtilities
  protected static final ArrayList<String> ourLeftAttributes;
  protected static final ArrayList<String> ourTopAttributes;
  protected static final ArrayList<String> ourRightAttributes;
  protected static final ArrayList<String> ourBottomAttributes;

  static {
    ourLeftAttributes = new ArrayList<>();
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_START_TO_START_OF);
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_START_TO_END_OF);

    ourTopAttributes = new ArrayList<>();
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);

    ourRightAttributes = new ArrayList<>();
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_END_TO_START_OF);
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_END_TO_END_OF);

    ourBottomAttributes = new ArrayList<>();
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
  }

  private static boolean hasAttributes(@NotNull AttributesTransaction transaction, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (transaction.getAttribute(uri, attribute) != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasLeft(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourLeftAttributes);
  }

  private static boolean hasTop(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourTopAttributes);
  }

  private static boolean hasRight(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourRightAttributes);
  }

  private static boolean hasBottom(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourBottomAttributes);
  }

  private static boolean hasBaseline(@NotNull AttributesTransaction transaction) {
    return transaction.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
  }

  @Override
  public void fill(@NotNull SceneComponent owner, @NotNull SceneComponent snappableComponent,
                   @NotNull List<Notch> horizontalNotches, @NotNull List<Notch> verticalNotches) {
    int x1 = owner.getDrawX();
    int x2 = x1 + owner.getDrawWidth();
    int midX = x1 + (x2 - x1) / 2 - snappableComponent.getDrawWidth() / 2;
    horizontalNotches.add(new Notch.Horizontal(owner, midX, x1 + (x2 - x1) / 2, (AttributesTransaction attributes) -> {
      if (hasLeft(attributes) || hasRight(attributes)) {
        return;
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent");
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent");
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, null);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, null);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, null);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, null);
    }));
    horizontalNotches.add(new Notch.Horizontal(owner, x1 + 16, x1 + 16, (AttributesTransaction attributes) -> {
      if (hasLeft(attributes) || hasRight(attributes)) {
        return;
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent");
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, String.format(SdkConstants.VALUE_N_DP, 16));
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, String.format(SdkConstants.VALUE_N_DP, 16));
    }));
    horizontalNotches.add(new Notch.Horizontal(owner, x2 - snappableComponent.getDrawWidth() - 16, x2 - 16, (AttributesTransaction attributes) -> {
      if (hasLeft(attributes) || hasRight(attributes)) {
        return;
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent");
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, String.format(SdkConstants.VALUE_N_DP, 16));
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, String.format(SdkConstants.VALUE_N_DP, 16));

    }));

    int y1 = owner.getDrawY();
    int y2 = y1 + owner.getDrawHeight();
    int midY = y1 + (y2 - y1) / 2 - snappableComponent.getDrawHeight() / 2;
    verticalNotches.add(new Notch.Vertical(owner, midY, y1 + (y2 - y1) / 2, (AttributesTransaction attributes) -> {
      if (hasTop(attributes) || hasBottom(attributes) || hasBaseline(attributes)) {
        return;
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent");
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, "parent");
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
    }));
    verticalNotches.add(new Notch.Vertical(owner, y1 + 16, y1 + 16, (AttributesTransaction attributes) -> {
      if (hasTop(attributes) || hasBottom(attributes) || hasBaseline(attributes)) {
        return;
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent");
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, String.format(SdkConstants.VALUE_N_DP, 16));
    }));
    verticalNotches.add(new Notch.Vertical(owner, y2 - snappableComponent.getDrawHeight() - 16, y2 - 16, (AttributesTransaction attributes) -> {
      if (hasTop(attributes) || hasBottom(attributes) || hasBaseline(attributes)) {
        return;
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, "parent");
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, String.format(SdkConstants.VALUE_N_DP, 16));
    }));
  }
}
