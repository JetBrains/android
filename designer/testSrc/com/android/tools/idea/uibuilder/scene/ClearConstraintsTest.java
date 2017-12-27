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
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test the clearing of constraints to ensure they remove constraints and sets there positions and sizes
 */
public class ClearConstraintsTest  extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT)
                   .id("@+id/root")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .withAttribute("android:padding", "20dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@+id/button1")
                       .withBounds(900, 980, 200, 40)
                       .width("100dp")
                       .height("20dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
                     component(TEXT_VIEW)
                       .id("@+id/text1")
                       .withBounds(900, 980, 200, 40)
                       .width("0dp")
                       .height("0dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "@+id/button1")
                       .withAttribute("app:layout_constraintRight_toRightOf", "@+id/button1")
                       .withAttribute("app:layout_constraintTop_toBottomOf", "@+id/button1")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                   ));
  }

  public void testClearingConstraints() {
    myScene.clearAttributes();
    myScreen.get("@+id/button1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/button1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"450dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"490dp\" />");
    myScreen.get("@+id/text1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/text1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"450dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"490dp\" />");
  }
}