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
import com.android.tools.idea.tests.gui.framework.GuiTestFileUtils;
import com.android.tools.idea.tests.util.WizardUtils;
import org.fest.swing.fixture.JListFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

@RunWith(GuiTestRunner.class)
public final class MenuTest {
  @Language("XML")
  @SuppressWarnings("XmlUnusedNamespaceDeclaration")
  private static final String MENU_MAIN_XML_CONTENTS = "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                       "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                                       "    xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                                                       "    <item\n" +
                                                       "        android:id=\"@+id/action_settings\"\n" +
                                                       "        android:title=\"@string/action_settings\"\n" +
                                                       "        app:showAsAction=\"always\" />\n" +
                                                       "</menu>\n";

  private static final Path MENU_MAIN_XML_RELATIVE_PATH =
    FileSystems.getDefault().getPath("app", "src", "main", "res", "menu", "menu_main.xml");

  private static final Path IC_SEARCH_BLACK_24DP_XML_RELATIVE_PATH =
    FileSystems.getDefault().getPath("app", "src", "main", "res", "drawable", "ic_search_black_24dp.xml");

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private Path myMenuMainXmlAbsolutePath;
  private Path myIcSearchBlack24dpXmlAbsolutePath;

  private EditorFixture myEditor;

  @Before
  public void setUp() {
    WizardUtils.createNewProject(myGuiTest, "Basic Activity");

    Path path = myGuiTest.getProjectPath().toPath();
    myMenuMainXmlAbsolutePath = path.resolve(MENU_MAIN_XML_RELATIVE_PATH);
    myIcSearchBlack24dpXmlAbsolutePath = path.resolve(IC_SEARCH_BLACK_24DP_XML_RELATIVE_PATH);

    myEditor = myGuiTest.ideFrame().getEditor();
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/66470893
  @Test
  public void dragCastButtonIntoActionBar() throws IOException {
    GuiTestFileUtils.writeAndReloadDocument(myMenuMainXmlAbsolutePath, MENU_MAIN_XML_CONTENTS);

    myEditor.open(MENU_MAIN_XML_RELATIVE_PATH);
    dragAndDrop("Cast Button", new Point(320, 121));

    MessagesFixture.findByTitle(myGuiTest.robot(), "Add Project Dependency").clickOk();
    myGuiTest.ideFrame().waitForGradleProjectSyncToFinish();

    myEditor.open(MENU_MAIN_XML_RELATIVE_PATH, Tab.EDITOR);

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

    assertEquals(expected, myEditor.getCurrentFileContents());
  }

  @Test
  public void dragMenuItemIntoActionBar() {
    myEditor.open(MENU_MAIN_XML_RELATIVE_PATH);
    dragAndDrop("Menu Item", new Point(380, 120));
    myEditor.open(MENU_MAIN_XML_RELATIVE_PATH, Tab.EDITOR);

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

    assertEquals(expected, myEditor.getCurrentFileContents());
  }

  @Test
  public void dragSearchItemIntoActionBar() throws IOException {
    GuiTestFileUtils.writeAndReloadDocument(myMenuMainXmlAbsolutePath, MENU_MAIN_XML_CONTENTS);

    myEditor.open(MENU_MAIN_XML_RELATIVE_PATH);
    dragAndDrop("Search Item", new Point(330, 120));
    myEditor.open(MENU_MAIN_XML_RELATIVE_PATH, Tab.EDITOR);

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

    assertEquals(expected, myEditor.getCurrentFileContents());
    assertTrue(Files.exists(myIcSearchBlack24dpXmlAbsolutePath));

    myEditor.invokeAction(EditorAction.UNDO);

    assertEquals(MENU_MAIN_XML_CONTENTS, myEditor.getCurrentFileContents());
    assertFalse(Files.exists(myIcSearchBlack24dpXmlAbsolutePath));
  }

  private void dragAndDrop(@NotNull String item, @NotNull Point point) {
    NlEditorFixture editor = myEditor.getLayoutEditor(false);
    editor.waitForRenderToFinish();

    JListFixture list = editor.getPalette().getItemList("");
    list.replaceCellReader(new ItemTitleListCellReader());
    list.drag(item);

    editor.getSurface().drop(point);
  }
}