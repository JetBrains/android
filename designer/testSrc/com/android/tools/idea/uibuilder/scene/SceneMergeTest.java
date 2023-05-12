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

import com.android.AndroidXConstants;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.handlers.MergeDelegateHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutDecorator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public class SceneMergeTest extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(VIEW_MERGE)
                   .id("@+id/root")
                   .withBounds(0, 0, 1000, 1000)
                   .width("1000dp")
                   .height("1000dp")
                   .withAttribute("tools:parentTag", AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
    );
  }

  public void testBasicScene() {
    myScreen.get("@+id/root").expectXml("<merge xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                        "      xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                                        "  android:id=\"@+id/root\"\n" +
                                        "  android:layout_width=\"1000dp\"\n" +
                                        "  android:layout_height=\"1000dp\"\n" +
                                        "  tools:parentTag=\"android.support.constraint.ConstraintLayout\"/>");
    SceneComponent component = myScene.getSceneComponent("root");
    assertTrue(component.getDecorator() instanceof ConstraintLayoutDecorator);
    Project project = component.getNlComponent().getModel().getProject();
    ViewHandler viewGroupHandler = ViewHandlerManager.get(project).getHandler(component.getNlComponent(), () -> {});
    assertTrue(viewGroupHandler instanceof MergeDelegateHandler);
  }
}