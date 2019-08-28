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
import java.util.HashMap;

/**
 * Provides a view within MotionLayout with all the basic information under the ConstraintSet.
 */
public class MotionAttributes {
  private boolean mDefinedLayout = false;
  private boolean mDefinedPropertySet = false;
  private boolean mDefinedTransform = false;
  private boolean mDefinedMotion = false;
  private String mId;
  private HashMap<String , DefinedAttribute> definedAttributes = new HashMap<>();

  public String getId() {
    return mId;
  }

  public static class DefinedAttribute {
    private String source_id;  // Id of the constraintset or null if from layout
    private String nameSpace;
    private String customType;
    private String name;
    private String value;

    @Override
    public String toString() {
       String ret = "("+((source_id==null)?"LAYOUT":source_id)+")"+name+" "+value;
       if (customType!=null) {
         ret += "("+customType+")";
       }
       return ret;
    }
  }

  /**
   * Returns a map if id to DefinedAttributes
   * @return
   */
  public HashMap<String , DefinedAttribute> getAttrMap(){
    return definedAttributes;
  }

  public void loadViewAttrs(MTag viewTag) {
    HashMap<String, MTag.Attribute> map = viewTag.getAttrList();
    for (String type : map.keySet()) {
      MTag.Attribute attr = map.get(type);

      if (!mDefinedPropertySet && MotionSceneAttrs.isPropertySetAttribute(attr)) {
        DefinedAttribute newAttribute = new DefinedAttribute();
        newAttribute.source_id = null;
        newAttribute.nameSpace = attr.mNamespace;
        newAttribute.customType = null;
        newAttribute.name = attr.mAttribute;
        newAttribute.value = attr.mValue;
        definedAttributes.put(newAttribute.name , newAttribute);
      }
      if (!mDefinedTransform && MotionSceneAttrs.isTransformAttribute(attr)) {
        DefinedAttribute newAttribute = new DefinedAttribute();
        newAttribute.source_id = null;
        newAttribute.nameSpace = attr.mNamespace;
        newAttribute.customType = null;
        newAttribute.name = attr.mAttribute;
        newAttribute.value = attr.mValue;
        definedAttributes.put(newAttribute.name , newAttribute);
      }
      if (!mDefinedLayout && MotionSceneAttrs.isLayoutAttribute(attr)) {
        DefinedAttribute newAttribute = new DefinedAttribute();
        newAttribute.source_id = null;
        newAttribute.nameSpace = attr.mNamespace;
        newAttribute.customType = null;
        newAttribute.name = attr.mAttribute;
        newAttribute.value = attr.mValue;
        definedAttributes.put(newAttribute.name , newAttribute);
      }
    }
  }

  public enum Section {
    LAYOUT,
    PROPERTY_SET,
    TRANSFORM,
    MOTION,
    ALL
  }

  public MotionAttributes(String id) {
    this.mId = id;
  }

  public void dumpList() {
    Debug.log("   "+ mId);
    for (DefinedAttribute attribute : definedAttributes.values()) {
      String s = attribute.name + "  "+ attribute.value;
      System.out.println(s);
    }
  }

  public void addCustomAttrs(String constraintSetId, MTag customAttr) {
    String name = customAttr.getAttributeValue("attributeName");
    String customType = null;
    String value = null;
    for (String s : MotionSceneAttrs.ourCustomAttribute) {
      String v = customAttr.getAttributeValue(s);
      if (v != null) {
        customType = s;
        value = v;
        break;
      }
    }
    if (definedAttributes.containsKey(name)) { // It was overridden at a higher level
      return;
    }
    DefinedAttribute newAttribute = new DefinedAttribute();
    newAttribute.source_id = constraintSetId;
    newAttribute.customType = customType;
    newAttribute.name = name;
    newAttribute.value = value;
    definedAttributes.put(newAttribute.name , newAttribute);
  }



  public void consume(boolean definedLayout, boolean definedPropertySet, boolean definedTransform, boolean definedMotion) {
    mDefinedLayout |= definedLayout;
    mDefinedPropertySet |= definedPropertySet;
    mDefinedTransform |= definedTransform;
    mDefinedMotion |= definedMotion;
  }
  public boolean allFilled() {
    return mDefinedLayout&&mDefinedMotion&&mDefinedPropertySet&&mDefinedTransform;
  }
  public boolean layoutTagsFilled() {
    return mDefinedLayout&&mDefinedPropertySet&&mDefinedTransform;
  }
  public void loadAttrs(Section type , String constraintSetId, HashMap<String, MTag.Attribute> attr) {
    switch (type) {
      case LAYOUT:
        if (mDefinedLayout) {
          return;
        }
        mDefinedLayout = true;
        break;
      case PROPERTY_SET:
        if (mDefinedPropertySet) {
          return;
        }
        mDefinedPropertySet = true;
        break;
      case TRANSFORM:
        if (mDefinedTransform) {
          return;
        }
        mDefinedTransform = true;
        break;
      case MOTION:
        if (mDefinedMotion) {
          return;
        }
        mDefinedMotion = true;
        break;
      case ALL:
        mDefinedLayout = true;
        mDefinedPropertySet = true;
        mDefinedMotion = true;
        mDefinedTransform = true;
        break;
    }
    for (String key : attr.keySet()) {
      MTag.Attribute a = attr.get(key);
      DefinedAttribute newAttribute = new DefinedAttribute();
      newAttribute.source_id = constraintSetId;
      newAttribute.nameSpace = a.mNamespace;
      newAttribute.customType = null;
      newAttribute.name = a.mAttribute;
      newAttribute.value = a.mValue;
      definedAttributes.put(newAttribute.name , newAttribute);
    }
  }
}
