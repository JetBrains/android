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

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;

public class AttributeValueTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testAttributeValueInput() throws IOException {
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("SimpleApplication");
    final EditorFixture editor = myProjectFrame.getEditor();

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("<TextView|"));
    editor.enterText("\nandroid:fontFamily=\"monospace\"");

    // No double quotes have been added because of automatic first quote insertion
    Assert.assertEquals("android:fontFamily=\"monospace\"", editor.getCurrentLineContents(true, false, 0));

    editor.enterText("\nandroid:inputT");
    editor.invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT);

    // Invoking completion adds quotes
    Assert.assertEquals("android:inputType=\"\"", editor.getCurrentLineContents(true, false, 0));
  }
}
