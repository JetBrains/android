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
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.ConstraintSetPanelCommands;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;

/**
 * Test Creation of Constraint
 */
public class MeCreateConstraintTest extends BaseMotionEditorTest {

  public void testCreateConstraint() {
    MeModel model = getModel();
    MTag[] cSet = model.motionScene.getChildTags("ConstraintSet");
    MTag constraintSet = cSet[0];
    MTag[] trans = model.getViewNotInConstraintSet(cSet[0]);

    MTag tag = ConstraintSetPanelCommands.createConstraint(trans[0], constraintSet);
    String created = "\n" +
      "<Constraint\n" +
      "   android:id=\"@+id/number\"\n" +
      "   motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
      "   motion:layout_constraintStart_toStartOf=\"parent\"\n" +
      "   motion:layout_constraintTop_toTopOf=\"parent\"\n" +
      "   android:layout_height=\"wrap_content\"\n" +
      "   android:layout_marginEnd=\"8dp\"\n" +
      "   android:layout_marginStart=\"8dp\"\n" +
      "   android:layout_marginTop=\"8dp\"\n" +
      "   android:layout_width=\"0dp\" />\n";
    assertEquals(created, tag.toFormalXmlString(""));
  }

  public void testCreateSectionedConstraint() {
    MeModel model = getModel();
    MTag[] cSet = model.motionScene.getChildTags("ConstraintSet");
    MTag constraintSet = cSet[0];
    MTag[] trans = model.getViewNotInConstraintSet(cSet[0]);

    MTag tag = ConstraintSetPanelCommands.createSectionedConstraint(trans[1], constraintSet);
    String created = "\n" +
      "<Constraint\n" +
      "   android:id=\"@+id/dialtitle\" >\n" +
      "\n" +
      "  <Layout\n" +
      "     motion:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
      "     motion:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
      "     motion:layout_constraintTop_toTopOf=\"@+id/dial_pad\"\n" +
      "     android:layout_height=\"wrap_content\"\n" +
      "     android:layout_marginEnd=\"8dp\"\n" +
      "     android:layout_marginStart=\"8dp\"\n" +
      "     android:layout_marginTop=\"8dp\"\n" +
      "     android:layout_width=\"0dp\" />\n" +
      "</Constraint>\n";
    MTag[] c = tag.getChildTags();
    assertEquals(created, tag.toFormalXmlString(""));
  }
}
