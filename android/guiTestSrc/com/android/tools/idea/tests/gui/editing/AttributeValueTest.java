/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class AttributeValueTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testAttributeValueInput() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication");
    final EditorFixture editor = guiTest.ideFrame().getEditor();

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    editor.moveBetween("<TextView", "");
    editor.enterText("\nandroid:fontFamily=\"monospace\"");

    // No double quotes have been added because of automatic first quote insertion
    assertThat(editor.getCurrentLine().trim()).isEqualTo("android:fontFamily=\"monospace\"");

    editor.enterText("\nandroid:inputT");
    editor.invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT);

    // Invoking completion adds quotes
    assertThat(editor.getCurrentLine().trim()).isEqualTo("android:inputType=\"\"");
  }
}
