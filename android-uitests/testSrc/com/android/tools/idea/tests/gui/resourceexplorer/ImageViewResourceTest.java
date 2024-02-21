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
package com.android.tools.idea.tests.gui.resourceexplorer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ImageViewResourceTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private IdeFrameFixture myIdeFrameFixture;
  private EditorFixture myEditorFixture;
  private NlEditorFixture myNlEditorFixture;
  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String APP_NAME = "TestApp";
  protected static final String PACKAGE_NAME = "com.google.testapp";
  protected static final int MIN_SDK_API = SdkVersionInfo.HIGHEST_SUPPORTED_API;
  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Kotlin);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    myIdeFrameFixture =  guiTest.ideFrame();
    myEditorFixture = myIdeFrameFixture.getEditor();
  }

  /**
   * To verify drag and drop of image resource in layout and XML editor
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1e665fdf-16fa-49c9-9e42-2be600860b46
   * TT ID: efc3b07f-9850-419b-9c2a-7a902ea65aee
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Launch Android Studio.
   *   2. Open an existing project, DrawableResource
   *   3. Open the "activity_main.xml" file, with the "Design" tab selected (if not already open).
   *   4. Open the Resource Manager (View > Tool Windows > Resource Manager).
   *   5. Click on the "Drawable" tab
   *   6. Drag "ic_launcher_background" from the "Resources" tool window onto the Layout Editor. (Verify 1)
   *   7. On the layout editor, switch to the ""Text"" tab to display the XML editor.
   *   8. On the ""Resource"" window, right click on ic_launcher_foreground and select Copy.
   *   9. On the XML editor, move the cursor and right click on the ImageView and click Paste. (Verify 2)
   *   10. Drag any drawable (event_header_codelabs) from the  and drop it in an empty space within a &lt;FrameLayout&gt; view in the XML file. (Verify 3) (Verify 4)
   *   Verify:
   *   1. A new image view displaying ""ic_launcher_background"" should be created.
   *   2. The app:srcCompat attribute should have been changed to ""@drawable/ic_launcher_foreground"
   *   3. In the Text view, an <ImageView> should be added to the XML layout.
   *   4. In the Design view, the drawable image should be added
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testDrawableResource() {

    myEditorFixture.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.DESIGN, Wait.seconds(30));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture = myEditorFixture.getLayoutEditor().waitForSurfaceToLoad().waitForRenderToFinish(Wait.seconds(30));
    //Open Resource Manager
    myIdeFrameFixture.invokeMenuPath("Tools", "Resource Manager");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ResourceExplorerFixture myResourceExplorerFixture = ResourceExplorerFixture.find(guiTest.robot());

    //Go to Drawable tab in the Resource Manager
    int selectedTabIndex = myResourceExplorerFixture.tabbedPane().target().getSelectedIndex();
    assertThat(myResourceExplorerFixture.tabbedPane().target().getTitleAt(selectedTabIndex)).matches("Drawable");
    myResourceExplorerFixture.click();
    myIdeFrameFixture.waitUntilProgressBarNotDisplayed();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Verify 1 : A new image view displaying ""ic_launcher_background"" should be created.
    myResourceExplorerFixture.focus();
    myResourceExplorerFixture.dragResourceToLayoutEditor("ic_launcher_background");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myEditorFixture.selectEditorTab(EditorFixture.Tab.EDITOR);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(myEditorFixture.getCurrentFileContents()
                 .contains("app:srcCompat=\"@drawable/ic_launcher_background\""))
      .isTrue();

    // Verify 2: After pasting new image, the app:srcCompat attribute should have been changed to ""@drawable/ic_launcher_foreground"
    myResourceExplorerFixture.selectResource("ic_launcher_foreground");
    myIdeFrameFixture.copyUsingKeyboard();
    myIdeFrameFixture.requestFocusIfLost();
    myEditorFixture.select("(<Image)");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myIdeFrameFixture.pasteUsingKeyboard();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    String activityFileContent = myEditorFixture.getCurrentFileContents();
    assertThat(activityFileContent
                 .contains("app:srcCompat=\"@drawable/ic_launcher_foreground\"")).isTrue();
    assertFalse(activityFileContent
                 .contains("app:srcCompat=\"@drawable/ic_launcher_background\""));

    //Verify 3: Drag any drawable image and drop it in an empty space in the XML file creates code for that component
    myResourceExplorerFixture.dragResourceToXmlEditor("ic_launcher_background", ".MainActivity\">", "");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(myEditorFixture.getCurrentFileContents()
                 .contains("app:srcCompat=\"@drawable/ic_launcher_background\"")).isTrue();

    //Verify 4: In Design view, the drawable image should be added
    myEditorFixture.selectEditorTab(EditorFixture.Tab.SPLIT);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // The components list is expected to have 3 elements in the following order:
    // * A RelativeLayout containing all the other components
    // * An ImageView dragged to Editor (Verify 3)
    // * A TextView, which was already present in activity_main.xml
    // * An ImageView, dragged to Layout (design mode) (Verify 1)
    assertThat(myNlEditorFixture.getAllComponents()).hasSize(4);
  }
}