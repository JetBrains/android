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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.scout.Scout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Check Chain Minipulation APIs.
 */
public class ScoutArrangeChainTest01 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT)
                   .id("@+id/content_main")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@+id/a")
                       .width("wrap_content")
                       .height("wrap_content")
                       .withBounds(0, 0, 500, 200)
                       .withAttribute("app:layout_constraintBottom_toTopOf", "@+id/c")
                       .withAttribute("app:layout_constraintStart_toStartOf", "parent")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                     ,
                     component(TEXT_VIEW)
                       .id("@+id/b")
                       .width("wrap_content")
                       .height("wrap_content")
                       .withBounds(0, 158, 500, 200)
                     ,
                     component(TEXT_VIEW)
                       .id("@+id/c")
                       .width("wrap_content")
                       .height("wrap_content")
                       .withBounds(55, 0, 500, 200)
                       .withAttribute("app:layout_constraintBottom_toTopOf", "@+id/d")
                       .withAttribute("app:layout_constraintStart_toStartOf", "parent")
                       .withAttribute("app:layout_constraintTop_toBottomOf", "@+id/a")
                     ,
                     component(TEXT_VIEW)
                       .id("@+id/d")
                       .width("wrap_content")
                       .height("48dp")
                       .withBounds(55, 292, 500, 200)
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                       .withAttribute("app:layout_constraintHorizontal_bias", "0.5")
                       .withAttribute("app:layout_constraintStart_toStartOf", "parent")
                       .withAttribute("app:layout_constraintTop_toBottomOf", "@+id/c")
                   ));
  }

  public void testChainInsertVertical() {
    myScreen.get("@+id/b")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/b\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"/>");

    List<NlComponent> list = new ArrayList<>(); // testing passing in an empty selection does not crash
    Scout.arrangeWidgets(Scout.Arrange.CenterVertically, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectStart, list, true);
    list.add(myScreen.get("@+id/b").getComponent());
    Scout.arrangeWidgets(Scout.Arrange.ChainInsertVertical, list, true);

    myScreen.get("@+id/b")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/b\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/c\"\n" +
                 "        app:layout_constraintTop_toBottomOf=\"@+id/a\" />");


    buildScene();
  }
}