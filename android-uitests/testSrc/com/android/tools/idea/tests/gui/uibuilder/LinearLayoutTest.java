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
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.GuiTestFileUtils;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.structure.StructureTreeDecorator;
import com.android.xml.XmlBuilder;
import icons.StudioIcons;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public final class LinearLayoutTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private Path myProjectPath;
  private Path myNewStylePath;
  private Path myMainStylePath;
  private Path myLayoutPath;

  @Ignore("b/66680171")
  @Before
  public void setUp() {
    WizardUtils.createNewProject(myGuiTest, "Empty Activity");
    myProjectPath = myGuiTest.getProjectPath().toPath();

    FileSystem fileSystem = FileSystems.getDefault();

    myNewStylePath = fileSystem.getPath("app", "src", "main", "res", "values", "linear_layout.xml");
    myLayoutPath = fileSystem.getPath("app", "src", "main", "res", "layout", "linear_layout.xml");
    myMainStylePath = fileSystem.getPath("app", "src", "main", "res", "values", "styles.xml");
  }

  @Test
  public void resolveAttributeInStyle() throws IOException {
    // @formatter:off
    String xml = new XmlBuilder()
      .startTag("resources")
        .startTag("style")
        .attribute("name", "linear_layout")
          .startTag("item")
          .attribute("name", "android:orientation")
          .characterData("vertical")
          .endTag("item")
        .endTag("style")
      .endTag("resources")
      .toString();
    // @formatter:on

    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myNewStylePath), xml);
    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myLayoutPath), buildLayout("@style/linear_layout"));

    myGuiTest.ideFrame().getEditor().open(myLayoutPath.toString());
    assertEquals("LinearLayout (vertical)", getComponentTree().valueAt(0));
  }

  @Test
  public void resolveAttributeNotInStyle() throws IOException {
    // @formatter:off
    String xml = new XmlBuilder()
      .startTag("resources")
        .startTag("style")
        .attribute("name", "linear_layout")
        .endTag("style")
      .endTag("resources")
      .toString();
    // @formatter:on

    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myNewStylePath), xml);
    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myLayoutPath), buildLayout("@style/linear_layout"));

    myGuiTest.ideFrame().getEditor().open(myLayoutPath.toString());
    assertEquals("LinearLayout (horizontal)", getComponentTree().valueAt(0));
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/70726902
  /**
   * Tries the case where style is referenced indirectly, e.g. through a reference in the theme.
   */
  @Test
  public void resolveAttributeStyleReference() throws IOException {
    // @formatter:off
    String xml = new XmlBuilder()
      .startTag("resources")
        // Style that defines the vertical orientation.
        .startTag("style")
          .attribute("name", "vertical_linear_layout")
            .startTag("item")
              .attribute("name", "android:orientation")
              .characterData("vertical")
            .endTag("item")
        .endTag("style")
        // Attr used to point to a style. AppTheme defines linear_layout_style to point to @style/vertical_linear_layout.
        .startTag("attr")
          .attribute("name", "linear_layout_style")
          .attribute("format", "reference")
        .endTag("attr")
      .endTag("resources")
      .toString();
    // @formatter:on

    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myNewStylePath), xml);
    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myLayoutPath), buildLayout("?linear_layout_style"));

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open(myMainStylePath);
    editor.moveBetween("AppTheme", "");
    editor.invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT);

    // Editor inserts the closing tag automatically.
    editor.enterText("<item name=\"linear_layout_style\">@style/vertical_linear_layout");

    editor.open(myLayoutPath.toString());
    assertEquals("LinearLayout (vertical)", getComponentTree().valueAt(0));
  }

  @NotNull
  private static String buildLayout(@NotNull String styleReference) {
    return new XmlBuilder()
      .startTag("LinearLayout")
      .attribute("xmlns", "android", "http://schemas.android.com/apk/res/android")
      .attribute("style", styleReference)
      .androidAttribute("layout_width", "match_parent")
      .androidAttribute("layout_height", "match_parent")
      .endTag("LinearLayout")
      .toString();
  }

  @NotNull
  private JTreeFixture getComponentTree() {
    NlEditorFixture editor = myGuiTest.ideFrame().getEditor().getLayoutEditor(false);
    editor.waitForRenderToFinish();

    JTreeFixture treeFixture = editor.getComponentTree();

    treeFixture.replaceCellReader((tree, value) -> {
      return StructureTreeDecorator.toString((NlComponent)value);
    });

    return treeFixture;
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE)
  public void changeOrientation() throws IOException {
    // @formatter:off
    String layout = new XmlBuilder()
      .startTag("LinearLayout")
        .attribute("xmlns", "android", "http://schemas.android.com/apk/res/android")
        .androidAttribute("layout_width", "match_parent")
        .androidAttribute("layout_height", "match_parent")
      .endTag("LinearLayout")
      .toString();
    // @formatter:on

    GuiTestFileUtils.writeAndReloadDocument(myProjectPath.resolve(myLayoutPath), layout);
    NlEditorFixture layoutEditor = myGuiTest.ideFrame().getEditor().open(myLayoutPath.toString()).getLayoutEditor(true);
    assertEquals("LinearLayout (horizontal)", getComponentTree().valueAt(0));
    layoutEditor.getComponentToolbar().getButtonByIcon(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT).click();
    assertEquals("LinearLayout (vertical)", getComponentTree().valueAt(0));
  }
}
