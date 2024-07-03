/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * The data model for all the information in the MotionScene and Layout file
 */
public class MeModel {
  @SuppressWarnings("SSBasedInspection")
  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  public final boolean DEBUG = false;
  public final MTag motionScene;
  public final MTag layout;
  public final String layoutFileName;
  public final String motionSceneFileName;
  MotionEditorSelector.Type mSelectedType;
  public MTag[] mSelected;
  private float mProgress;
  private String[] mSelectedViewIDs = EMPTY_STRING_ARRAY;
  public Track myTrack;

  void clearViewInfo() {
    MTag[] view = layout.getChildTags();
    for (int i = 0; i < view.length; i++) {
      MTag tag = view[i];
      tag.setClientData(MotionSceneUtils.MOTION_LAYOUT_PROPERTIES, null);
    }
  }

  /**
   * This populates the attributes structure
   *
   * @param constraintSet
   * @return
   */
  public HashMap<String, MotionAttributes> populateViewInfo(MTag constraintSet) {
    if (DEBUG) {
      Debug.log("populateViewInfo : configuring Attributes " + constraintSet.getAttributeValue("id"));
    }
    ArrayList<String> ids = new ArrayList<>();
    HashMap<String, MotionAttributes> viewBundles = new HashMap<>();
    HashMap<String, MotionAttributes> filledBundles = new HashMap<>();
    ArrayList<MTag> csetChain = new ArrayList<>();
    // Build of the chain of dependency of constraints sets
    for (MTag set = constraintSet; set != null; set = getDerivedFrom(set)) {
      csetChain.add(set);
    }
    // build a table of attribute one for each view in the layout
    MTag[] view = layout.getChildTags();
    HashMap<String, MTag> viewMap = new HashMap<>();

    for (int i = 0; i < view.length; i++) {
      String id = view[i].getAttributeValue("id");
      if (id == null) {
        continue;
      }
      id = Utils.stripID(id);
      viewMap.put(id, view[i]);
      viewBundles.put(id, new MotionAttributes(id, constraintSet));
    }
    if (DEBUG) {
      Debug.log("populateViewInfo :  view info " + view.length);
    }
    // for each constraint set in the chain
    for (MTag cset : csetChain) {
      String constraintSetId = Utils.stripID(cset.getAttributeValue("id")); // get the id of the ConstraintSet

      // for each view in the ConstraintSet
      MTag[] constraint = cset.getChildTags();
      for (int i = 0; i < constraint.length; i++) {
        String id = constraint[i].getAttributeValue("id");
        if (id == null) {
          continue;
        }
        id = Utils.stripID(id);
        MotionAttributes bundle = viewBundles.get(id);
        if (bundle == null) { // we don't need to look at this id
          continue;
        }
        MTag[] subTags = constraint[i].getChildTags();
        HashMap<String, MTag.Attribute> attr = constraint[i].getAttrList();
        boolean allCustom = true;
        for (int j = 0; j < subTags.length; j++) {
          if (!(subTags[j].getTagName().equals("CustomAttribute"))) {
            allCustom = false;
            break;
          }
        }
        if ((subTags.length == 0 || allCustom) && attr.size() > 1) {
          bundle.loadAttrs(MotionAttributes.Section.ALL, constraintSetId, attr);
          bundle.consume(true, true, true, true);
          for (int j = 0; j < subTags.length; j++) {
            MTag subTag = subTags[j];
            bundle.addCustomAttrs(constraintSetId, subTag);
          }
          viewBundles.remove(id);
          filledBundles.put(id, bundle);
        }

        for (int j = 0; j < subTags.length; j++) {
          String tagType = subTags[j].getTagName();
          switch (tagType) {
            case MotionSceneAttrs.Tags.MOTION:
              bundle.loadAttrs(MotionAttributes.Section.MOTION, constraintSetId, subTags[j].getAttrList());
              break;
            case MotionSceneAttrs.Tags.TRANSFORM:
              bundle.loadAttrs(MotionAttributes.Section.TRANSFORM, constraintSetId, subTags[j].getAttrList());
              break;
            case MotionSceneAttrs.Tags.PROPERTY_SET:
              bundle.loadAttrs(MotionAttributes.Section.PROPERTY_SET, constraintSetId, subTags[j].getAttrList());
              break;
            case MotionSceneAttrs.Tags.LAYOUT:
              bundle.loadAttrs(MotionAttributes.Section.LAYOUT, constraintSetId, subTags[j].getAttrList());
              break;
            case MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE:
              bundle.addCustomAttrs(constraintSetId, subTags[j]);
              break;
            default:
              Debug.log("Unknown TAG! " + tagType);
          }
        }
        if (bundle.allFilled()) {
          viewBundles.remove(id);
          filledBundles.put(id, bundle);
        }
      }
      // anything left must be
    }
    // for each view left in the view bundle (which would mean it is not "complete yet"


    for (String id : new ArrayList<>(viewBundles.keySet())) {
      MotionAttributes bundle = viewBundles.get(id);
      MTag viewTag = viewMap.get(id);
      bundle.loadViewAttrs(viewTag);
      viewBundles.remove(id);
      filledBundles.put(id, bundle);
    }

    return filledBundles;
  }

  MTag getConstraint(MTag constraintSet, String stripedId) {
    MTag[] constraint = constraintSet.getChildTags("Constraint");
    for (int i = 0; i < constraint.length; i++) {
      String str = Utils.stripID(constraint[i].getAttributeValue("id"));
      if (stripedId.equals(str)) {
        return constraint[i];
      }
    }
    return null;
  }

  MTag getDerivedFrom(MTag cset) {
    String s = cset.getAttributeValue("deriveConstraintsFrom");
    if (s == null) {
      return null;
    }
    s = Utils.stripID(s);
    MTag[] constraintSets = motionScene.getChildTags("ConstraintSet");
    for (int i = 0; i < constraintSets.length; i++) {
      String str = Utils.stripID(constraintSets[i].getAttributeValue("id"));
      if (s.equals(str)) {
        return constraintSets[i];
      }
    }
    return null;
  }

  public MeModel(MTag motionScene, MTag layout, String layoutFileName, String motionSceneFileName, Track track) {
    this.layout = layout;
    this.motionScene = motionScene;
    this.layoutFileName = layoutFileName;
    this.motionSceneFileName = motionSceneFileName;
    this.myTrack = track;
  }

  public MeModel(MTag motionScene, MTag layout, String layoutFileName, String motionSceneFileName) {
    this.layout = layout;
    this.motionScene = motionScene;
    this.layoutFileName = layoutFileName;
    this.motionSceneFileName = motionSceneFileName;
  }

  public MTag getConstraintSet(String id) {
    id = Utils.stripID(id);
    MTag[] sets = motionScene.getChildTags("ConstraintSet", "id", id);
    if (sets.length == 1) {
      return sets[0];
    }
    return null;
  }

  public void setSelected(MotionEditorSelector.Type type, MTag[] tags) {
    if (type == MotionEditorSelector.Type.CONSTRAINT_SET) {
      MTag[] constraints = tags[0].getChildTags();
      HashMap<String, MTag> constraintMap = new HashMap<>();
      for (int i = 0; i < constraints.length; i++) {
        MTag constraint = constraints[i];
        String id = constraint.getAttributeValue("id");
        if (id == null) {
          continue;
        }
        constraintMap.put(Utils.stripID(id), constraint);
      }
      HashMap<String, MotionAttributes> motionAttributeSet = populateViewInfo(tags[0]);
      MTag[] views = layout.getChildTags();
      for (MTag view : views) {
        String id = view.getAttributeValue("id");
        if (id == null) {
          continue;
        }
        id = Utils.stripID(id);
        view.setClientData(MotionSceneUtils.MOTION_LAYOUT_PROPERTIES, motionAttributeSet.get(id));
        view.setClientData(MotionSceneUtils.MTAG_ACCESS, constraintMap.get(id));
      }
    }
    else if (type == MotionEditorSelector.Type.LAYOUT) {
      clearViewInfo();
    }
    mSelectedType = type;
    mSelected = tags;
  }

  public MotionEditorSelector.Type getSelectedType() {
    return mSelectedType;
  }

  public MTag[] getSelected() {
    return mSelected;
  }

  public void setProgress(float progress) {
    mProgress = progress;
  }

  public float getCurrentProgress() {
    return mProgress;
  }

  public String[] getLayoutViewNames() {
    ArrayList<String> ret = new ArrayList<>();
    if (layout == null) {
      return EMPTY_STRING_ARRAY;
    }
    MTag[] allViews = layout.getChildTags();
    for (int j = 0; j < allViews.length; j++) {
      String[] row = new String[3];
      MTag view = allViews[j];
      String layoutId = Utils.stripID(view.getAttributeValue("id"));
      ret.add(layoutId);
    }
    return ret.toArray(EMPTY_STRING_ARRAY);
  }

  /**
   * Given a transition or a child of transition find the related start constraintSet
   *
   * @param transitionOrChild
   * @return start constraintSet
   */
  public MTag findStartConstraintSet(MTag transitionOrChild) {
    MTag transition = transitionOrChild;
    while (!MotionSceneAttrs.Tags.TRANSITION.equals(transition.getTagName())) {
      transition = transition.getParent();
      if (transition == null) {
        return null;
      }
    }
    String start = transition.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START);
    return getConstraintSet(start);
  }

  /**
   * Given a transition or a child of transition find the related end constraintSet
   *
   * @param transitionOrChild
   * @return end constraintSet
   */
  public MTag findEndConstraintSet(MTag transitionOrChild) {
    MTag transition = transitionOrChild;
    while (!MotionSceneAttrs.Tags.TRANSITION.equals(transition.getTagName())) {
      transition = transition.getParent();
      if (transition == null) {
        return null;
      }
    }
    String end = transition.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END);
    return getConstraintSet(end);
  }

  public void findStartAndEndValues(MTag layout, String attribute, MTag mTag, String[] values) {
    MTag transition = mTag.getParent().getParent();
    String target = mTag.getAttributeValue("motionTarget");
    String start = transition.getAttributeValue("constraintSetStart");
    String end = transition.getAttributeValue("constraintSetEnd");
    MTag startSet = getConstraintSet(start);

    String startValue = findAttribute(layout, startSet, target, attribute);
    if (startValue == null) {
      values[0] = defaultValueForAttribute(attribute);
    }

    MTag endSet = getConstraintSet(end);
    String endValue = findAttribute(layout, startSet, target, attribute);
    if (endValue == null) {
      values[1] = defaultValueForAttribute(attribute);
    }
  }

  private String defaultValueForAttribute(String attribute) {
    switch (attribute) {
      case "elevation":
        return "0";
      case "rotation":
        return "0";
      case "rotationX":
        return "0";
      case "rotationY":
        return "0";
      case "scaleX":
        return "1";
      case "scaleY":
        return "1";
      case "translationX":
        return "0";
      case "translationY":
        return "0";
      case "translationZ":
        return "0";
      case "transitionPathRotate":
        return "0";
      case "alpha":
        return "1";
      case "motionProgress":
        return "0";
    }
    return null;
  }

  String findAttribute(MTag layout, MTag cSet, String id, String attribute) {
    if (cSet != null) {
      MTag[] constraints = cSet.getChildTags("id", id);
      if (constraints != null && constraints.length > 0) {
        String value = constraints[0].getAttributeValue(attribute);
        if (value != null) {
          return value;
        }
      }
      String derivedStr = cSet.getAttributeValue("deriveConstraintsFrom");
      if (derivedStr != null) {
        MTag[] derivedTag = cSet.getParent().getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET, MotionSceneAttrs.ATTR_ANDROID_ID, derivedStr);
        if (derivedTag != null && derivedTag.length > 0) {
          return findAttribute(layout, derivedTag[0], id, attribute);
        }
      }
    }
    if (layout == null) {
      return null;
    }
    MTag[] view = layout.getChildTags("id", id);
    if (view == null || view.length == 0) {
      return null;
    }
    return view[0].getAttributeValue(attribute);
  }

  public MTag[] getViewNotInConstraintSet(MTag constraintSet) {
    HashSet<String> found = new HashSet<>();
    MTag[] sets = constraintSet.getChildTags("Constraint");
    String derived = constraintSet.getAttributeValue("deriveConstraintsFrom");
    for (int i = 0; i < sets.length; i++) {
      MTag constraint = sets[i];
      String id = Utils.stripID(constraint.getAttributeValue("id"));
      found.add(id);
    }
    ArrayList<MTag> tags = new ArrayList<>();
    MTag[] allViews = layout.getChildTags();
    for (int j = 0; j < allViews.length; j++) {
      Object[] row = new Object[4];
      MTag view = allViews[j];
      String layoutId = view.getAttributeValue("id");

      if (layoutId == null) {
        row[0] = view.getTagName().substring(1 + view.getTagName().lastIndexOf("/"));
        continue;
      }

      layoutId = Utils.stripID(layoutId);
      if (found.contains(layoutId)) {
        continue;
      }
      tags.add(view);
    }
    return tags.toArray(new MTag[0]);
  }

  public MTag findTag(String type, String id) {
    if (mSelectedType == null || id == null) {
      return null;
    }

    if (mSelected == null || mSelected.length == 0) {
      return null;
    }
    MTag tag = mSelected[0];
    switch (mSelectedType) {
      case TRANSITION:
      case KEY_FRAME_GROUP:
        break;
      case CONSTRAINT:
        tag = tag.getParent(); // for constraint we need to go up a level to the constraint set
        // falls through
      case CONSTRAINT_SET:
        MTag[] look = tag.getChildTags("id", id);
        if (look != null && look.length > 0) {
          return look[0];
        }
        MTag[] layoutView = layout.getChildTags();
        for (int i = 0; i < layoutView.length; i++) {
          MTag mTag = layoutView[i];
          String viewId = Utils.stripID(mTag.getAttributeValue("id"));
          if (viewId != null && id.equals(viewId)) {
            return mTag;
          }
        }
    }
    return null;
  }

  /**
   * This caches the selected view ids to allow them to be reselected from constraintSet to MotionLayout panel
   *
   * @param ids
   */
  public void setSelectedViewIDs(List<String> ids) {
    mSelectedViewIDs = ids.toArray(EMPTY_STRING_ARRAY);
  }

  public void setSelectedViewIDs(@NotNull String[] ids) {
    mSelectedViewIDs = ids;
  }

  public String[] getSelectedViewIDs() {
    return mSelectedViewIDs;
  }
}
