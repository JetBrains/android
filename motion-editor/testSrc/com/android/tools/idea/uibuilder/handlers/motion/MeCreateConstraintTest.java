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
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.ConstraintSetPanelCommands;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import java.util.ArrayList;
import java.util.Arrays;

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

  public void testCreateAllConstraints() {
    MeModel model = getModel();
    MTag[] cSet = model.motionScene.getChildTags("ConstraintSet");
    MTag constraintSet = cSet[0];
    MTag[] trans = model.getViewNotInConstraintSet(cSet[0]);
    String original = "\n" +
                      "<ConstraintSet\n" +
                      "   android:id=\"@+id/base_state\" >\n" +
                      "\n" +
                      "  <Constraint\n" +
                      "     android:id=\"@+id/dial_pad\" >\n" +
                      "\n" +
                      "    <Layout\n" +
                      "       motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
                      "       motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                      "       motion:layout_constraintTop_toBottomOf=\"parent\"\n" +
                      "       android:layout_height=\"300dp\"\n" +
                      "       android:layout_width=\"fill_parent\" />\n" +
                      "  </Constraint>\n" +
                      "\n" +
                      "  <Constraint\n" +
                      "     android:id=\"@+id/people_pad\" >\n" +
                      "\n" +
                      "    <Layout\n" +
                      "       motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                      "       motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
                      "       motion:layout_constraintTop_toTopOf=\"parent\"\n" +
                      "       android:layout_height=\"500dp\"\n" +
                      "       android:layout_marginStart=\"4dp\"\n" +
                      "       android:layout_width=\"300dp\" />\n" +
                      "  </Constraint>\n" +
                      "</ConstraintSet>\n";

    assertEquals(original, constraintSet.toFormalXmlString(""));

    ArrayList<MTag> rows = new ArrayList<>(Arrays.asList(trans));
    ConstraintSetPanelCommands.createAllConstraints(rows, constraintSet);
    String created = "\n" +
                     "<ConstraintSet\n" +
                     "   android:id=\"@+id/base_state\" >\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/dial_pad\" >\n" +
                     "\n" +
                     "    <Layout\n" +
                     "       motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
                     "       motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                     "       motion:layout_constraintTop_toBottomOf=\"parent\"\n" +
                     "       android:layout_height=\"300dp\"\n" +
                     "       android:layout_width=\"fill_parent\" />\n" +
                     "  </Constraint>\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people_pad\" >\n" +
                     "\n" +
                     "    <Layout\n" +
                     "       motion:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                     "       motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
                     "       motion:layout_constraintTop_toTopOf=\"parent\"\n" +
                     "       android:layout_height=\"500dp\"\n" +
                     "       android:layout_marginStart=\"4dp\"\n" +
                     "       android:layout_width=\"300dp\" />\n" +
                     "  </Constraint>\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/number\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"parent\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"parent\"\n" +
                     "     android:layout_height=\"wrap_content\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_marginTop=\"8dp\"\n" +
                     "     android:layout_width=\"0dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/dialtitle\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/dial_pad\"\n" +
                     "     android:layout_height=\"wrap_content\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_marginTop=\"8dp\"\n" +
                     "     android:layout_width=\"0dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button1\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button2\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button2\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button2\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button3\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button1\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button3\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button3\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/button6\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button2\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/dial_pad\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button4\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button5\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button5\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button5\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button6\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button4\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button6\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button6\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/button9\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button5\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/button3\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button7\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button8\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button8\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button8\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button9\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button7\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button9\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button9\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/button12\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button8\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/button6\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button10\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button11\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button11\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button11\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/button12\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button10\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/button12\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/button12\"\n" +
                     "     motion:layout_constraintBottom_toBottomOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/dial_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/button11\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/button9\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people_title\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/people_pad\"\n" +
                     "     android:layout_height=\"20dp\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_marginTop=\"8dp\"\n" +
                     "     android:layout_width=\"0dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people1\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/people3\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/people2\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/people_pad\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people2\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/people4\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/people1\"\n" +
                     "     motion:layout_constraintTop_toTopOf=\"@+id/people_pad\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people3\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/people5\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/people4\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/people1\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people4\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/people6\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/people3\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/people2\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people5\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/people7\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/people6\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/people3\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people6\"\n" +
                     "     motion:layout_constraintBottom_toTopOf=\"@+id/people8\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/people5\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/people4\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people7\"\n" +
                     "     motion:layout_constraintBottom_toBottomOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintEnd_toStartOf=\"@+id/people8\"\n" +
                     "     motion:layout_constraintStart_toStartOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/people5\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginStart=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "\n" +
                     "  <Constraint\n" +
                     "     android:id=\"@+id/people8\"\n" +
                     "     motion:layout_constraintBottom_toBottomOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintEnd_toEndOf=\"@+id/people_pad\"\n" +
                     "     motion:layout_constraintStart_toEndOf=\"@+id/people7\"\n" +
                     "     motion:layout_constraintTop_toBottomOf=\"@+id/people6\"\n" +
                     "     android:layout_height=\"64dp\"\n" +
                     "     android:layout_marginEnd=\"8dp\"\n" +
                     "     android:layout_width=\"64dp\" />\n" +
                     "</ConstraintSet>\n";
    assertEquals(created, constraintSet.toFormalXmlString(""));
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
