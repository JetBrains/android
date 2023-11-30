/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editors;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.JBList;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JListItemFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class LiveTemplatesTestUtil {

  protected static String JAVA_FILE = "/app/src/main/java/google/simpleapplication/MyActivity.java";
  protected static String STATEMENT = "setContentView\\(R.layout.activity_my\\);";

  protected static JListFixture clickOnCodeInsertLiveTemplate(@NotNull GuiTestRule guiTest, @NotNull IdeFrameFixture ideFrame,
                                                              @NotNull EditorFixture editorFixture) {
    editorFixture.waitForFileToActivate();
    ideFrame.requestFocusIfLost();
    editorFixture.moveBetween("", "setContentView(R.layout.activity_my);")
      .enterText("\n");
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);

    ideFrame.waitAndInvokeMenuPath("Code", "Insert Live Template...");

    Ref<JBList> out = new Ref<>();
    Wait.seconds(10).expecting("Live Templates list to show.").until(() -> {
      // There is only one JBList.
      JBList jbList = ideFrame.robot().finder().findByType(ideFrame.target(), JBList.class);

      if (jbList == null) {
        return false;
      }

      out.set(jbList);
      return true;
    });

    JListFixture listFixture= new JListFixture(ideFrame.robot(), out.get());
    return listFixture;
  }

  protected static void doubleTapToInsertLiveTemplate(@NotNull GuiTestRule guiTest,  @NotNull IdeFrameFixture ideFrame,
                                                      @NotNull EditorFixture editorFixture,
                                                      String itemText) {
    JListFixture listFixture = clickOnCodeInsertLiveTemplate(guiTest, ideFrame, editorFixture);
    JListItemFixture jListItemFixture = listFixture.item(Pattern.compile(".*" + itemText + ".*", Pattern.DOTALL));
    jListItemFixture.doubleClick();
  }

}
