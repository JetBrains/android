/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class OpenCloseVisualizationToolTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  private IdeFrameFixture myIdeFrameFixture;
  private EditorFixture editor;

  private final String activity_myFile = "app/src/main/res/layout/activity_my.xml";

  private File projectDir;
  private String projectName = "SimpleApplication";

  @Before
  public void setUp() throws Exception {
    projectDir = guiTest.setUpProject(projectName, null, null, null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myIdeFrameFixture = guiTest.ideFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor = myIdeFrameFixture.getEditor();
    editor.open(activity_myFile, EditorFixture.Tab.DESIGN);
    editor.getLayoutEditor().waitForRenderToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.waitForFileToActivate(90);
    assertThat(editor.getVisualizationTool().waitForRenderToFinish().getCurrentFileName())
      .isEqualTo("activity_my.xml");
  }
  @After
  public void cleanUp() {
    myIdeFrameFixture.requestFocusIfLost();
    myIdeFrameFixture.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
  /**
   * Verifies that the Visualization Tool can be open and closed correctly.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   * <p>
   * TT ID: 1aa49fe7-0b4c-4eb7-84ca-a1e4c1ba18ff
   * TT ID: 511a9c5b-f62b-4076-9b45-68bf218bcbf3
   * <p>
   * <pre>
   *  Procedure:
   *  1. Launch Android Studio.
   *  2. Create a new project using the Basic Activity template.
   *  3. Under the layout resource directory, open the activity_main.xml file and wait for sync to finish.
   *  4. Open the activity_main.xml file from within the layout resource.
   *  5. Open the  Layout Validation by clicking the Layout validation on the right sidebar of Studio. (Verify 1)
   *  6. Resize the Visualization window by dragging the left edge. (Verify 2)
   *
   *  Verification:
   *  1. The  Layout Validation is open displaying the contents of activity main.
   *  2. The contents within the  Layout Validation will shrink and expand different number of columns.
   *   </pre>
   * </p>
   */
  @Test
  public void testDisplayContentsInVisualizationWindow() {
    // Zooming to fit the screen
    editor.getVisualizationTool().zoomToFit();
    editor.getVisualizationTool().waitForRenderToFinish();
    Integer numberOfRowsFitToScreen = editor.getVisualizationTool().getRowNumber();

    // Increasing the Zoom to 100%
    editor.getVisualizationTool().zoomToActual();
    editor.getVisualizationTool().waitForRenderToFinish();
    Integer numberOfRowsFor100 = editor.getVisualizationTool().getRowNumber();

    assertThat(numberOfRowsFor100).isGreaterThan(numberOfRowsFitToScreen);

    //Moving back to fit tot screen and expanding the window size.
    editor.getVisualizationTool().zoomToFit();
    editor.getVisualizationTool().waitForRenderToFinish();

    editor.getVisualizationTool().expandWindow();
    editor.getVisualizationTool().waitForRenderToFinish();
    int numberOfRowsForExpandedWindow = editor.getVisualizationTool().getRowNumber();

    assertThat(numberOfRowsForExpandedWindow <= numberOfRowsFitToScreen).isTrue();
  }

  /**
   * Verifies that the Visualization Tool can be open and closed correctly.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   * <p>
   * TT ID: 1aa49fe7-0b4c-4eb7-84ca-a1e4c1ba18ff
   * TT ID: 511a9c5b-f62b-4076-9b45-68bf218bcbf3
   * <p>
   * <pre>
   *  Procedure:
   *  1. Launch Android Studio.
   *  2. Create a new project using the Basic Activity template.
   *  3. Under the layout resource directory, open the activity_main.xml file and wait for sync to finish.
   *  4. Open the activity_main.xml file from within the layout resource.
   *  5. Open the Visualization window by clicking the Visualization tab on the right sidebar of Studio. (or from View -> Tool Window -> Layout Validation)
   *  6. Click on the "+" button from the zoom/pan controls located on the bottom-right side widget a few times.(Verify 1)
   *  7. Click on the "-" button from the zoom/pan controls located on the bottom-right side widget a few times(Verify 2)
   *  8. Click on the "1:1" button from the zoom/pan controls located on the bottom-right side widget.(Verify 3)
   *
   *  Verification:
   *  1. The preview size of the contents in the Visualization window becomes larger.
   *  2. The preview size of the contents in the Visualization window becomes smaller
   *  3. The preview size of the contents in the Visualization window zooms to 100%, which becomes very big.
   *   </pre>
   * </p>
   */
  @Test
  public void testZoomInZoomOut() {
    //Get original scale value
    double originalScale = editor.getVisualizationTool().getScale();

    // Zoom In
    editor.getVisualizationTool().zoomIn();
    editor.getVisualizationTool().zoomIn(); // Doing it twice to increase the size.
    double zoomInScale = editor.getVisualizationTool().getScale();

    // Zoom out
    editor.getVisualizationTool().zoomOut();
    editor.getVisualizationTool().zoomOut();
    double zoomOutScale = editor.getVisualizationTool().getScale();

    // Zoom to 100%
    editor.getVisualizationTool().zoomToActual();
    double zoomTo100Scale = editor.getVisualizationTool().getScale();

    // Zoom to Fit Screen
    editor.getVisualizationTool().zoomToFit();
    double zoomToFitScale = editor.getVisualizationTool().getScale();

    assertThat(zoomInScale).isGreaterThan(originalScale);
    assertThat(zoomOutScale).isLessThan(zoomInScale);
    assertThat(zoomTo100Scale).isGreaterThan(originalScale);
    assertThat(zoomTo100Scale).isEqualTo(1.0);
    assertThat(zoomToFitScale).isLessThan(zoomTo100Scale);

    String myActivityFile = "app/src/main/java/google/simpleapplication/MyActivity.java";
    editor.open(myActivityFile)
      .waitForVisualizationToolToHide();
  }
}