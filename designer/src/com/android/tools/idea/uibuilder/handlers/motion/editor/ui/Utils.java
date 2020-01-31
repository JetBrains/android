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

/**
 * Small static functions used across the system
 */
public class Utils {

  public static String stripID(String id) {
    if (id == null) {
      return "";
    }
    int index = id.indexOf('/');
    if (index < 0) {
      return id;
    }
    return id.substring(index + 1).trim();
  }

  public static String formatTransition(MTag tag) {
    String id = Utils.stripID(tag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_ID));
    String start = Utils.stripID(tag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START));
    String end = Utils.stripID(tag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END));
    return formatTransition(id, start, end);
  }

  public static String formatTransition(String id, String start, String end) {
    if (id == null || id.length() == 0) {
      return start + "->" + end;
    }
    return id;
  }
}
