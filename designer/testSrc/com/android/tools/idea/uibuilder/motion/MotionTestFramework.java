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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.motion.adapters.MTagImp;
import com.android.tools.idea.uibuilder.motion.adapters.samples.layout_16_xml;
import com.android.tools.idea.uibuilder.motion.adapters.samples.motion_scene_16_xml;

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
}
