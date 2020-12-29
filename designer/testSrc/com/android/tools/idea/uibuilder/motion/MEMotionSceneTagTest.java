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
import java.util.ArrayList;
import junit.framework.TestCase;

public class MEMotionSceneTagTest extends TestCase {

  public void testMotionSceneParse() {
    MTestXmlFile sceneFile = new MTestXmlFile(false);
    MotionSceneTag myTag =  MotionSceneTag.parse(null, null, null, sceneFile);
    assertNotNull("MotionSceneTag.parse return null",myTag);
    assertEquals("root tag ", "MotionScene", myTag.getTagName());
    MTag[]tags = myTag.getChildTags();
    assertEquals("number of Children", 8, tags.length);
    MTag [] tag = myTag.getChildTags("ConstraintSet", "id", "@+id/base_state");
    assertEquals("constraintset with id = start", 1, tag.length);
    MTag [] tag2 = myTag.getChildTags("ConstraintSet", "id", "@+id/include_state");
    assertEquals("include constraintset with id = start", 1, tag2.length);
    assertEquals("include getTagName ", "ConstraintSet", tag2[0].getTagName());
    assertEquals("include getAttributeValue id ", "@+id/include_state", tag2[0].getAttributeValue("id"));
    assertEquals("include getChildren ", 2, tag2[0].getChildren().size());
    assertEquals("include getAttrList ", 1, tag2[0].getAttrList().size());
    assertEquals("include getChildTags ", 2, tag2[0].getChildTags().length);
    assertEquals("include getChildTags ", 2, tag2[0].getChildTags("Constraint").length);
    assertEquals("include getChildTags ", 1, tag2[0].getChildTags("id","people_pad" ).length);
    assertEquals("include getChildTags ", 1, tag2[0].getChildTags("Constraint","id","people_pad").length);
    assertEquals("include getTreeId ", "include_state", tag2[0].getTreeId() );

    tag = myTag.getChildTags("Transition");
    assertEquals("transition", 3, tag.length);
    assertEquals("Transition duration is", "3000", tag[0].getAttributeValue("duration"));
  }
}
