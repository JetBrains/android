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
package com.android.tools.idea.uibuilder.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.motion.adapters.MTagImp;
import com.android.tools.idea.uibuilder.motion.adapters.samples.layout_16_xml;
import com.android.tools.idea.uibuilder.motion.adapters.samples.motion_scene_16_xml;

import com.android.tools.idea.uibuilder.motion.adapters.samples.simple_scene_xml;
import java.io.InputStream;

public class MotionTestFramework extends BaseMotionEditorTest {

  public void testLayoutFileAccess() {
    InputStream stream = layout_16_xml.asStream();
    assertNotNull(" unable to access layout_", stream);
  }

  public void testSceneFileAccess() {
    InputStream stream = motion_scene_16_xml.asStream();
  }

  public void testLoadingOfXml() {
    InputStream layout_stream = layout_16_xml.asStream();
    assertNotNull(" unable to access layout", layout_stream);
    String layoutStr = convert(layout_stream);

    InputStream scene_stream = motion_scene_16_xml.asStream();
    assertNotNull(" unable to access scene", scene_stream);
    String msStr = convert(scene_stream);
    MTag layout = MTagImp.parse(layoutStr);
    MTag motionScene = MTagImp.parse(msStr);
    assertTrue(true);
  }

  public void testDelete() {
    InputStream scene_stream = simple_scene_xml.asStream();
    assertNotNull(" unable to access scene", scene_stream);
    String msStr = convert(scene_stream);
    MTag motionScene = MTagImp.parse(msStr);
    MotionSceneUtils.deleteRelatedConstraintSets(motionScene,"button29");
    String str = "\n" +
                 "<MotionScene\n" +
                 "   xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "   xmlns:motion=\"http://schemas.android.com/apk/res-auto\" >\n" +
                 "\n" +
                 "  <Transition\n" +
                 "     motion:constraintSetEnd=\"@+id/end\"\n" +
                 "     motion:constraintSetStart=\"@id/start\"\n" +
                 "     motion:duration=\"1000\" >\n" +
                 "\n" +
                 "    <KeyFrameSet />\n" +
                 "  </Transition>\n" +
                 "\n" +
                 "  <ConstraintSet\n" +
                 "     android:id=\"@+id/start\" />\n" +
                 "\n" +
                 "  <ConstraintSet\n" +
                 "     android:id=\"@+id/end\" >\n" +
                 "\n" +
                 "    <Constraint\n" +
                 "       android:id=\"@+id/move\"\n" +
                 "       motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "       motion:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "       android:layout_height=\"wrap_content\"\n" +
                 "       android:layout_marginLeft=\"216dp\"\n" +
                 "       android:layout_marginStart=\"216dp\"\n" +
                 "       android:layout_marginTop=\"76dp\"\n" +
                 "       android:layout_width=\"wrap_content\" />\n" +
                 "  </ConstraintSet>\n" +
                 "</MotionScene>\n";
    assertEquals(str, motionScene.toXmlString());
  }
}
