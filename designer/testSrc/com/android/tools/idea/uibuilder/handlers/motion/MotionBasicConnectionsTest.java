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
package com.android.tools.idea.uibuilder.handlers.motion;

import static com.android.AndroidXConstants.MOTION_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BaseLineToggleViewAction;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Test basic connection interactions
 */
public class MotionBasicConnectionsTest extends SceneTest {

  private static final Predicate<ViewAction> BASELINE_ACTION_SELECTOR = target -> target instanceof BaseLineToggleViewAction;

  public void testConnectLeft() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease("root", AnchorTarget.Type.LEFT);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
  }

  public void testConnectTop() {
    myInteraction.select("button", true);
    NlComponent button = myScene.getSceneComponent("button").getNlComponent();
    assertEquals(1, myScreen.getScreen().getSelectionModel().getSelection().size());
    assertEquals(button, myScreen.getScreen().getSelectionModel().getPrimary());
    myInteraction.mouseDown("button", AnchorTarget.Type.TOP);
    myInteraction.mouseRelease("root", AnchorTarget.Type.TOP);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteX=\"100dp\" />");
    assertEquals(button, myScreen.getScreen().getSelectionModel().getPrimary());
    assertEquals(1, myScreen.getScreen().getSelectionModel().getSelection().size());
  }

  public void testConnectRight() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.RIGHT);
    myInteraction.mouseRelease("root", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
  }

  public void testConnectBottom() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.BOTTOM);
    myInteraction.mouseRelease("root", AnchorTarget.Type.BOTTOM);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteX=\"100dp\" />");
  }

  public void testConnectBaseline() {
    myInteraction.select("button2", true);
    myInteraction.performViewAction("button2", BASELINE_ACTION_SELECTOR);
    myInteraction.mouseDown("button2", AnchorTarget.Type.BASELINE);
    myInteraction.mouseRelease("button", AnchorTarget.Type.BASELINE);
    myScreen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBaseline_toBaselineOf=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteX=\"300dp\" />");
    myInteraction.select("button2", false);
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease("button", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBaseline_toBaselineOf=\"@+id/button\"\n" +
                 "        app:layout_constraintStart_toEndOf=\"@+id/button\" />");
  }

  public void testConnectBaselineWithNoId() {
    SceneComponent noIdComponent = null;
    for (SceneComponent component : myScene.getSceneComponents()) {
      if (component.getId() == null) {
        noIdComponent = component;
      }
    }
    assertThat(noIdComponent).isNotNull();
    myInteraction.select(noIdComponent, true);
    myInteraction.performViewAction(noIdComponent, BASELINE_ACTION_SELECTOR);
    myInteraction.mouseDown(noIdComponent, AnchorTarget.Type.BASELINE);
    myInteraction.mouseRelease("button", AnchorTarget.Type.BASELINE);
    assertThat(noIdComponent.getNlComponent().getTagDeprecated().getText())
      .isEqualTo("<TextView\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBaseline_toBaselineOf=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteX=\"500dp\" />");
  }

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(MOTION_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(100, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(TEXT_VIEW)
                                       .id("@id/button2")
                                       .withBounds(300, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "300dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(TEXT_VIEW)
                                       .withBounds(300, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "500dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }
}
