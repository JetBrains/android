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

import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.RenderErrorPanelFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GUI Tests for custom components.
 * <p/>
 * This tests layoutlib interaction with custom components and the error handling.
 */
@RunIn(TestGroup.LAYOUT)
@RunWith(GuiTestRunner.class)
public class CustomComponentsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testClassConverter() throws Exception {
    // Tests that the class converter rewrites the onDraw, onLayout and onMeasure methods to avoid errors from propagating
    // and breaking the rendering.

    guiTest.importProjectAndWaitForProjectSyncToFinish("CustomComponents");

    // Make sure the project is built: we need custom views to run the test
    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Load layout, wait for render to be shown in the preview window
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    editor.requireName("activity_my.xml");

    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForRenderToFinish();
    preview.requireRenderSuccessful();

    editor.moveBetween("failure=\"", "");

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
      editor.select(String.format("(%s)", Pattern.quote(failureMode)));
      editor.enterText("");
    }

    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.invokeAction(EditorFixture.EditorAction.SAVE);
    preview.waitForNextRenderToFinish();
    renderErrors.requireRenderSuccessful(false, false);
  }
}
