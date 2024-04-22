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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides a view within MotionLayout with all the basic information under the ConstraintSet.
 */
public class MotionAttributes {
  private final MTag mConstraintSet;
  private boolean mDefinedLayout = false;
  private boolean mDefinedPropertySet = false;
  private boolean mDefinedTransform = false;
  private boolean mDefinedMotion = false;
  private String mId;
  private String mLayoutFrom = "undefined";

  private HashMap<String , DefinedAttribute> definedAttributes = new HashMap<>();

  public String getId() {
    return mId;
  }

  public String getLayoutSource() {
    return mLayoutFrom;
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

    @NotNull
    public String getName() {
      return name;
    }

    @Nullable
    public String getValue() {
      return value;
    }

    @NotNull
    public String getNamespace() {
      return nameSpace;
    }

    @Nullable
    public String getSourceId() {
      return source_id;
    }

    @Nullable
    public String getCustomType() {
      return customType;
    }

    public boolean isLayoutAttribute() {
      return MotionSceneAttrs.layout_tags.contains(name);
    }

    public boolean isPropertySetAttribute() {
      return MotionSceneAttrs.ourPropertySet_tags.contains(name);
    }

    public boolean isTransformAttribute() {
      return MotionSceneAttrs.ourTransform_tags.contains(name);
    }

    public boolean isMotionAttribute() {
      return MotionSceneAttrs.ourMotion_tags.contains(name);
    }

    public boolean isCustomAttribute() { return customType != null; }
  }

  public MTag getConstraintSet() {
    return mConstraintSet;
  }

  public void fillTagWriter(MTag.TagWriter writer) {
    for (MotionAttributes.DefinedAttribute value : getAttrMap().values()) {
      writer.setAttribute(value.nameSpace, value.name, value.value);
    }
  }

  /**
   * Returns a map if id to DefinedAttributes
   *
   * @return
   */
  public HashMap<String, DefinedAttribute> getAttrMap() {
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
        mLayoutFrom = "MotionLayout";
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

  public MotionAttributes(String id,MTag constraintSet) {
    this.mId = id;
    mConstraintSet = constraintSet;
  }

  public void dumpList() {
    Debug.log("   " + mId);
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


  static HashMap<String,HashSet<String>> validMap = new HashMap<>( );
  static {
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_COLOR_VALUE, new HashSet<>(Arrays.asList("int")));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE, new HashSet<>(Arrays.asList("Drawable")));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_INTEGER_VALUE, new HashSet<>(Arrays.asList("int")));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_FLOAT_VALUE, new HashSet<>(Arrays.asList("float")));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_STRING_VALUE, new HashSet<>(Arrays.asList("CharSequence", "String")));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_DIMENSION_VALUE, new HashSet<>(Arrays.asList("float" )));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE, new HashSet<>(Arrays.asList("float" )));
    validMap.put(MotionSceneAttrs.ATTR_CUSTOM_BOOLEAN_VALUE, new HashSet<>(Arrays.asList("boolean")));
  }

  public static Set<String> getCustomAttributesFor(NlComponent nlComponent, String customAttributeType) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(nlComponent);
    if (viewInfo == null) {
      return Collections.emptySet();
    }
    Object o = viewInfo.getViewObject();
    if (o == null) {
      return Collections.emptySet();
    }
    Method[] m = o.getClass().getMethods();
    HashSet<String> ret = new HashSet<>();
    HashSet<String> valid  = validMap.get(customAttributeType);
    for (int i = 0; i < m.length; i++) {
      Method method = m[i];
      String name = method.getName();
      if (!name.startsWith("set")) {
        continue;
      }
      if (!Character.isUpperCase(name.charAt(3))) {
        continue;
      }
      Class<?>[] types = method.getParameterTypes();
      if (types.length != 1) {
        continue;
      }
      String typeStr = types[0].getSimpleName();
      if (valid.contains(typeStr)) {
        name = Character.toLowerCase(name.charAt(3))+name.substring(4);
        ret.add(name);
      }
    }
    return ret;
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
        mLayoutFrom = constraintSetId;
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
        if (!mDefinedLayout) {
          mLayoutFrom = constraintSetId;
        }
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
