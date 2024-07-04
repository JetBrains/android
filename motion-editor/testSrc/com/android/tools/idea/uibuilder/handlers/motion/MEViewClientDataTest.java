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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.uibuilder.handlers.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import java.util.Arrays;
import java.util.HashMap;

public class MEViewClientDataTest extends BaseMotionEditorTest {

  public void testViewClientData() {
    MeModel model = getModel();
    MTag[] cSet = model.motionScene.getChildTags("ConstraintSet");
    MTag constraintSet = cSet[0];

    HashMap<String, MotionAttributes> viewInfo = model.populateViewInfo(constraintSet);
    assertEquals(25,viewInfo.size());
    int sum = 0;
    for (MotionAttributes motionAttributes : viewInfo.values()) {
      HashMap<String, MotionAttributes.DefinedAttribute> map = motionAttributes.getAttrMap();
      System.out.println(motionAttributes.getId() +"   " +map.size()+ Arrays.toString(map.values().toArray()));
      sum+= map.size();
    }

    assertEquals(155,sum);
  }

}
