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

import com.android.resources.ResourceUrl;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.uibuilder.api.StructurePaneComponentHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.xml.XmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import icons.StudioIcons;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRemoteRunner.class)
public final class LinearLayoutTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();
  @Rule
  public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  private Path myMainStylePath;
  private Path myNewStylePath;
  private Path myNewLayoutPath;

  @Before
  public void setUp() throws IOException {
    myGuiTest.importSimpleApplication();
    FileSystem fileSystem = FileSystems.getDefault();

    myNewStylePath = fileSystem.getPath("app", "src", "main", "res", "values", "linear_layout.xml");
    myNewLayoutPath = fileSystem.getPath("app", "src", "main", "res", "layout", "linear_layout.xml");
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

    myGuiTest.ideFrame().getEditor()
      .newFile(myNewStylePath, xml)
      .newFile(myNewLayoutPath, buildLayout("@style/linear_layout"))
      .selectEditorTab(EditorFixture.Tab.DESIGN);
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

    myGuiTest.ideFrame().getEditor()
      .newFile(myNewStylePath, xml)
      .newFile(myNewLayoutPath, buildLayout("@style/linear_layout"))
      .selectEditorTab(EditorFixture.Tab.DESIGN);
    assertEquals("LinearLayout (horizontal)", getComponentTree().valueAt(0));
  }

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

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.newFile(myNewStylePath, xml);
    editor.newFile(myNewLayoutPath,  buildLayout("?linear_layout_style"));

    editor.open(myMainStylePath);
    editor.moveBetween("AppTheme", "");
    editor.invokeAction(EditorFixture.EditorAction.COMPLETE_CURRENT_STATEMENT);

    editor.pasteText("<item name=\"linear_layout_style\">@style/vertical_linear_layout</item>");

    editor.open(myNewLayoutPath.toString());
    Wait.seconds(10)
      .expecting("Component tree pane to update")
      .until(() -> getComponentTree().valueAt(0).equals("LinearLayout (vertical)"));
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
    NlEditorFixture editor = myGuiTest.ideFrame().getEditor().getLayoutEditor();
    editor.waitForRenderToFinish();

    JTreeFixture treeFixture = editor.getComponentTree();

    treeFixture.replaceCellReader((tree, value) -> TreeSearchUtil.toString((NlComponent)value));

    return treeFixture;
  }

  @Test
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

    NlEditorFixture layoutEditor = myGuiTest.ideFrame().getEditor()
      .newFile(myNewLayoutPath, layout)
      .getLayoutEditor();
    layoutEditor.getAllComponents().get(0).getSceneComponent().click(); // Make sure the Linear layout has focus
    assertEquals("LinearLayout (horizontal)", getComponentTree().valueAt(0));
    layoutEditor.getComponentToolbar().getButtonByIcon(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT).click();
    assertEquals("LinearLayout (vertical)", getComponentTree().valueAt(0));
  }

  public final class TreeSearchUtil {

    private TreeSearchUtil() {
    }

    /**
     * Provide a string representation of the given component to search
     * help searching by different useful features that can identify a component
     * (e.g text of a text view)
     */
    @NotNull
    public static String toString(@NotNull NlComponent component) {
      StringBuilder container = new StringBuilder();
      String id = ResourceUrl.parse(component.getId()).getQualifiedName();
      if (!id.isEmpty()) {
        container.append(id);
      }

      StructurePaneComponentHandler handler = getViewHandler(component);
      String title = handler.getTitle(component);

      if (!StringUtil.startsWithIgnoreCase(id, title)) {
        container.append(id.isEmpty() ? title : " (" + title + ')');
      }

      String attributes = handler.getTitleAttributes(component);

      if (!attributes.isEmpty()) {
        container.append(' ').append(attributes);
      }
      return container.toString();
    }

    @NotNull
    private static StructurePaneComponentHandler getViewHandler(@NotNull NlComponent component) {
      StructurePaneComponentHandler handler = NlComponentHelperKt.getViewHandler(component, () -> {});
      return handler == null ? ViewHandlerManager.NONE : handler;
    }
  }
}
