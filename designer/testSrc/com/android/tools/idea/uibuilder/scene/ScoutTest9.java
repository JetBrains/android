/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Check that views get id
 */
public class ScoutTest9 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT)
                   .id("@+id/content_main")
                   .withBounds(0, 0, 720, 1024)
                   .width("360dp")
                   .height("512dp")
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(32, 416, 98, 34)
                       .width("49dp")
                       .height("17dp"),
                     component(TEXT_VIEW)
                       .withBounds(32, 512, 98, 34)
                       .width("49dp")
                       .height("17dp")
                   ));
  }

  public void testRTLScene() {
    myScreen.get("@+id/content_main")
      .expectXml("<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "  android:id=\"@+id/content_main\"\n" +
                 "  android:layout_width=\"360dp\"\n" +
                 "  android:layout_height=\"512dp\">\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:layout_width=\"49dp\"\n" +
                 "    android:layout_height=\"17dp\"/>\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:layout_width=\"49dp\"\n" +
                 "    android:layout_height=\"17dp\"/>\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    WriteCommandAction.runWriteCommandAction(myFacet.getModule().getProject(), () -> {
      Scout.inferConstraintsAndCommit(myModel.getComponents());
    });
    myScreen.get("@+id/content_main")
      .expectXml("<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:id=\"@+id/content_main\"\n" +
                 "  android:layout_width=\"360dp\"\n" +
                 "  android:layout_height=\"512dp\">\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:layout_width=\"0dp\"\n" +
                 "    android:layout_height=\"17dp\"\n" +
                 "      android:id=\"@+id/textView\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"@+id/textView2\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"@+id/textView2\"\n" +
                 "      android:layout_marginBottom=\"31dp\"\n" +
                 "      app:layout_constraintBottom_toTopOf=\"@+id/textView2\" />\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:layout_width=\"49dp\"\n" +
                 "    android:layout_height=\"17dp\"\n" +
                 "      android:id=\"@+id/textView2\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      android:layout_marginStart=\"16dp\" />\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>");
  }
}