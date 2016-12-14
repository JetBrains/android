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
package com.android.tools.idea.uibuilder.scene;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

import static com.android.tools.idea.uibuilder.scene.draw.DrawGuidelineCycle.BEGIN;
import static com.android.tools.idea.uibuilder.scene.draw.DrawGuidelineCycle.END;
import static com.android.tools.idea.uibuilder.scene.draw.DrawGuidelineCycle.PERCENT;

/**
 * Encapsulate basic querys on a ConstraintLayout component
 * TODO: use this class everywhere for this type of queries and replace/update ConstraintTarget
 */
public class ConstraintComponentUtilities {

  protected static final HashMap<String, String> ourReciprocalAttributes;
  protected static final HashMap<String, String> ourMapMarginAttributes;
  protected static final ArrayList<String> ourLeftAttributes;
  protected static final ArrayList<String> ourTopAttributes;
  protected static final ArrayList<String> ourRightAttributes;
  protected static final ArrayList<String> ourBottomAttributes;
  protected static final ArrayList<String> ourMarginAttributes;
  protected static final ArrayList<String> ourHorizontalAttributes;
  protected static final ArrayList<String> ourVerticalAttributes;

  static {
    ourReciprocalAttributes = new HashMap<>();
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);

    ourMapMarginAttributes = new HashMap<>();
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);

    ourLeftAttributes = new ArrayList<>();
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);

    ourTopAttributes = new ArrayList<>();
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);

    ourRightAttributes = new ArrayList<>();
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourBottomAttributes = new ArrayList<>();
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);

    ourMarginAttributes = new ArrayList<>();
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_START);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_END);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);

    ourHorizontalAttributes = new ArrayList<>();
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourVerticalAttributes = new ArrayList<>();
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
  }

  private static boolean hasConstraints(SceneComponent component, String uri, ArrayList<String> constraints) {
    int count = constraints.size();
    NlComponent nlComponent = component.getNlComponent();
    for (int i = 0; i < count; i++) {
      if (nlComponent.getLiveAttribute(uri, constraints.get(i)) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasHorizontalConstraints(SceneComponent component) {
    return hasConstraints(component, SdkConstants.SHERPA_URI, ourHorizontalAttributes);
  }

  public static boolean hasVerticalConstraints(SceneComponent component) {
    return hasConstraints(component, SdkConstants.SHERPA_URI, ourVerticalAttributes);
  }

  /**
   * Return a dp value correctly resolved. This is only intended for generic
   * dimensions (number + unit). Do not use this if the string can contain
   * wrap_content or match_parent. See {@link #getLayoutDimensionDpValue(NlComponent, String)}.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  public static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : (int)(0.5f + px / (configuration.getDensity().getDpiValue() / 160.0f));
      }
    }
    return 0;
  }

  public static int getGuidelineMode(SceneComponent component) {
    NlComponent nlComponent = component.getNlComponent();
    String begin = nlComponent.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = nlComponent.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);

    if (begin != null) {
      return BEGIN;
    } else if (end != null) {
      return END;
    } else {
      return PERCENT;
    }
  }
}
