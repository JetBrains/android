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
package com.android.tools.idea.tests.gui.editors.hprof;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.HprofEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.JCheckBox;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class HprofAnalysisTest {

  private static final String HPROF_FILENAME = "memory-analysis-sample.hprof";
  private static final String HPROF_GZ_PATH = "captures/" + HPROF_FILENAME +".gz";
  private static final String HPROF_PATH = "captures/" + HPROF_FILENAME;

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void decompressHprofFile() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("HprofAnalysis");

    File projectRoot = ideFrame.getProjectPath();
    File compressedHprof = new File(projectRoot, HPROF_GZ_PATH);
    File hprof = new File(projectRoot, HPROF_PATH);

    try (
      InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(compressedHprof)));
      OutputStream out = new BufferedOutputStream(new FileOutputStream(hprof))
    ) {
      byte[] buffer = new byte[512];

      for (int numRead = in.read(buffer); numRead >= 0; numRead = in.read(buffer)) {
        out.write(buffer, 0, numRead);
      }
    }
  }

  /**
   * Verifies automatic .hprof file analysis
   *
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   *
   * <p>TT ID: a45d73f9-e599-4a96-a603-22e436b75a74
   *
   * <pre>
   *   Test Steps:
   *   1. Import project with hprof file.
   *   2. Open hprof file.
   *   3. Expand the analyzer tasks tool window. Select all analyzer tasks
   *   4. Click analyze
   *   Verify:
   *   1. Two nodes named "Leaked Activities" and "Duplicated Strings" are displayed.
   *   2. The leaked activities should include two LeakyThreadActivity and two
   *      LeakyActivity instances.
   *   3. The duplicate strings list should show strings and their duplication count.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void analyzeHprof() throws Exception {
    // Project has already been imported and opened in setup method.
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.getEditor().open(HPROF_PATH);

    HprofEditorFixture hprofEditor = HprofEditorFixture.findByFileName(ideFrame.robot(), ideFrame, HPROF_FILENAME);
    JPanelFixture analyzerWindow = hprofEditor.openAnalyzerTasks().getAnalyzerTasksWindow();

    analyzerWindow.checkBox(Matchers.byText(JCheckBox.class, "Detect Leaked Activities")).select();
    analyzerWindow.checkBox(Matchers.byText(JCheckBox.class, "Find Duplicate Strings")).select();
    hprofEditor.performAnalysis();

    JTreeFixture analysisTree = hprofEditor.getAnalysisResultsTree();
    int numRows = analysisTree.target().getRowCount();
    List<String> rowValues = new ArrayList<>(numRows);
    for (int row = 0; row < numRows; row++) {
      rowValues.add(analysisTree.valueAt(row));
    }

    assertThat(rowValues).containsAllOf("Duplicated Strings", "Leaked Activities");

    // Verify path exists - no exceptions thrown
    analysisTree.selectPath("Duplicated Strings/com.example.dunno.myapplication.LeakyThreadActivity");
    analysisTree.collapsePath("Duplicated Strings");

    analysisTree.expandPath("Leaked Activities");
    // Easiest way to check for activities is to scan over all rows
    numRows = analysisTree.target().getRowCount();
    int numLeakyActs = 0;
    for (int row = 0; row < numRows; row++) {
      String rowValue = analysisTree.valueAt(row);
      rowValue = rowValue != null ? rowValue : "";
      if (rowValue.contains("LeakyThreadActivity") || rowValue.contains("LeakyActivity")) {
        numLeakyActs++;
      }
    }

    assertThat(numLeakyActs).isAtLeast(4);
  }
}
