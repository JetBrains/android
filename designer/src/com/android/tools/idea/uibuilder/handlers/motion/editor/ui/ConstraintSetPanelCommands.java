/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Collection of static commands that run on ConstraintSet
 */
public final class ConstraintSetPanelCommands {

  private static final boolean DEBUG = false;

  public static void createAllConstraints(ArrayList<MTag> displayedRows, MTag constraintSet) {
    ArrayList<MTag> tagList = new ArrayList<MTag>();
    for (MTag row : displayedRows) {
      if (!constraintSet.equals(row.getParent())) {
        tagList.add(row);
      }
    }
    createConstraint(tagList.toArray(new MTag[0]), constraintSet);
  }

  public static void createConstraint(MTag[] selected, MTag constraintSet) {
    for (MTag mTag : selected) {
      createConstraint(mTag, constraintSet);
    }
  }

  public static MTag createConstraint(MTag selected, MTag constraintSet) {
    String id = selected.getAttributeValue("id");
    MTag.TagWriter new_constraint;
    new_constraint = constraintSet.getChildTagWriter("Constraint");
    new_constraint.setAttribute(MotionSceneAttrs.ANDROID, "id", id);
    HashMap<String, MTag.Attribute> list = selected.getAttrList();
    for (String type : list.keySet()) {
      MTag.Attribute attr = list.get(type);
      if (DEBUG) {
        Debug.log(attr.mAttribute + "  " + attr.mValue);
      }
      if (MotionSceneAttrs.copyToConstraint(attr)) {
        if (DEBUG) {
          Debug.log(" found!  " + attr.mValue);
        }
        new_constraint.setAttribute(MotionSceneAttrs.lookupName(attr), attr.mAttribute, attr.mValue);
      }
    }
    // TODO support rich parameter .visibility etc.
    return new_constraint.commit("Create Constraint");
  }

  public static void createSectionedConstraint(MTag[] selected, MTag constraintSet) {
    for (MTag mTag : selected) {
      createSectionedConstraint(mTag, constraintSet);
    }
  }

  public static MTag createSectionedConstraint(MTag selected, MTag constraintSet) {
    String id = selected.getAttributeValue("id");
    MTag.TagWriter new_constraint;
    new_constraint = constraintSet.getChildTagWriter("Constraint");
    new_constraint.setAttribute(MotionSceneAttrs.ANDROID, "id", id);
    HashMap<String, MTag.Attribute> list = selected.getAttrList();
    MTag.TagWriter layout = null;
    MTag.TagWriter propertySet = null;
    MTag.TagWriter transform = null;

    for (String type : list.keySet()) {
      MTag.Attribute attr = list.get(type);
      if (DEBUG) {
        Debug.log(attr.mAttribute + "  " + attr.mValue);
      }
      if (MotionSceneAttrs.isLayoutAttribute(attr)) {
        if (layout == null) {
          layout = new_constraint.getChildTagWriter(MotionSceneAttrs.Tags.LAYOUT);
        }
        layout.setAttribute(MotionSceneAttrs.lookupName(attr), attr.mAttribute, attr.mValue);
      }

      if (MotionSceneAttrs.isPropertySetAttribute(attr)) {
        if (propertySet == null) {
          propertySet = new_constraint.getChildTagWriter(MotionSceneAttrs.Tags.PROPERTY_SET);
        }

        propertySet.setAttribute(MotionSceneAttrs.lookupName(attr), attr.mAttribute, attr.mValue);
      }
      if (MotionSceneAttrs.isTransformAttribute(attr)) {
        if (transform == null) {
          transform = new_constraint.getChildTagWriter(MotionSceneAttrs.Tags.TRANSFORM);
        }
        transform.setAttribute(MotionSceneAttrs.lookupName(attr), attr.mAttribute, attr.mValue);
      }
    }
    return new_constraint.commit("Create Constraint");
  }

  /**
   * Assuming the selected constraint is a Constraint in a MotionScene/ConstraintSet
   * Simply delete it.
   *
   * @param selected      the selected constraint
   * @param ConstraintSet the constraintset it is under
   */
  public static void clearConstraint(MTag selected, MTag ConstraintSet) {
    selected.getTagWriter().deleteTag().commit("clear Constraint");
  }

  /**
   * This copies the selected constrains to the underlying layout
   *
   * @param selected
   * @param ConstraintSet
   */
  public static void moveConstraint(MTag selected, MTag ConstraintSet) {

  }

  /**
   * Convert from Constraint that is divided into sections into one that overrides all constraints
   * @param selected
   * @param ConstraintSet
   */
  public static void convertFromSectioned(MTag selected, MTag ConstraintSet) {
    MTag[] child = selected.getChildTags();
    MTag.TagWriter writer = selected.getTagWriter();
    for (int i = 0; i < child.length; i++) {
      MTag mTag = child[i];
      HashMap<String, MTag.Attribute> attrs = mTag.getAttrList();
      for (MTag.Attribute value : attrs.values()) {
        writer.setAttribute(value.mNamespace,value.mAttribute,value.mValue);
      }
      selected.getTagWriter().deleteTag().commit("convert");
    }
    writer.commit("Convert Constraint");
  }
}
