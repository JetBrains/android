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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.junit.Assert.assertNotNull;

@RunIn(TestGroup.LAYOUT)
@RunWith(GuiTestRunner.class)
public class ConvertToConstraintLayoutTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testConvert() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForRenderToFinish();

    // Find and click the first button
    NlComponentFixture button = layout.findView("Button", 0);
    button.invokeContextMenuAction("Convert AbsoluteLayout to ConstraintLayout");

    // Confirm dialog
    DialogFixture quickFixDialog = WindowFinder.findDialog(new GenericTypeMatcher<Dialog>(Dialog.class) {
      @Override
      protected boolean isMatching(@NotNull Dialog dialog) {
        return dialog.isShowing() && "Convert to ConstraintLayout".equals(dialog.getTitle());
      }
    }).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    // Press OK
    JButtonFixture finish = quickFixDialog.button(withText("OK"));
    finish.click();

    // Check that we've converted to what we expected
    layout.waitForRenderToFinish();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);

    // TODO: Get test fixture to wait for Scout call

    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:text=\"Button\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:id=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteX=\"19dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"18dp\" />\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:text=\"Button\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:id=\"@+id/button2\"\n" +
                 "        tools:layout_editor_absoluteX=\"22dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"66dp\" />\n" +
                 "\n" +
                 "    <EditText\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:inputType=\"textPersonName\"\n" +
                 "        android:text=\"Name\"\n" +
                 "        android:ems=\"10\"\n" +
                 "        android:id=\"@+id/editText\"\n" +
                 "        tools:layout_editor_absoluteX=\"124dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"222dp\" />\n" +
                 "\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:text=\"Button\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:id=\"@+id/button3\"\n" +
                 "        tools:layout_editor_absoluteX=\"33dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"432dp\" />\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:text=\"Button\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:id=\"@+id/button5\"\n" +
                 "        tools:layout_editor_absoluteX=\"152dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"432dp\" />\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:text=\"Button\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:id=\"@+id/button6\"\n" +
                 "        tools:layout_editor_absoluteX=\"272dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"432dp\" />\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>\n" +
                 "\n";
    assertThat(editor.getCurrentFileContents()).isEqualTo(xml);
  }

  @Test
  public void testConvert2() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForRenderToFinish();

    // Find and click the first text View
    NlComponentFixture button = layout.findView("TextView", 0);
    button.invokeContextMenuAction("Convert LinearLayout to ConstraintLayout");

    // Confirm dialog
    DialogFixture quickFixDialog = WindowFinder.findDialog(new GenericTypeMatcher<Dialog>(Dialog.class) {
      @Override
      protected boolean isMatching(@NotNull Dialog dialog) {
        return dialog.isShowing() && "Convert to ConstraintLayout".equals(dialog.getTitle());
      }
    }).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    // Press OK
    JButtonFixture finish = quickFixDialog.button(withText("OK"));
    finish.click();

    // Check that we've converted to what we expected
    layout.waitForRenderToFinish();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);

    // TODO: Get test fixture to wait for Scout call

    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:orientation=\"vertical\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@+id/title\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:text=\"Welcome\"\n" +
                 "        tools:layout_editor_absoluteX=\"0dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"0dp\" />\n" +
                 "\n" +
                 "    <FrameLayout\n" +
                 "        android:id=\"@+id/attending_remotely\"\n" +
                 "        android:layout_width=\"match_parent\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:foreground=\"?android:selectableItemBackground\"\n" +
                 "        tools:layout_editor_absoluteX=\"0dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"16dp\">\n" +
                 "\n" +
                 "        <ImageView\n" +
                 "            android:layout_width=\"100dp\"\n" +
                 "            android:layout_height=\"100dp\"\n" +
                 "            android:adjustViewBounds=\"true\"\n" +
                 "            android:scaleType=\"centerInside\" />\n" +
                 "\n" +
                 "        <TextView\n" +
                 "            android:layout_width=\"wrap_content\"\n" +
                 "            android:layout_height=\"wrap_content\"\n" +
                 "            android:layout_gravity=\"bottom|end|right\"\n" +
                 "            android:text=\"Remotely\" />\n" +
                 "\n" +
                 "    </FrameLayout>\n" +
                 "\n" +
                 "    <FrameLayout\n" +
                 "        android:id=\"@+id/attending_in_person\"\n" +
                 "        android:layout_width=\"match_parent\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:foreground=\"?android:selectableItemBackground\"\n" +
                 "        tools:layout_editor_absoluteX=\"0dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"116dp\">\n" +
                 "\n" +
                 "        <ImageView\n" +
                 "            android:layout_width=\"100dp\"\n" +
                 "            android:layout_height=\"100dp\"\n" +
                 "            android:adjustViewBounds=\"true\"\n" +
                 "            android:scaleType=\"centerInside\" />\n" +
                 "\n" +
                 "        <TextView\n" +
                 "            android:layout_width=\"wrap_content\"\n" +
                 "            android:layout_height=\"wrap_content\"\n" +
                 "            android:layout_gravity=\"bottom|end|right\"\n" +
                 "            android:text=\"In Person\" />\n" +
                 "\n" +
                 "    </FrameLayout>\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>\n";
    assertThat(editor.getCurrentFileContents()).isEqualTo(xml);
  }
}
