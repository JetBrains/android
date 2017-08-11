/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.common.fixtures.ModelBuilder;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE;

/**
 * Test dragging a widget
 */
public class SceneDragGuidelineTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("500dp")
                                   .height("500dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(CONSTRAINT_LAYOUT_GUIDELINE)
                                       .id("@id/guideline")
                                       .withBounds(0, 500, 1000, 2)
                                       .width("500dp")
                                       .height("0dp")
                                       .withAttribute("app:layout_constraintGuide_percent", "0.5")
                                       .withAttribute("tools:layout_editor_absoluteX", "0dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "250dp"),
                                     component(CONSTRAINT_LAYOUT_GUIDELINE)
                                       .id("@id/verticalGuideline")
                                       .withBounds(300, 0, 2, 1000)
                                       .width("0dp")
                                       .height("500dp")
                                       .withAttribute("android:orientation", "vertical")
                                       .withAttribute("app:layout_constraintGuide_percent", "0.3")
                                       .withAttribute("tools:layout_editor_absoluteX", "150dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "0dp")
                                   ));
    return builder;
  }

  public void testDragGuideline() {
    myInteraction.mouseDown("guideline");
    myInteraction.mouseRelease(400, 100);
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/guideline\"\n" +
                 "        android:layout_width=\"500dp\"\n" +
                 "        android:layout_height=\"0dp\"\n" +
                 "        app:layout_constraintGuide_percent=\"0.2\" />");
  }

  public void testDragGuidelineRounding() {
    myInteraction.mouseDown("guideline");
    myInteraction.mouseRelease(400, 147.25981f);
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/guideline\"\n" +
                 "        android:layout_width=\"500dp\"\n" +
                 "        android:layout_height=\"0dp\"\n" +
                 "        app:layout_constraintGuide_percent=\"0.29\" />");
  }

  public void testDragGuidelineRoundingUp() {
    myInteraction.mouseDown("guideline");
    myInteraction.mouseRelease(400, 148f);
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/guideline\"\n" +
                 "        android:layout_width=\"500dp\"\n" +
                 "        android:layout_height=\"0dp\"\n" +
                 "        app:layout_constraintGuide_percent=\"0.3\" />");
  }

  public void testDragVerticalGuideline() {
    myInteraction.mouseDown("verticalGuideline");
    myInteraction.mouseRelease(400, 100);
    myScreen.get("@id/verticalGuideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/verticalGuideline\"\n" +
                 "        android:layout_width=\"0dp\"\n" +
                 "        android:layout_height=\"500dp\"\n" +
                 "        android:orientation=\"vertical\"\n" +
                 "        app:layout_constraintGuide_percent=\"0.8\" />");
  }
}
