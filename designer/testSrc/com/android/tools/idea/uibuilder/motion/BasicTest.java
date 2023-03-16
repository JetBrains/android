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

import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.motion.adapters.MTagImp;
import java.awt.BorderLayout;
import java.util.function.Predicate;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class BasicTest extends BaseMotionEditorTest {
  private final MTag layout = getLayout();

  private MotionEditor motionSceneUi;
  private FakeUi fakeUi;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    motionSceneUi = new MotionEditor();
    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(motionSceneUi, BorderLayout.CENTER);
    fakeUi = new FakeUi(motionSceneUi, 1.0, true);
    fakeUi.getRoot().setSize(800, 600);
    fakeUi.getRoot().validate();
    fakeUi.layoutAndDispatchEvents();
  }

  private JLabel findErrorLabel() {
    return fakeUi.findComponent(JLabel.class,
                                (Predicate<JLabel>)label -> label.isVisible() && "MotionEditorErrorLabel".equals(label.getName()));
  }

  public void testValidScene() {
    MTag validScene = MTagImp.parse(
      //language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <MotionScene xmlns:android="http://schemas.android.com/apk/res/android"
                     xmlns:motion="http://schemas.android.com/apk/res-auto">
            <Transition
                    motion:constraintSetStart="@+id/base_state"
                    motion:constraintSetEnd="@+id/dial"
                    motion:duration="3000" />
            <ConstraintSet android:id="@+id/half_people"
                           motion:deriveConstraintsFrom="@+id/people">
                <Constraint android:id="@+id/dial_pad">
                    <Layout
                            android:layout_width="fill_parent"
                            android:layout_height="500dp"
                            motion:layout_constraintTop_toBottomOf="parent"/>
                </Constraint>
                <Constraint android:id="@+id/people_pad">
                    <Layout
                            android:layout_width="360dp"
                            android:layout_height="600dp"
                            motion:layout_constraintEnd_toStartOf="parent"
                            motion:layout_constraintTop_toTopOf="parent"/>
                </Constraint>
            </ConstraintSet>
        </MotionScene>""");

    MeModel model = new MeModel(validScene, layout, "foo", "bar");
    motionSceneUi.setMTag(validScene, layout, "foo", "bar", null);
    motionSceneUi.setMTag(model);
  }

  public void testEmptyErrorState() {
    MTag emptyScene = new MTagImp();
    MeModel model = new MeModel(emptyScene, layout, "foo", "bar");
    motionSceneUi.setMTag(emptyScene, layout, "foo", "bar", null);
    motionSceneUi.setMTag(model);

    org.junit.Assert.assertEquals(
      "<HTML>MotionScene Syntax error:<ul>Empty Scene</ul></HTML>",
      findErrorLabel().getText()
    );
  }

  public void testNoTransitionsErrorState() {
    MTag noTransitionsScene = MTagImp.parse(
      //language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <MotionScene xmlns:android="http://schemas.android.com/apk/res/android"
                     xmlns:motion="http://schemas.android.com/apk/res-auto">
            <ConstraintSet android:id="@+id/half_people"
                           motion:deriveConstraintsFrom="@+id/people">
                <Constraint android:id="@+id/dial_pad">
                    <Layout
                            android:layout_width="fill_parent"
                            android:layout_height="500dp"
                            motion:layout_constraintEnd_toEndOf="parent"
                            motion:layout_constraintStart_toStartOf="parent"
                            motion:layout_constraintTop_toBottomOf="parent"/>
                </Constraint>
                <Constraint android:id="@+id/people_pad">
                    <Layout
                            android:layout_width="360dp"
                            android:layout_height="600dp"
                            motion:layout_constraintBottom_toBottomOf="parent"
                            motion:layout_constraintStart_toStartOf="parent"
                            motion:layout_constraintEnd_toStartOf="parent"
                            motion:layout_constraintTop_toTopOf="parent"/>
                </Constraint>
            </ConstraintSet>
        </MotionScene>"""
    );
    MeModel model = new MeModel(noTransitionsScene, layout, "foo", "bar");
    motionSceneUi.setMTag(noTransitionsScene, layout, "foo", "bar", null);
    motionSceneUi.setMTag(model);

    org.junit.Assert.assertEquals(
      "<HTML>MotionScene Syntax error:<ul>At least one transition required.</ul></HTML>",
      findErrorLabel().getText()
    );
  }
}
