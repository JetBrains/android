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
package com.android.tools.idea.tests.gui.uibuilder;

import static com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ResourcePickerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlPaletteFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixtureKt;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddAllLayoutItemsFromPaletteTest {
  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(20, TimeUnit.MINUTES);

  private final String projectName = "SimpleApplication";
  private final String agpVersion = ANDROID_GRADLE_PLUGIN_VERSION;

  private IdeFrameFixture ideFrame;
  private EditorFixture editor;
  private NlEditorFixture nlEditor;
  private NlPaletteFixture nlPalette;
  private Integer dragCount;

  @Before
  public void setup() throws IOException {
    File projectDir = guiTest.setUpProject(projectName, null, agpVersion, null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame = guiTest.ideFrame();
    editor = ideFrame.getEditor();
    ideFrame.clearNotificationsPresentOnIdeFrame();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    cleanLayoutFile();
    nlEditor = editor.getLayoutEditor();
    assertThat(nlEditor.canInteractWithSurface())
      .isTrue();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    nlPalette = nlEditor.getPalette();
    dragCount = nlEditor.getAllComponents()
      .size();
  }

  //The test verifies the AGP upgrade from one stable version to the latest public stable version.
  /**
   * 1.32.3 - Adding all layout items from palette
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 7fd4834b-ef5a-4414-b601-3c8bd8ab54d0
   * <pre>
   *   Test Steps:
   *   1. Create a new project
   *   2. Open Layout Editor for the default activity.
   *   3. Switch to Design/Split View of layout using icons at top right corner of editor
   *   4. Add each item from the palette to the Layout
   *   5. Switch to Text View.(Verify)
   *   Verification:
   *   1. Verify the item displays in the preview pane and also in the xml view
   * </pre>
   */

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testAddItemsFromTextGroup() {
    String groupName = "Text";
    String[] itemsInTextGroup = {"TextView", "Plain Text", "Password", "Password (Numeric)", "E-mail", "Phone", "Postal Address", "Multiline Text", "Time", "Date", "Number", "Number (Signed)", "Number (Decimal)"};
    String[] itemsInTextGroup2 = {"TextInputLayout", "AutoCompleteTextView", "MultiAutoCompleteTextView", "CheckedTextView"};
    // Adding all items from Text palette.
    for (String item : itemsInTextGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size())
        .isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());

    //Removing the added items list
    cleanLayoutFile();

    // Verifying Set 2 in Group Text
    dragCount = nlEditor.getAllComponents().size();
    for (String item : itemsInTextGroup2) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size())
        .isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Finding all the added item id's or tag's
    verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testAddItemsFromButtonGroup() {
    String groupName = "Buttons";
    String[] itemsInButtonsGroup = {"Button", "ChipGroup", "Chip", "CheckBox", "RadioGroup", "RadioButton", "ToggleButton", "Switch"};

    // Adding all items from Buttons palette.
    for (String item : itemsInButtonsGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size()).isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Additional workflow for selected items
    nlEditor.dragComponentToSurface(groupName, "ImageButton");
    selectResourcePicker();
    assertThat(nlEditor.getAllComponents().size())
      .isGreaterThan(dragCount);
    System.out.printf("Added %s from group %s\n", "ImageButton", groupName);
    dragCount = dragCount + 1;

    nlEditor.dragComponentToSurface(groupName, "FloatingActionButton");
    selectResourcePicker();
    assertThat(nlEditor.getAllComponents().size())
      .isGreaterThan(dragCount);
    System.out.printf("Added %s from group %s\n", "ImageButton", groupName);
    dragCount = dragCount + 1;

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testAddItemsFromWidgetsGroup() {
    String groupName = "Widgets";
    String[] itemsInWidgetGroup = {"View", "VideoView", "CalendarView", "Text Clock", "ProgressBar", "ProgressBar (Horizontal)", "SeekBar", "SeekBar (Discrete)", "RatingBar", "TextureView", "SurfaceView", "Horizontal Divider", "Vertical Divider", "SearchView", "WebView"};

    // Adding all items from Buttons palette.
    for (String item : itemsInWidgetGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size())
        .isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());

    //Removing the added items from layout file.
    cleanLayoutFile();

    // Additional workflow for selected items
    nlEditor.dragComponentToSurface(groupName, "ImageView");
    selectResourcePicker();
    System.out.printf("Added %s from group %s\n", "ImageView", groupName);
    // Finding all the added item id's or tag's
    verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testAddItemsFromLayoutsGroup() {
    String groupName = "Layouts";
    String[] itemsInLayoutGroup = {"ConstraintLayout", "TableLayout", "TableRow"};

    // Adding all items from Layout palette.
    for (String item : itemsInLayoutGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size())
        .isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }

  @Ignore("b/303106910")
  public void testAddItemsFromContainersGroup() {
    // Need to implement Containers.
    /* TODO() */
  }

  //Ignoring as drag and drop is not working, need further investigation
  @Ignore("b/303106910")
  public void testAddItemsFromHelpersGroup() {
    String groupName = "Helpers";
    String[] itemsInHelpersGroup = {"Group", "Barrier (Horizontal)", "Barrier (Vertical)", "Flow", "Guideline (Horizontal)", "Guideline (Vertical)", "Layer", "MockView"};

    // Adding all items from Buttons palette.
    for (String item : itemsInHelpersGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size())
        .isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }

    // Additional workflow for selected items
    nlEditor.dragComponentToSurface(groupName, "ImageFilterView");
    selectResourcePicker();
    assertThat(nlEditor.getAllComponents().size()).isGreaterThan(dragCount);
    System.out.printf("Added %s from group %s\n", "ImageFilterView", groupName);
    dragCount = dragCount + 1;

    nlEditor.dragComponentToSurface(groupName, "ImageFilterButton");
    selectResourcePicker();
    assertThat(nlEditor.getAllComponents().size()).isGreaterThan(dragCount);
    System.out.printf("Added %s from group %s\n", "ImageFilterButton", groupName);
    dragCount = dragCount + 1;

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testAddItemsFromGoogleGroup() {
    String groupName = "Google";
    String[] itemsInGoogleGroup = {"AdView", "MapView"};

    // Adding all items from Buttons palette.
    for (String item : itemsInGoogleGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size()).isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }


  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testAddItemsFromLegacyGroup() {
    String groupName = "Legacy";
    String[] itemsInLegacyGroup = {"RelativeLayout", "TabHost"};
    // String[] itemsInLegacyGroup2 = {"GridView", "ListView"}; // Ignoring until b/280503416 is resolved.

    // Adding all items from Legacy palette.
    for (String item : itemsInLegacyGroup) {
      dragComponent(groupName, item);
      assertThat(nlEditor.getAllComponents().size())
        .isGreaterThan(dragCount);
      System.out.printf("Added %s from group %s\n", item, groupName);
      dragCount = dragCount + 1;
    }

    // Finding all the added item id's or tag's
    List<String> verificationIdOrTagInfoList = getVerificationIdOrTags();

    // Verifying if the items are added to code.
    editor.switchToTab("Text");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    System.out.println(verificationIdOrTagInfoList);
    assertItemsAddedToCode(verificationIdOrTagInfoList, editor.getCurrentFileContents());
  }

  private void assertItemsAddedToCode(List<String> codeSnipetsList, String fileContents) {
    for (String codeSnippet : codeSnipetsList) {
      assertThat(fileContents.contains(codeSnippet)).isTrue();
    }
  }

  private List<String> getVerificationIdOrTags() {
    List<String> verificationIdOrTagInfoList = new ArrayList<>();
    List<NlComponentFixture> allComponentsList = nlEditor.getAllComponents();
    for (NlComponentFixture itemComponent : allComponentsList) {
      verificationIdOrTagInfoList.add(itemComponent.getComponent().getTagName());
      if (itemComponent.getComponent().getId() != null) {
        verificationIdOrTagInfoList.add(itemComponent.getComponent().getId());
      }
    }
    return verificationIdOrTagInfoList;
  }

  private void dragComponent(String groupName, String itemName) {
    Dimension screenViewSize = nlEditor.getSurface()
      .target()
      .getFocusedSceneView()
      .getScaledContentSize();
    int widthOffset = screenViewSize.width / 2;
    int heightOffset = screenViewSize.height / 2;
    nlEditor.dragComponentToSurface(groupName, itemName, widthOffset, heightOffset)
      .waitForRenderToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  private void selectResourcePicker() {
    // Resource picker
    ResourcePickerDialogFixture dialog = ResourcePickerDialogFixture.find(guiTest.robot());
    assertThat(dialog.getTitle()).isEqualTo("Pick a Resource");
    dialog.getResourceExplorer().selectResource("ic_launcher");
    dialog.clickOk();
    nlEditor.waitForSurfaceToLoad();
  }

  private void cleanLayoutFile(){
    // To clean or remove the added items from palette.
    SplitEditorFixture splitEditorFixture = SplitEditorFixtureKt.getSplitEditorFixture(editor);
    splitEditorFixture.setCodeMode();
    editor.replaceText("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                       "    android:layout_width=\"match_parent\"\n" +
                       "    android:layout_height=\"match_parent\"\n" +
                       "    android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n" +
                       "    android:paddingRight=\"@dimen/activity_horizontal_margin\"\n" +
                       "    android:paddingTop=\"@dimen/activity_vertical_margin\"\n" +
                       "    android:paddingBottom=\"@dimen/activity_vertical_margin\"\n" +
                       "    tools:context=\".MyActivity\">\n" +
                       "\n" +
                       "</RelativeLayout>");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.switchToTab("Design");
    editor.getLayoutEditor()
      .waitForSurfaceToLoad();
    ideFrame.robot()
      .waitForIdle();
  }
}
