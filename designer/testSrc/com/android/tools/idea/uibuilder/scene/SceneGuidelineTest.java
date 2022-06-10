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
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineCycleTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test guideline interactions
 */
public class SceneGuidelineTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 2000, 2000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(900, 980, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                                     ,component(CONSTRAINT_LAYOUT_GUIDELINE.defaultName())
                                     .id("@id/guideline")
                                     .withBounds(200, 0, 200, 2000)
                                     .width("wrap_content")
                                     .height("wrap_content")
                                       .withAttribute("android:orientation", "vertical")
                                       .withAttribute("app:layout_constraintGuide_begin", "100dp")
                                   ));
    return builder;
  }

  public void testCycleGuideline() {
    myInteraction.mouseDown("guideline", GuidelineCycleTarget.class, 0);
    myInteraction.mouseRelease("guideline", GuidelineCycleTarget.class, 0);
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/guideline\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:orientation=\"vertical\"\n" +
                 "        app:layout_constraintGuide_end=\"900dp\" />");
    myInteraction.mouseDown("guideline", GuidelineCycleTarget.class, 0);
    myInteraction.mouseRelease("guideline", GuidelineCycleTarget.class, 0);
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/guideline\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:orientation=\"vertical\"\n" +
                 "        app:layout_constraintGuide_percent=\"0.1\" />");
    myInteraction.mouseDown("guideline", GuidelineCycleTarget.class, 0);
    myInteraction.mouseRelease("guideline", GuidelineCycleTarget.class, 0);
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "        android:id=\"@id/guideline\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:orientation=\"vertical\"\n" +
                 "        app:layout_constraintGuide_begin=\"100dp\" />");
  }
}
