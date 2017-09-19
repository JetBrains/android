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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.application.ApplicationManager;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.junit.Assert.assertEquals;

@RunIn(TestGroup.UNRELIABLE)  // b/63164506
@RunWith(GuiTestRunner.class)
public class ConvertToConstraintLayoutTest {
  private static final Pattern TOOLS_DIMENSION = Pattern.compile("tools:(.*)=\"(.*)dp\"");
  private static final Pattern ANDROID_DIMENSION = Pattern.compile("android:(.*)=\"(.*)dp\"");

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testConvert() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first button
    NlComponentFixture button = layout.findView("Button", 0);
    button.invokeContextMenuAction("Convert AbsoluteLayout to ConstraintLayout");

    // Confirm dialog
    DialogFixture quickFixDialog = WindowFinder.findDialog(Matchers.byTitle(Dialog.class, "Convert to ConstraintLayout"))
      .withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    // Press OK
    JButtonFixture finish = quickFixDialog.button(withText("OK"));
    finish.click();

    // Check that we've converted to what we expected
    layout.waitForRenderToFinish();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    waitForScout();

    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                 "    android:id=\"@+id/absoluteLayout\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@+id/button\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:layout_marginStart=\"<test>\"\n" +
                 "        android:layout_marginTop=\"<test>\"\n" +
                 "        android:text=\"Button\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@+id/button2\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:layout_marginStart=\"<test>\"\n" +
                 "        android:text=\"Button\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"@+id/button\"\n" +
                 "        app:layout_constraintTop_toBottomOf=\"@+id/button\" />\n" +
                 "\n" +
                 "    <EditText\n" +
                 "        android:id=\"@+id/editText\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:layout_marginEnd=\"<test>\"\n" +
                 "        android:ems=\"10\"\n" +
                 "        android:inputType=\"textPersonName\"\n" +
                 "        android:text=\"Name\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
                 "\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@+id/button3\"\n" +
                 "        android:layout_width=\"<test>\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:layout_marginEnd=\"<test>\"\n" +
                 "        android:layout_marginStart=\"<test>\"\n" +
                 "        android:text=\"Button\"\n" +
                 "        app:layout_constraintBaseline_toBaselineOf=\"@+id/button5\"\n" +
                 "        app:layout_constraintEnd_toStartOf=\"@+id/button5\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\" />\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@+id/button5\"\n" +
                 "        android:layout_width=\"<test>\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:layout_marginBottom=\"<test>\"\n" +
                 "        android:layout_marginEnd=\"<test>\"\n" +
                 "        android:text=\"Button\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintStart_toEndOf=\"@+id/button3\" />\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@+id/button6\"\n" +
                 "        android:layout_width=\"<test>\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:text=\"Button\"\n" +
                 "        app:layout_constraintBaseline_toBaselineOf=\"@+id/button5\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintStart_toEndOf=\"@+id/button3\" />\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>\n" +
                 "\n";

    assertEquals(wipeDimensions(xml), wipeDimensions(editor.getCurrentFileContents()));
  }

  private static String wipeDimensions(@Language("XML") String xml) {
    // Remove specific pixel sizes from an XML layout before pretty printing it; they may very from machine
    // to machine. It's the constraints that matter.
    xml = TOOLS_DIMENSION.matcher(xml).replaceAll("tools:$1=\"<test>\"");
    xml = ANDROID_DIMENSION.matcher(xml).replaceAll("android:$1=\"<test>\"");

    return xml;
  }

  @Test
  public void testConvert2() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text View
    NlComponentFixture button = layout.findView("TextView", 0);
    button.invokeContextMenuAction("Convert LinearLayout to ConstraintLayout");

    // Confirm dialog
    DialogFixture quickFixDialog = WindowFinder.findDialog(Matchers.byTitle(Dialog.class, "Convert to ConstraintLayout"))
      .withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    // Press OK
    JButtonFixture finish = quickFixDialog.button(withText("OK"));
    finish.click();

    // Check that we've converted to what we expected
    layout.waitForRenderToFinish();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    waitForScout();

    @Language("XML")
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                 "    android:id=\"@+id/linearLayout\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"wrap_content\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@+id/title\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:text=\"Welcome\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/attending_remotely\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"@+id/attending_remotely\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_chainStyle=\"packed\" />\n" +
                 "\n" +
                 "    <FrameLayout\n" +
                 "        android:id=\"@+id/attending_remotely\"\n" +
                 "        android:layout_width=\"<test>\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:foreground=\"?android:selectableItemBackground\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/attending_in_person\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toBottomOf=\"@+id/title\">\n" +
                 "\n" +
                 "        <ImageView\n" +
                 "            android:layout_width=\"<test>\"\n" +
                 "            android:layout_height=\"<test>\"\n" +
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
                 "        android:layout_width=\"<test>\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:foreground=\"?android:selectableItemBackground\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toBottomOf=\"@+id/attending_remotely\">\n" +
                 "\n" +
                 "        <ImageView\n" +
                 "            android:layout_width=\"<test>\"\n" +
                 "            android:layout_height=\"<test>\"\n" +
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

    assertEquals(wipeDimensions(xml), wipeDimensions(editor.getCurrentFileContents()));
  }

  private void waitForScout() {
    ApplicationManager.getApplication().invokeLater(() -> {
    });

    guiTest.robot().waitForIdle();
  }
}
