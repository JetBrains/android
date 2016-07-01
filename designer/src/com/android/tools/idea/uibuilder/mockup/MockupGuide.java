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
package com.android.tools.idea.uibuilder.mockup;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Represents a guideline from a mockup file.
 *
 * It is contains helper method to create the actual {@value SdkConstants#CONSTRAINT_LAYOUT_GUIDELINE} to insert
 * in a {@value SdkConstants#CONSTRAINT_LAYOUT}
 *
 * @see MockupInteractionPanel#exportSelectedGuidelines()
 */
class MockupGuide {
  private final static InsertType INSERT_TYPE = InsertType.CREATE;
  private int myPosition;
  private Orientation myOrientation;

  enum Orientation {
    VERTICAL,
    HORIZONTAL
  }

  /**
   * @param dpPosition Android Dp position of the mockup. Y coordinate if orientation is
   *                   HORIZONTAL, X coordinate if VERTICAL.
   * @param orientation Orientation of the guideline.
   */
  MockupGuide(@AndroidDpCoordinate int dpPosition, Orientation orientation) {
    myPosition = dpPosition;
    myOrientation = orientation;
  }

  /**
   * Create the actual {@value SdkConstants#CONSTRAINT_LAYOUT_GUIDELINE} to insert
   * in a {@value SdkConstants#CONSTRAINT_LAYOUT}
   *
   * @param screenView
   * @param model
   * @param parentConstraintLayout The Constraint Layout that hold
   */
  void createConstraintGuideline(@NotNull ScreenView screenView, @NotNull NlModel model, @NotNull NlComponent parentConstraintLayout) {

    if (!parentConstraintLayout.getTagName().equals(SdkConstants.CONSTRAINT_LAYOUT)) {
      return;
    }

    final NlComponent guideline = model.createComponent(
      screenView, SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE, parentConstraintLayout, null, INSERT_TYPE);

    // Set the Guideline NlComponent position
    guideline.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN,
                           String.format(Locale.US, "%ddp", myPosition));

    // Set the Guideline Component orientation
    if (myOrientation.equals(Orientation.HORIZONTAL)) {
      guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                             SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
    }
    else {
      guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                             SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL);
    }
  }
}
