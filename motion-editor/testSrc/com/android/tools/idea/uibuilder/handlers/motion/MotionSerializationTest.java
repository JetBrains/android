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
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.StringMTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;

public class MotionSerializationTest extends BaseMotionEditorTest {
  public void testViewClientData() {
    MeModel model = getModel();
    MTag[] cSet = model.motionScene.getChildTags("ConstraintSet");
    MTag constraint = cSet[0].getChildTags()[0];

    String str = "\n" +
                 "<Constraint\n" +
                 "   android:id=\"@+id/dial_pad\" >\n" +
                 "\n" +
                 "  <Layout\n" +
                 "     android:layout_width=\"fill_parent\"\n" +
                 "     motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "     android:layout_height=\"300dp\"\n" +
                 "     motion:layout_constraintTop_toBottomOf=\"parent\"\n" +
                 "     motion:layout_constraintStart_toStartOf=\"parent\" >\n" +
                 "  </Layout>\n" +
                 "</Constraint>\n";

    assertEquals(str,  MTag.serializeTag(constraint));
    StringMTag tag = StringMTag.parse(str);
    assertEquals(str,  MTag.serializeTag(tag));

  }

}
