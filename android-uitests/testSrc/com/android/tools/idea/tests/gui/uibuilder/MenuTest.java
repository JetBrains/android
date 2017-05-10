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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

@RunIn(TestGroup.UNRELIABLE)  // timeout waiting for Gradle sync: b/37965951
@RunWith(GuiTestRunner.class)
public final class MenuTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private EditorFixture myEditor;
  private Path myMenuPath;

  @Before
  public void setUp() {
    WizardUtils.createNewProject(myGuiTest, "google.com", "Basic Activity");

    myEditor = myGuiTest.ideFrame().getEditor();
    myMenuPath = FileSystems.getDefault().getPath("app", "src", "main", "res", "menu", "menu_main.xml");
  }

  @Test
  public void dragCastButtonIntoActionBar() throws IOException {
    writeSettingsActionMenu();

    myEditor.open(myMenuPath);
    dragAndDrop("Cast Button", new Point(320, 121));
    MessagesFixture.findByTitle(myGuiTest.robot(), "Add Project Dependency").clickOk();

    myGuiTest.ideFrame().waitForGradleProjectSyncToFinish();

    @Language("XML")
    String expected = "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                      "    <item\n" +
                      "        android:id=\"@+id/media_route_menu_item\"\n" +
                      "        android:title=\"Cast\"\n" +
                      "        app:actionProviderClass=\"android.support.v7.app.MediaRouteActionProvider\"\n" +
                      "        app:showAsAction=\"always\"\n" +
                      "        tools:icon=\"@drawable/mr_button_light\" />\n" +
                      "    <item\n" +
                      "        android:id=\"@+id/action_settings\"\n" +
                      "        android:title=\"@string/action_settings\"\n" +
                      "        app:showAsAction=\"always\" />\n" +
                      "</menu>\n";

    myEditor.open(myMenuPath, Tab.EDITOR);
    assertEquals(expected, myEditor.getCurrentFileContents());
  }

  @Test
  public void dragMenuItemIntoActionBar() {
    myEditor.open(myMenuPath);
    dragAndDrop("Menu Item", new Point(380, 120));

    @Language("XML")
    String expected = "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                      "    tools:context=\"com.google.myapplication.MainActivity\">\n" +
                      "    <item\n" +
                      "        android:orderInCategory=\"100\"\n" +
                      "        android:title=\"Item\" />\n" +
                      "    <item\n" +
                      "        android:id=\"@+id/action_settings\"\n" +
                      "        android:orderInCategory=\"101\"\n" +
                      "        android:title=\"@string/action_settings\"\n" +
                      "        app:showAsAction=\"never\" />\n" +
                      "</menu>\n";

    myEditor.open(myMenuPath, Tab.EDITOR);

    myEditor.invokeAction(EditorAction.SELECT_ALL);
    myGuiTest.ideFrame().invokeMenuPath("Code", "Reformat Code");

    assertEquals(expected, myEditor.getCurrentFileContents());
  }

  @Test
  public void dragSearchItemIntoActionBar() throws IOException {
    writeSettingsActionMenu();

    myEditor.open(myMenuPath);
    dragAndDrop("Search Item", new Point(330, 120));

    @Language("XML")
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    String expected = "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                      "    <item\n" +
                      "        android:id=\"@+id/app_bar_search\"\n" +
                      "        android:actionViewClass=\"android.widget.SearchView\"\n" +
                      "        android:icon=\"@drawable/ic_search_black_24dp\"\n" +
                      "        android:title=\"Search\"\n" +
                      "        app:showAsAction=\"always\" />\n" +
                      "    <item\n" +
                      "        android:id=\"@+id/action_settings\"\n" +
                      "        android:title=\"@string/action_settings\"\n" +
                      "        app:showAsAction=\"always\" />\n" +
                      "</menu>\n";

    myEditor.open(myMenuPath, Tab.EDITOR);
    assertEquals(expected, myEditor.getCurrentFileContents());
  }

  private void writeSettingsActionMenu() throws IOException {
    @Language("XML")
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    String xml = "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                 "    <item\n" +
                 "        android:id=\"@+id/action_settings\"\n" +
                 "        android:title=\"@string/action_settings\"\n" +
                 "        app:showAsAction=\"always\" />\n" +
                 "</menu>\n";

    FileUtils.write(myGuiTest.getProjectPath().toPath().resolve(myMenuPath), xml);
  }

  private void dragAndDrop(@NotNull String item, @NotNull Point point) {
    NlEditorFixture layoutEditor = myEditor.getLayoutEditor(false);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.getPaletteItemList(0).drag(item);
    layoutEditor.getSurface().drop(point);
  }
}
