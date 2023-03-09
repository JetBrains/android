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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Data that populates a Timeline row.
 */
public class TimeLineRowData {
  public static final boolean DEBUG = false;
  String mKey;
  String mRef;
  String mName;
  String mKeyProp;
  String mKeyPropToolTip;
  String mType; // Pos, Trig, Att, TCyc, Cyc
  int mKeyPropIndex = 0; // used to map a property to a color

  private static String[] properties =
    {
      "transitionPathRotate",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ",
      "motionProgress"
    };
  private static String[] compact_properties =
    {
      "PathRot",
      "alph",
      "elv",
      "rot",
      "rotX",
      "rotY",
      "scaleX",
      "scaleY",
      "tranX",
      "tranY",
      "tranZ",
      "Prog"
    };
  private static HashMap<String, String> nameMap = new HashMap<>();
  public static final String TYPE_KEY_TRIGGER = "Trig";
  public static final String TYPE_KEY_TIME_CYCLE = "TCyc";
  public static final String TYPE_KEY_CYCLE = "Cyc";
  public static final String TYPE_KEY_POSITION = "Pos";
  public static final String TYPE_KEY_ATTRIBUTE = "Att";

  static {
    nameMap.put("KeyTrigger", TYPE_KEY_TRIGGER);
    nameMap.put("KeyTimeCycle", TYPE_KEY_TIME_CYCLE);
    nameMap.put("KeyCycle", TYPE_KEY_CYCLE);
    nameMap.put("KeyPosition", TYPE_KEY_POSITION);
    nameMap.put("KeyAttribute", TYPE_KEY_ATTRIBUTE);
  }

  // map a property to an index that is associated with a specific color
  private static HashMap<String, Integer> propertyMap = new HashMap<>();

  static {
    for (int i = 0; i < properties.length; i++) {
      propertyMap.put(properties[i], i);
    }
  }

  ArrayList<MTag> mKeyFrames = new ArrayList<>();

  public void buildTargetStrings(MTag keyFrame) {
    String target = keyFrame.getAttributeValue("motionTarget");
    mType = nameMap.get(keyFrame.getTagName());
    if (mType == null) {
      System.err.println(" no name for " + keyFrame.getTagName());
    }
    if (target != null && target.startsWith("@")) {
      mRef = "Id";
      mName = Utils.stripID(target);
    }
    else {
      mRef = "Tg";
      mName = target;
    }
    int mask = 0;
    int count = 0;
    for (int i = 0; i < properties.length; i++) {
      if (keyFrame.getAttributeValue(properties[i]) != null) {
        mask = mask | 1 << i;
        count++;
        if (DEBUG) {
          Debug.log(count + " " + properties[i]);
        }
        mKeyProp = properties[i];
        mKeyPropIndex = propertyMap.getOrDefault(mKeyProp, properties.length - 1);
      }
    }
    if (count > 1) {
      count = 0;
      mKeyProp = "";
      mKeyPropToolTip = "";
      // show the a specific color for a composite of propierties
      mKeyPropIndex = properties.length - 1;
      for (int i = 0; i < properties.length; i++) {
        if ((mask & (1 << i)) != 0) {
          if (count > 0) {
            mKeyProp += ",";
            mKeyPropToolTip += ",";
          }
          mKeyProp += compact_properties[i];
          mKeyPropToolTip += properties[i];
          count++;
        }
      }
    }
    else if (count == 0) {
      mKeyProp = "";
      mKeyPropToolTip = "";
    }

    if (mType.equals("Pos")) {
      mKeyProp = keyFrame.getAttributeValue("keyPositionType");
      if (mKeyProp == null) {
        mKeyProp = "(deltaRelative)";
      }
    }
  }

  public void buildKey(MTag keyFrame) {
    String target = keyFrame.getAttributeValue("motionTarget");
    String key = target;
    if (target != null && target.startsWith("@")) {
      key = "Id:" + Utils.stripID(target);
    }
    else {
      key = "Tag:" + target;
    }
    String name = " " + nameMap.get(keyFrame.getTagName());
    key += name;
    mKey = key;
  }
}
