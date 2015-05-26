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
package com.android.tools.idea.tests.gui.layout;

import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.ConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutWidgetFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.RenderErrorPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.TagMatcher.AttributeMatcher;
import com.android.tools.lint.detector.api.LintUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.tests.gui.framework.TestGroup.LAYOUT;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static junit.framework.Assert.*;
import static org.junit.Assert.assertTrue;

/**
 * GUI Tests for custom components.
 * <p/>
 * This tests layoutlib interaction with custom components and the error hadling.
 */
@BelongsToTestGroups({LAYOUT})
public class CustomComponentsTest extends GuiTestCase {

  @Test
  @IdeGuiTest
  public void testClassConverter() throws Exception {
    // Tests that the class converter rewrites the onDraw, onLayout and onMeasure methods to avoid errors from propagating
    // and breaking the rendering.

    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("CustomComponents");

    // Make sure the project is built: we need custom views to run the test
    GradleInvocationResult result = projectFrame.invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Load layout, wait for render to be shown in the preview window
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    editor.requireName("activity_my.xml");
    preview.waitForRenderToFinish();
    preview.requireRenderSuccessful();

    editor.moveTo(editor.findOffset("failure=\"", null, true));

    RenderErrorPanelFixture renderErrors = preview.getRenderErrors();
    for (String failureMode : new String[]{"onDraw", "onLayout", "onMeasure"}) {
      // Set the failure mode, check that it generates an exception and the exception is correctly logged. Then select the failure mode
      // string so it can be replaced or removed in the next iteration.
      editor.enterText(failureMode);
      editor.invokeAction(EditorFixture.EditorAction.SAVE);

      preview.waitForNextRenderToFinish();

      // Make sure the error is correctly logged.
      renderErrors.requireHaveRenderError(failureMode + " error");
      renderErrors.requireHaveRenderError("NullPointerException");

      // Remove the existing mode
      int offset = editor.findOffset(failureMode, null, true);
      editor.select(offset, offset - failureMode.length());
    }

    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.invokeAction(EditorFixture.EditorAction.SAVE);
    preview.waitForNextRenderToFinish();
    renderErrors.requireRenderSuccessful(false, false);
  }
}
