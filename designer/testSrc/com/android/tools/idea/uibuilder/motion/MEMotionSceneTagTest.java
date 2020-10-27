/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.motion.adapters.MTestXmlFile;
import junit.framework.TestCase;

public class MEMotionSceneTagTest extends TestCase {

  public void testMotionSceneParse() {
    MTestXmlFile sceneFile = new MTestXmlFile();
    MotionSceneTag myTag =  MotionSceneTag.parse(null, null, null, sceneFile);
    assertNotNull("MotionSceneTag.parse return null",myTag);
    assertEquals("root tag ", "MotionScene", myTag.getTagName());
    MTag[]tags = myTag.getChildTags();
    assertEquals("number of Children", 7, tags.length);
    MTag [] tag = myTag.getChildTags("ConstraintSet", "id", "@+id/base_state");
    assertEquals("constraintset with id = start", 1, tag.length);
    tag = myTag.getChildTags("Transition");
    assertEquals("transition", 3, tag.length);
    assertEquals("Transition duration is", "3000", tag[0].getAttributeValue("duration"));
  }
}
