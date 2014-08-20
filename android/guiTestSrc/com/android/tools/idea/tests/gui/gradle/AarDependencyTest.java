/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Test;

import java.io.IOException;

import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

/**
 * Tests fix for issue <a href="https://code.google.com/p/android/issues/detail?id=74341">74341</a>.
 */
public class AarDependencyTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testEditorFindsAppCompatStyle() throws IOException {
    IdeFrameFixture ideFrame = importProject("AarDependency");

    String stringsXmlPath = "app/src/main/res/values/strings.xml";
    ideFrame.getEditor().open(stringsXmlPath, EditorFixture.Tab.EDITOR);

    FileFixture file = ideFrame.findExistingFileByRelativePath(stringsXmlPath);
    file.requireCodeAnalysisHighlightCount(ERROR, 0);
  }
}
