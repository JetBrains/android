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
package com.android.tools.idea.uibuilder.scout;

import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.VALUE_N_DP;
import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.util.DependencyManagementUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This performs direct conversion of RelativeLayout to ConstraintLayout
 */
public final class ScoutDirectConvert {
  public static final int VERTICAL = 0x1;
  public static final int HORIZONTAL = 0x2;
  public static final int BOTH = 0x3;

  static Convert[] ourConverts = {
    new Convert(ATTR_LAYOUT_ABOVE, Dir.TOP, false, VERTICAL, ATTR_LAYOUT_BOTTOM_TO_TOP_OF),
    new Convert(ATTR_LAYOUT_BELOW, Dir.BOTTOM, false, VERTICAL, ATTR_LAYOUT_TOP_TO_BOTTOM_OF),
    new Convert(ATTR_LAYOUT_TO_END_OF, Dir.END, false, HORIZONTAL, ATTR_LAYOUT_START_TO_END_OF),
    new Convert(ATTR_LAYOUT_TO_START_OF, Dir.START, false, HORIZONTAL, ATTR_LAYOUT_END_TO_START_OF),
    new Convert(ATTR_LAYOUT_TO_LEFT_OF, Dir.LEFT, false, HORIZONTAL, ATTR_LAYOUT_RIGHT_TO_LEFT_OF),
    new Convert(ATTR_LAYOUT_TO_RIGHT_OF, Dir.RIGHT, false, HORIZONTAL, ATTR_LAYOUT_LEFT_TO_RIGHT_OF),

    new Convert(ATTR_LAYOUT_ALIGN_RIGHT, Dir.NONE, false, HORIZONTAL, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF),
    new Convert(ATTR_LAYOUT_ALIGN_LEFT, Dir.NONE, false, HORIZONTAL, ATTR_LAYOUT_LEFT_TO_LEFT_OF),
    new Convert(ATTR_LAYOUT_ALIGN_END, Dir.NONE, false, HORIZONTAL, ATTR_LAYOUT_END_TO_END_OF),
    new Convert(ATTR_LAYOUT_ALIGN_START, Dir.NONE, false, HORIZONTAL, ATTR_LAYOUT_START_TO_START_OF),

    new Convert(ATTR_LAYOUT_ALIGN_BASELINE, Dir.NONE, false, VERTICAL, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF),
    new Convert(ATTR_LAYOUT_ALIGN_BOTTOM, Dir.NONE, false, VERTICAL, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF),
    new Convert(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, Dir.NONE, true, VERTICAL, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF),
    new Convert(ATTR_LAYOUT_ALIGN_PARENT_END, Dir.NONE, true, HORIZONTAL, ATTR_LAYOUT_END_TO_END_OF),
    new Convert(ATTR_LAYOUT_ALIGN_PARENT_LEFT, Dir.NONE, true, HORIZONTAL, ATTR_LAYOUT_LEFT_TO_LEFT_OF),
    new Convert(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, Dir.NONE, true, HORIZONTAL, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF),
    new Convert(ATTR_LAYOUT_ALIGN_PARENT_START, Dir.NONE, true, HORIZONTAL, ATTR_LAYOUT_START_TO_START_OF),
    new Convert(ATTR_LAYOUT_ALIGN_PARENT_TOP, Dir.NONE, true, VERTICAL, ATTR_LAYOUT_TOP_TO_TOP_OF),
    new Convert(ATTR_LAYOUT_ALIGN_TOP, Dir.NONE, false, VERTICAL, ATTR_LAYOUT_TOP_TO_TOP_OF),
    new Convert(ATTR_LAYOUT_CENTER_HORIZONTAL, Dir.NONE, true, HORIZONTAL, ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF),
    new Convert(ATTR_LAYOUT_CENTER_IN_PARENT, Dir.NONE, true, BOTH,
                ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF),
    new Convert(ATTR_LAYOUT_CENTER_VERTICAL, Dir.NONE, true, VERTICAL, ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF),
  };

  enum Dir {
    LEFT, RIGHT, TOP, BOTTOM, START, END, NONE
  }

  /**
   * Data class to hold conversion tables
   */
  static class Convert {
    String mRelativeAttribute;
    String[] mConstraintAttributes;
    boolean mAttachToParent;
    Dir mMddTargetMargin;
    int mConstrained;

    public Convert(String attribute, Dir addTargetMargin, boolean parent, int constraint, String... constraintAttributes) {
      mRelativeAttribute = attribute;
      mAttachToParent = parent;
      mConstrained = constraint;
      mConstraintAttributes = constraintAttributes;
      mMddTargetMargin = addTargetMargin;
    }
  }

  static HashMap<String, Convert> ourRelativeLayoutAttr = new HashMap<>();

  static {
    for (int i = 0; i < ourConverts.length; i++) {
      ourRelativeLayoutAttr.put(ourConverts[i].mRelativeAttribute, ourConverts[i]);
    }
  }

  public static boolean directProcess(NlComponent layout) {
    if (!layout.getTagDeprecated().getName().equals(RELATIVE_LAYOUT)) {
      return false;
    }
    layout.getTagDeprecated().setName(DependencyManagementUtil.mapAndroidxName(layout.getModel().getModule(), CLASS_CONSTRAINT_LAYOUT));
    convert(layout);
    return true;
  }

  /**
   * For every child view of this RelativeLayout each attribute is checked against a map
   * and a matching ConstraintLayout attribute is created
   * RelativeLayout has two types of attributes: ones that attach to parent
   * & ones that attach to other views.
   * Some attributes require the margins of the constraining view to be added
   *
   * @param component the Relative layout component
   */
  public static void convert(NlComponent component) {
    // Work bottom up to ensure the children aren't invalidated when processing the parent
    HashMap<String, NlComponent> idMap = new HashMap<>();
    for (NlComponent child : component.getChildren()) {
      idMap.put(child.getId(), child);
    }
    for (NlComponent child : component.getChildren()) {
      ArrayList<String[]> createList = new ArrayList<>();
      ArrayList<String[]> delList = new ArrayList<>();
      List<AttributeSnapshot> list = child.getAttributes();
      boolean verticalConstrained = false;
      boolean horizontallyConstrained = false;
      for (AttributeSnapshot attr : list) {
        Convert a = ourRelativeLayoutAttr.get(attr.name);

        if (a != null) {
          verticalConstrained |= (a.mConstrained & VERTICAL) > 0;
          horizontallyConstrained |= (a.mConstrained & HORIZONTAL) > 0;
          if (!a.mAttachToParent) {
            delList.add(new String[]{ANDROID_URI, attr.name});
            createList.add(new String[]{SHERPA_URI, a.mConstraintAttributes[0], attr.value});
            if (a.mMddTargetMargin != Dir.NONE) {
              String id = attr.value != null ? stripPrefixFromId(attr.value) : null;
              NlComponent ref = idMap.get(id);
              switch (a.mMddTargetMargin) {
                case LEFT:
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT, createList);
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_START, createList);
                  break;
                case RIGHT:
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, createList);
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END, createList);
                  break;
                case TOP:
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_BOTTOM, ATTR_LAYOUT_MARGIN_TOP, createList);
                  break;
                case BOTTOM:
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM, createList);
                  break;
                case START:
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_START, createList);
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT, createList);
                  break;
                case END:
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END, createList);
                  fixMargin(child, ref, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, createList);
                  break;
                case NONE:
                  break;
              }
            }
          }
          else {

            delList.add(new String[]{ANDROID_URI, attr.name});
            for (int i = 0; i < a.mConstraintAttributes.length; i++) {
              createList.add(new String[]{SHERPA_URI, a.mConstraintAttributes[i], ATTR_PARENT});
            }
          }
        }
      }
      if (!verticalConstrained) {
        createList.add(new String[]{SHERPA_URI, ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_PARENT});
      }
      if (!horizontallyConstrained) {
        createList.add(new String[]{SHERPA_URI, ATTR_LAYOUT_START_TO_START_OF, ATTR_PARENT});
      }
      AttributesTransaction transaction = child.startAttributeTransaction();

      for (String[] attr : delList) {
        transaction.removeAttribute(attr[0], attr[1]);
        transaction.setAttribute(attr[0], attr[1], null);
      }
      for (String[] attr : createList) {
        transaction.setAttribute(attr[0], attr[1], attr[2]);
      }

      transaction.commit();
    }
  }

  private static void fixMargin(NlComponent child, NlComponent ref, String srcMargin, String refMargin, List<String[]> creatList) {
    String marginStr = ref.getLiveAttribute(ANDROID_URI, refMargin);
    int targetMargin = ConstraintComponentUtilities.getDpValue(ref, marginStr);
    if (targetMargin != 0) {
      int childMargin = ConstraintComponentUtilities.getDpValue(child, srcMargin);
      if (childMargin == 0) {
        creatList.add(new String[]{ANDROID_URI, srcMargin, marginStr});
      }
      else {
        int margin = targetMargin + childMargin;
        creatList.add(new String[]{ANDROID_URI, srcMargin, String.format(VALUE_N_DP, margin)});
      }
    }
  }
}
