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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class DataBindingTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verify Databinding class's methods are resolved even when XML file contains
   * no databinding variables.
   *
   * <p>TT ID: e794da93-9693-4585-95d0-855628849770
   *
   * <pre>
   *   Test steps:
   *   1. Import DatabindingMethodsTest
   *   2. Open BlankFragment.java
   *   Verify:
   *   1. Ensure BlankFragment.java has no highlighted errors.
   * </pre>
   */
  @Test
  public void resolvesSymbols() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("DatabindingMethodsTest");
    List<String> errors = ideFrame.getEditor()
      .open("app/src/main/java/com/google/test/databinding/BlankFragment.java")
      .getHighlights(HighlightSeverity.ERROR);
    assertThat(errors).isEmpty();
  }

  /**
   * Verify data binding expressions in the XML are resolved properly.
   */
  @Test
  public void resolveXmlReferences() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("DatabindingMethodsTest");
    List<String> errors = ideFrame.getEditor()
      .open("app/src/main/res/layout/binding_layout.xml", EditorFixture.Tab.EDITOR)
      .getHighlights(HighlightSeverity.ERROR);
    assertThat(errors).isEmpty();
  }
}
