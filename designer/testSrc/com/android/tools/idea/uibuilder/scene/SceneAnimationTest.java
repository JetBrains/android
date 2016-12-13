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

import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.scene.target.AnchorTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test basic animation
 */
public class SceneAnimationTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
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
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }

  public void testConnectRight() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.RIGHT);
    myInteraction.mouseRelease("root", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"200dp\"\n" +
                 "      android:layout_marginRight=\"8dp\"\n" +
                 "      app:layout_constraintRight_toRightOf=\"parent\" />");
    SceneComponent component = myScene.getSceneComponent("button");
    long currentTime = System.currentTimeMillis();
    assertEquals(100, component.getDrawX(currentTime));
    component.getNlComponent().x = 900;
    myScene.setAnimate(true);
    myScene.modelChangedOnLayout(myModel, true);
    myScene.modelChanged(myModel);
    myScene.modelRendered(myModel);
    long time = currentTime + 500;
    try {
      Thread.sleep(100);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    currentTime = System.currentTimeMillis();
    while (currentTime < time) {
      assertTrue(component.getDrawX(currentTime) > 100);
      assertEquals(component.getDrawY(currentTime), 200);
      assertEquals(component.getDrawWidth(currentTime), 100);
      assertEquals(component.getDrawHeight(currentTime), 20);
      myScene.buildDisplayList(myInteraction.getDisplayList(), currentTime);
      currentTime = System.currentTimeMillis();
    }
    assertEquals(900, component.getDrawX(currentTime));
  }

}
