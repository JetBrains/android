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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.tabs.impl.TabLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.GOTO_DECLARATION;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

@BelongsToTestGroups({PROJECT_SUPPORT})
@RunWith(GuiTestRunner.class)
public class GradleTestArtifactSyncTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final char VIRTUAL_FILE_PATH_SEPARATOR = '/';

  private boolean myOriginalLoadAllTestArtifactsValue;

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Before
  public void enableTestArtifacts() throws Exception {
    myOriginalLoadAllTestArtifactsValue = GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS;
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = true;
  }

  @After
  public void recoverTestArtifactsSetting() {
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = myOriginalLoadAllTestArtifactsValue;
  }

  @Test
  public void testLoadAllTestArtifacts() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LoadMultiTestArtifacts");
    EditorFixture editor = guiTest.ideFrame().getEditor();

    Module appModule = guiTest.ideFrame().getModule("app");
    List<String> sourceRootNames = Lists.newArrayList();
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(appModule).getSourceRoots();
    for (VirtualFile sourceRoot : sourceRoots) {
      // Get the last 2 segments of the path for each source folder (e.g. 'testFree/java')
      String path = sourceRoot.getPath();
      List<String> pathSegments = Splitter.on(VIRTUAL_FILE_PATH_SEPARATOR).omitEmptyStrings().splitToList(path);
      int segmentCount = pathSegments.size();
      assertThat(segmentCount).as("number of segments in path '" + path + "'").isGreaterThan(2);
      String name = Joiner.on(VIRTUAL_FILE_PATH_SEPARATOR).join(pathSegments.get(segmentCount - 2), pathSegments.get(segmentCount - 1));
      sourceRootNames.add(name);
    }
    assertThat(sourceRootNames).containsOnly("testFree/java", "androidTestFree/java", "testDebug/java", "main/res", "main/java",
                                             "test/java", "androidTest/java");

    // Refer to the test source file for the reason of unresolved references
    editor.open("app/src/androidTest/java/com/example/ApplicationTest.java")
          .requireHighlights(ERROR, "Cannot resolve symbol 'Assert'", "Cannot resolve symbol 'ExampleUnitTest'",
                             "Cannot resolve symbol 'Lib'")
          .moveTo(editor.findOffset("Test^Util util"))
          .invokeAction(GOTO_DECLARATION);
    requirePath(editor.getCurrentFile(), "androidTest/java/com/example/TestUtil.java");

    editor.open("app/src/test/java/com/example/UnitTest.java")
          .requireHighlights(ERROR, "Cannot resolve symbol 'Collections2'", "Cannot resolve symbol 'ApplicationTest'")
          .moveTo(editor.findOffset("Test^Util util"))
          .invokeAction(GOTO_DECLARATION);
    requirePath(editor.getCurrentFile(), "test/java/com/example/TestUtil.java");
  }

  private static void requirePath(@Nullable VirtualFile file, @NotNull String path) {
    assertNotNull(file);
    assertThat(file.getPath()).endsWith(path);
  }

  @Test
  public void testTestFileBackground() throws Exception {
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();

    editor.open("app/src/test/java/google/simpleapplication/UnitTest.java");
    TabLabel tabLabel = findTab(editor);
    Color green = new JBColor(new Color(231, 250, 219), new Color(0x425444));
    assertEquals(green, tabLabel.getInfo().getTabColor());

    // TODO re-enable following code when we fix background color support
    //editor.close();
    //editor.open("app/src/androidTest/java/google/simpleapplication/ApplicationTest.java");
    //tabLabel = findTab(editor);
    //Color blue = new JBColor(new Color(0xdcf0ff), new Color(0x3C476B));
    //assertEquals(blue, tabLabel.getInfo().getTabColor());
  }

  @NotNull
  private TabLabel findTab(@NotNull EditorFixture editor) {
    final VirtualFile file = editor.getCurrentFile();
    assert file != null;
    return guiTest.robot().finder().find(new GenericTypeMatcher<TabLabel>(TabLabel.class) {
      @Override
      protected boolean isMatching(@Nonnull TabLabel tabLabel) {
        return tabLabel.getInfo().getText().equals(file.getName());
      }
    });
  }
}
