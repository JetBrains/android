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

import static com.android.SdkConstants.*;

public class TooltipTest extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT.defaultName())
                   .id("@+id/root")
                   .withBounds(0, 0, 1000, 1000)
                   .children(
                     component(PROGRESS_BAR)
                       .id("@+id/a")
                       .withBounds(450, 490, 100, 20)
                       .width("100dp")
                       .height("20dp"),
                     component(BUTTON)
                       .withBounds(450, 490, 100, 20)
                   ));
  }

  public void testBaseTooltips() {
    assertEquals("root", myScreen.get("@+id/root").getComponent().getTooltipText());
    assertEquals("a", myScreen.get("@+id/a").getComponent().getTooltipText());
    assertEquals("Button", myScreen.getByTag(BUTTON).getComponent().getTooltipText());
  }

  // TODO: tests for other targets
}
