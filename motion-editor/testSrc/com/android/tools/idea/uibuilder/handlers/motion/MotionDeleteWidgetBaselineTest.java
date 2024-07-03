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
package com.android.tools.idea.uibuilder.handlers.motion;

import static com.android.AndroidXConstants.MOTION_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Test delete a widget with another depending on it (baseline)
 */
public class MotionDeleteWidgetBaselineTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(MOTION_LAYOUT.defaultName())
                   .id("@id/root")
                   .withBounds(0, 0, 1000, 1000)
                   .width("1000dp")
                   .height("1000dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@id/button")
                       .withBounds(134, 166, 100, 50)
                       .width("100dp")
                       .height("50dp")
                       .withAttribute("app:layout_constraintBaseline_toBaselineOf", "@+id/button2")
                       .withAttribute("android:layout_marginRight", "77dp")
                       .withAttribute("app:layout_constraintRight_toRightOf", "@+id/button2")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                       .withAttribute("android:layout_marginLeft", "8dp")
                       .withAttribute("app:layout_constraintHorizontal_bias", "1.0"),
                     component(TEXT_VIEW)
                       .id("@id/button2")
                       .withBounds(223, 166, 100, 50)
                       .width("100dp")
                       .height("50dp")
                       .withAttribute("android:text","Button")
                       .withAttribute("android:layout_marginTop", "166dp")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                       .withAttribute("android:layout_marginRight", "8dp")
                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                       .withAttribute("android:layout_marginLeft", "8dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                       .withAttribute("app:layout_constraintHorizontal_bias", "0.767")
                   ));
  }

  public void testDelete() {
    SceneComponent layout = myScene.getSceneComponent("root");
    NlComponent layoutComponent = layout.getNlComponent();
    ViewGroupHandler handler = (ViewGroupHandler)NlComponentHelperKt.getViewHandler(layoutComponent, () -> {});
    ArrayList<NlComponent> deleted = new ArrayList<>();
    deleted.add(myScene.getSceneComponent("button2").getNlComponent());
    handler.deleteChildren(layoutComponent, deleted);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"50dp\"\n" +
                 "        android:layout_marginLeft=\"8dp\"\n" +
                 "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteY=\"83dp\" />");
  }
}