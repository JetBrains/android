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

import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

/**
 * Test cycles are avoided
 */
public class SceneCycleDetectionTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(BUTTON)
                                       .id("@id/button")
                                       .withBounds(300, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintRight_toRightOf", "@+id/button2")
                                       .withAttribute("app:layout_constraintLeft_toRightOf", "@+id/button2")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "@+id/button2")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "@+id/button2")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(BUTTON)
                                       .id("@id/button2")
                                       .withBounds(100, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintRight_toLeftOf", "@+id/button")
                                       .withAttribute("app:layout_constraintLeft_toLeftOf", "@+id/button")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "@+id/button")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "@+id/button")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }

  public void testHorizontalCycle() {
    SceneComponent component = myScene.getSceneComponent("button");
    assertTrue(ConstraintComponentUtilities.isInChain(ConstraintComponentUtilities.ourLeftAttributes, ConstraintComponentUtilities.ourRightAttributes, component));
    assertNull(ConstraintComponentUtilities.findChainHead(component, ConstraintComponentUtilities.ourLeftAttributes, ConstraintComponentUtilities.ourRightAttributes));
    assertTrue(ConstraintComponentUtilities.isInChain(ConstraintComponentUtilities.ourLeftAttributes, ConstraintComponentUtilities.ourRightAttributes, component.getNlComponent()));
    assertNull(ConstraintComponentUtilities.findChainHead(component.getNlComponent(), ConstraintComponentUtilities.ourLeftAttributes, ConstraintComponentUtilities.ourRightAttributes));
  }

  public void testVerticalCycle() {
    SceneComponent component = myScene.getSceneComponent("button");
    assertTrue(ConstraintComponentUtilities.isInChain(ConstraintComponentUtilities.ourTopAttributes, ConstraintComponentUtilities.ourBottomAttributes, component));
    assertNull(ConstraintComponentUtilities.findChainHead(component, ConstraintComponentUtilities.ourTopAttributes, ConstraintComponentUtilities.ourBottomAttributes));
    assertTrue(ConstraintComponentUtilities.isInChain(ConstraintComponentUtilities.ourTopAttributes, ConstraintComponentUtilities.ourBottomAttributes, component.getNlComponent()));
    assertNull(ConstraintComponentUtilities.findChainHead(component.getNlComponent(), ConstraintComponentUtilities.ourTopAttributes, ConstraintComponentUtilities.ourBottomAttributes));
  }
}
