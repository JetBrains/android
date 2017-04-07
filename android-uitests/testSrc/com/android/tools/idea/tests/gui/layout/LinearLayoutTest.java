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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.XmlBuilder;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.structure.StructureTreeDecorator;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public final class LinearLayoutTest {
  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private Path myProjectPath;
  private Path myStylePath;
  private Path myLayoutPath;

  @Before
  public void setUp() {
    WizardUtils.createNewProject(myGuiTest, "Empty Activity");
    myProjectPath = myGuiTest.getProjectPath().toPath();

    FileSystem fileSystem = FileSystems.getDefault();

    myStylePath = fileSystem.getPath("app", "src", "main", "res", "values", "linear_layout.xml");
    myLayoutPath = fileSystem.getPath("app", "src", "main", "res", "layout", "linear_layout.xml");
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

    write(myProjectPath.resolve(myStylePath), xml);
    write(myProjectPath.resolve(myLayoutPath), buildLayout());

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

    write(myProjectPath.resolve(myStylePath), xml);
    write(myProjectPath.resolve(myLayoutPath), buildLayout());

    myGuiTest.ideFrame().getEditor().open(myLayoutPath.toString());
    assertEquals("LinearLayout (horizontal)", getComponentTree().valueAt(0));
  }

  private static void write(@NotNull Path path, @NotNull String string) throws IOException {
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Files.write(path, string.getBytes(StandardCharsets.UTF_8)).toFile());
  }

  @NotNull
  private static String buildLayout() {
    return new XmlBuilder()
      .startTag("LinearLayout")
      .attribute("xmlns", "android", "http://schemas.android.com/apk/res/android")
      .attribute("style", "@style/linear_layout")
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
      assert value != null;
      return StructureTreeDecorator.toString((NlComponent)value);
    });

    return treeFixture;
  }
}
