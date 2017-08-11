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
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.AnchorTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

/**
 * Test complex baseline connection interactions
 */
public class SceneComplexBaselineConnectionTest extends SceneTest {

  public void testConnectBaseline() {
    myInteraction.select("button1", true);
    myInteraction.mouseDown("button1", ActionTarget.class, 1);
    myInteraction.mouseRelease("button1", ActionTarget.class, 1);
    myInteraction.mouseDown("button1", AnchorTarget.Type.BASELINE);
    myInteraction.mouseRelease("button2", AnchorTarget.Type.BASELINE);
    myScreen.get("@id/button1")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"50dp\"\n" +
                 "        android:text=\"Button\"\n" +
                 "        app:layout_constraintBaseline_toBaselineOf=\"@+id/button2\"\n" +
                 "        tools:layout_editor_absoluteX=\"56dp\" />");
  }

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .children(
                                     component(BUTTON)
                                       .id("@id/button1")
                                       .withBounds(56, 295, 100, 50)
                                       .width("100dp")
                                       .height("50dp")
                                       .withAttribute("android:text", "Button")
                                       .withAttribute("tools:layout_editor_absoluteX", "56dp")
                                       .withAttribute("android:layout_marginBottom", "46dp")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                                       .withAttribute("android:layout_marginTop", "8dp")
                                       .withAttribute("app:layout_constraintVertical_bias", "0.704"),
                                     component(BUTTON)
                                       .id("@id/button2")
                                       .withBounds(250, 170, 100, 50)
                                       .width("100dp")
                                       .height("50dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "250dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "170dp")
                                   ));
    return builder;
  }
}
