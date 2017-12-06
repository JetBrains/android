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

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.AnchorTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * Test connecting to a guideline
 */
public class SceneGuidelineConnectionTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("500dp")
                                   .height("500dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(CONSTRAINT_LAYOUT_GUIDELINE.defaultName())
                                       .id("@id/guideline")
                                       .withBounds(300, 0, 2, 1000)
                                       .width("0dp")
                                       .height("500dp")
                                       .withAttribute("android:orientation", "vertical")
                                       .withAttribute("app:layout_constraintGuide_percent", "0.3")
                                       .withAttribute("tools:layout_editor_absoluteX", "150dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "0dp"),
                                     component(BUTTON)
                                       .id("@id/button")
                                       .withBounds(500, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "250dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }

  public void testConnectGuideline() {
    myScreen.get("@id/guideline")
      .expectXml("<android.support.constraint.Guideline\n" +
                 "    android:id=\"@id/guideline\"\n" +
                 "    android:layout_width=\"0dp\"\n" +
                 "    android:layout_height=\"500dp\"\n" +
                 "    android:orientation=\"vertical\"\n" +
                 "    app:layout_constraintGuide_percent=\"0.3\"\n" +
                 "    tools:layout_editor_absoluteX=\"150dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"0dp\"/>");
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease(150, myInteraction.getLastY()); // should connect to the guideline
    myScreen.get("@id/button")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"8dp\"\n" +
                 "        android:layout_marginStart=\"8dp\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"@+id/guideline\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
  }
}
