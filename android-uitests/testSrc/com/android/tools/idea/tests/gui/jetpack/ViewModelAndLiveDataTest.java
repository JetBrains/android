/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.jetpack;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.truth.Truth;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ViewModelAndLiveDataTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(20, TimeUnit.MINUTES);

  private static final String FILE_PATH = "app/src/main/java/com/example/android/unscramble/ui/game/";

  private static final String DATA_BINDING_FILE = "GameFragment.kt";

  private static final String LIVE_DATA_FILE = "GameViewModel.kt";


  /**
   * Verify symbol resolution of Methods on data binding classes
   * <p>
   * To verify that methods on the data binding classes are resolved
   * successfully even when there are no variables in the xml file
   * <p>
   * TT ID: e794da93-9693-4585-95d0-855628849770
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import the project from drive
   *      Sample project is created with all required classes in test data.
   *      Import the sample from test data.
   *   2. Open the MainActivity.java or MainViewModel.java and check if all the symbols are resolved.
   *      The file names in the sample are different. Test will be opening GameFragment.kt
   *      and GameViewModel.kt to verify the symbols
   *
   *   Verify:
   *   1. All the symbols should get resolved
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void viewModelAndLiveDataSymbolResolutionTest() throws Exception{
    IdeFrameFixture ideFrame = guiTest
      .importProjectAndWaitForProjectSyncToFinish("ViewModelLiveDataSample",
                                                  Wait.seconds(1000));
    Truth.assertThat(ideFrame.invokeProjectMake().isBuildSuccessful()).isTrue();

    EditorFixture editorFixture = ideFrame.getEditor().open(FILE_PATH + DATA_BINDING_FILE);
    Wait.seconds(10).expecting(DATA_BINDING_FILE + " file is opened.")
      .until(() -> DATA_BINDING_FILE.equals(ideFrame.getEditor().getCurrentFileName()));

    // Verify symbols for data binding and view model classes  are resolved.
    editorFixture.waitUntilErrorAnalysisFinishes();
    editorFixture.waitUntilErrorAnalysisFinishes(); //Run the analysis twice to reduce the flakiness

    assertThat(editorFixture.getHighlights(HighlightSeverity.WARNING).size())
      .isEqualTo(0);
    assertThat(editorFixture.getHighlights(HighlightSeverity.ERROR).size())
      .isEqualTo(0);

    // Verify symbols for Live Data classes are resolved.
    editorFixture = ideFrame.getEditor().open(FILE_PATH + LIVE_DATA_FILE);
    Wait.seconds(10).expecting(LIVE_DATA_FILE + " file is opened.")
      .until(() -> LIVE_DATA_FILE.equals(ideFrame.getEditor().getCurrentFileName()));

    editorFixture.waitUntilErrorAnalysisFinishes();
    editorFixture.waitUntilErrorAnalysisFinishes(); //Run the analysis twice to reduce the flakiness

    assertThat(editorFixture.getHighlights(HighlightSeverity.WARNING).size())
      .isEqualTo(0);
    assertThat(editorFixture.getHighlights(HighlightSeverity.ERROR).size())
      .isEqualTo(0);
  }
}
