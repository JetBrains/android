/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static junit.framework.Assert.assertTrue;

import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.properties.SectionFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.data.TableCell;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddOrRemoveAttributesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 30;
  private IdeFrameFixture myIdeFrameFixture;
  private EditorFixture myEditorFixture;
  NlEditorFixture myNlEditorFixture;
  private DesignSurface<?> myDesignSurface;
  SectionFixture declaredAttributes;
  PTableFixture myPTable;

  @Before
  public void setUp() {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Java);
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    myIdeFrameFixture = guiTest.ideFrame();
    myIdeFrameFixture.clearNotificationsPresentOnIdeFrame();

    myIdeFrameFixture.getProjectView().assertFilesExist(
      "app/src/main/res/layout/activity_main.xml"
    );

    myEditorFixture =myIdeFrameFixture.getEditor();

    myEditorFixture.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.DESIGN);

    myNlEditorFixture = myEditorFixture
      .getLayoutEditor()
      .waitForSurfaceToLoad();
    assertThat(myNlEditorFixture.canInteractWithSurface()).isTrue();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    myDesignSurface = myNlEditorFixture.getSurface().target();

    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * Verifies attributes can be added from UI attributes panel
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 8e4fb543-6518-479b-aa31-6801c3c2bd62
   * <pre>
   *   Test Steps:
   *   1) Create a Project with Empty Activity
   *   2) Switch to Design/Split View of layout using icons at top right corner of editor
   *   3) Drag and drop few components (TextView, Button, Checkbox) and make sure they have id's say (@+id/textview, @+id/button, @+id/checkbox)
   *   4) Open Attributes panel &gt; Expand "Declared Attributes section"
   *   5) Select TextView Click on '+' icon to add new attributes
   *   6) Type constraint..(Verify 1)
   *   7) Select "constraintTop_toTopOf" choose parent in value dropdown (verify 2)
   *   8) Select constraintLeft, constraintRight, constraintBottom in the same way to parent (verify 3)
   *   9) Select Button Click on '+' icon to add new attributes
   *   10) Type base..(verify 4)
   *   11) Select "constraintBaseline_toBaselineOf" and choose "@+id/textview" from dropdown (Verify 5)
   *   Verification:
   *   1) Autocomplete should show up for all attributes that includes "constraint" and there should be no freeze or time lag for suggestions to appear
   *   2) Top constraint for TextView should be created to the top and new attributes added to xml
   * 	app:layout_constraintBottom_toBottomOf="parent"
   * 	app:layout_constraintLeft_toLeftOf="parent"
   *         app:layout_constraintRight_toRightOf="parent"
   *         app:layout_constraintTop_toTopOf="parent"
   *   3) All constraints should be created to parent and TextView component should align in center of the view
   *   4) Autocomplete should show up for all attributes that includes "base" and there should be no freeze or time lag for suggestions to appear
   *   5) A baseline constraint should be created from Button to TextView and new attribute added to xml
   * 	app:layout_constraintBaseline_toBaselineOf="@id/textview"
   * </pre>
   */

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testAddAttributesToDeclaredAttributesPanel() {
    //Drag a Button from command pallet to design surface.
    Dimension screenViewSize = myNlEditorFixture.getSurface()
      .target()
      .getFocusedSceneView()
      .getScaledContentSize();
    int widthOffset = screenViewSize.width / 4;
    int heightOffset = screenViewSize.height / 4;

    myNlEditorFixture.dragComponentToSurface("Buttons", "CheckBox", screenViewSize.width / 2 - widthOffset, screenViewSize.height / 2 - heightOffset)
      .waitForRenderToFinish();
    myNlEditorFixture.waitForSurfaceToLoad();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Click on CheckBox
    myNlEditorFixture.findView("CheckBox", 0).getSceneComponent().click();
    myNlEditorFixture.getAttributesPanel()
      .waitForId("checkBox");

    //Finding the initial location of the dragged checkBox location
    Point checkBoxInitialLocation = myNlEditorFixture.findView("CheckBox", 0).getSceneComponent().getMidPoint();

    SectionFixture declaredAttributesPanel =  myNlEditorFixture
      .getAttributesPanel()
      .findSectionByName("Declared Attributes");

    //Expand declared attributes section.
    declaredAttributesPanel.expand();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Adding attributes in declared attributes panel.
    addLayoutConstraintInDeclaredAttributes("checkBox","app:layout_constraintRight_toRightOf", "parent");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    addLayoutConstraintInDeclaredAttributes("checkBox","app:layout_constraintTop_toTopOf", "parent");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    addLayoutConstraintInDeclaredAttributes("checkBox","app:layout_constraintBottom_toBottomOf", "parent");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    //Finding out the final location of check box after adding attributes.
    Point checkBoxFinalLocation = myNlEditorFixture.findView("CheckBox", 0).getSceneComponent().getMidPoint();

    //Validating that the checkbox location is changed after adding layoutConstraints.
    assertThat(checkBoxFinalLocation.x).isNotEqualTo(checkBoxInitialLocation.x);
    assertThat(checkBoxFinalLocation.y).isNotEqualTo(checkBoxInitialLocation.y);

    //Dragging a new button
    myNlEditorFixture.dragComponentToSurface("Buttons", "Button", 0, screenViewSize.height / 2 - heightOffset)
      .waitForRenderToFinish();
    myNlEditorFixture.waitForSurfaceToLoad();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Getting initial location of the button.
    Point buttonInitialLocation = myNlEditorFixture.findView("Button", 0).getSceneComponent().getMidPoint();

    myNlEditorFixture.findView("Button", 0).getSceneComponent().click();
    myNlEditorFixture.getAttributesPanel()
      .waitForId("button");

    //Adding baseline constraint attribute in the declared attributes panel.
    addLayoutConstraintInDeclaredAttributes("button","app:layout_constraintBaseline_toBaselineOf", "@id/checkBox");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    //Final location of the button after adding baseline attribute.
    Point buttonFinalLocation = myNlEditorFixture.findView("Button", 0).getSceneComponent().getMidPoint();

    //Validating that the button location is changed after adding baseline constraint with respect to checkbox.
    assertThat(buttonFinalLocation.x).isNotEqualTo(buttonInitialLocation.x);
    assertThat(buttonFinalLocation.y).isNotEqualTo(buttonInitialLocation.y);

    //Validating that the added constraints can be observed in the declared attributes and are stored.

    assertThat(myPTable.findRowOf("layout_constraintBaseline_toBaselineOf")).isGreaterThan(0);

    //Validating added attributes with respect to checkbox
    myNlEditorFixture.findView("CheckBox", 0).getSceneComponent().click();
    myNlEditorFixture.getAttributesPanel()
        .waitForId("checkBox");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    refreshDeclaredAttributesTable("checkBox");

    assertThat(myPTable.findRowOf("layout_constraintRight_toRightOf")).isGreaterThan(0);
    assertThat(myPTable.findRowOf("layout_constraintTop_toTopOf")).isGreaterThan(0);
    assertThat(myPTable.findRowOf("layout_constraintBottom_toBottomOf")).isGreaterThan(0);

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Switching to code tab
    myEditorFixture.switchToTab("Text");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String contentInFile = myEditorFixture.getCurrentFileContents();

    //Asserting code changes in xml file.
    assertThat(contentInFile).contains("    <CheckBox\n" +
                                       "        android:id=\"@+id/checkBox\"");

    assertThat(contentInFile).contains("        android:text=\"CheckBox\"\n" +
                                       "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                                       "        app:layout_constraintRight_toRightOf=\"parent\"\n" +
                                       "        app:layout_constraintTop_toTopOf=\"parent\" />");

    assertThat(contentInFile).contains("<Button\n" +
                                       "        android:id=\"@+id/button\"");

    assertThat(contentInFile).contains("        android:text=\"Button\"\n" +
                                       "        app:layout_constraintBaseline_toBaselineOf=\"@id/checkBox\"");
  }

  /**
   * Verifies attributes can be removed from UI attributes panel
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 8e4fb543-6518-479b-aa31-6801c3c2bd62
   * <pre>
   *   Test Steps:
   *   1) Create a Project with Empty Activity
   *   2) Switch to Design/Split View of layout using icons at top right corner of editor.
   *   3) Open Declared Attributes panel select any attribute &gt; Click on "-" icon (Verify 1)
   *   Verification:
   *   1) Attribute should be removed from Design view and from XML file
   * </pre>
   */

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testRemoveAttributesFromDeclaredAttributesPanel() {
    //Finding textView in the design window and clicking on it
    myNlEditorFixture.findView("TextView", 0).getSceneComponent().click();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    SectionFixture declaredAttributesPanel = myNlEditorFixture
      .getAttributesPanel()
      .waitForId("<unnamed>")
      .findSectionByName("Declared Attributes");

    //Expand declared attributes section.
    declaredAttributesPanel.expand();

    //Finding the initial location of the dragged TextView location
    Point textViewInitialLocation = myNlEditorFixture.findView("TextView", 0).getSceneComponent().getMidPoint();

    //Removing pre-added layout constraints from declared attributes
    removeAttributeFromDeclaredAttributes("<unnamed>","layout_constraintBottom_toBottomOf");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    removeAttributeFromDeclaredAttributes("<unnamed>","layout_constraintStart_toStartOf");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    removeAttributeFromDeclaredAttributes("<unnamed>","layout_constraintEnd_toEndOf");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    removeAttributeFromDeclaredAttributes("<unnamed>","layout_constraintTop_toTopOf");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myNlEditorFixture.waitForSurfaceToLoad();
    myNlEditorFixture.waitForRenderToFinish();

    Point textViewFinalLocation = myNlEditorFixture.findView("TextView", 0).getSceneComponent().getMidPoint();

    //Validating that the button location is changed after adding baseline constraint with respect to textView.
    assertThat(textViewFinalLocation.x).isNotEqualTo(textViewInitialLocation.x);
    assertThat(textViewFinalLocation.y).isNotEqualTo(textViewInitialLocation.y);

    //Validating that the attributes removed are not present in the declared attributes panel
    assertThat(myPTable.findRowOf("layout_constraintBottom_toBottomOf")).isLessThan(0);
    assertThat(myPTable.findRowOf("layout_constraintStart_toStartOf")).isLessThan(0);
    assertThat(myPTable.findRowOf("layout_constraintEnd_toEndOf")).isLessThan(0);
    assertThat(myPTable.findRowOf("layout_constraintTop_toTopOf")).isLessThan(0);

    //Switching to code tab
    myEditorFixture.switchToTab("Text");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String contentInFile = myEditorFixture.getCurrentFileContents();

    //Asserting code changes in xml file.
    assertThat(contentInFile).doesNotContain("app:layout_constraintBottom_toBottomOf=\"parent\"");
    assertThat(contentInFile).doesNotContain("app:layout_constraintStart_toStartOf=\"parent\"");
    assertThat(contentInFile).doesNotContain("app:layout_constraintEnd_toEndOf=\"parent\"");
    assertThat(contentInFile).doesNotContain("app:layout_constraintTop_toTopOf=\"parent\"");
  }

  private void addLayoutConstraintInDeclaredAttributes(String attributeID, String constraintLayoutName, String constraintComponent) {
    refreshDeclaredAttributesTable(attributeID);

    //Adding new attribute
    declaredAttributes.clickAddAttributeActionButton();

    refreshDeclaredAttributesTable(attributeID);

    //Getting the row value where the new row is created.
    int rowCount = myPTable.rowCount()-1;

    //Adding the constraint layout information and pressing enter.
    myPTable.robot().typeText(constraintLayoutName);
    myPTable.robot().pressAndReleaseKeys(KeyEvent.VK_ENTER);

    //refreshing the table.
    refreshDeclaredAttributesTable(attributeID);

    //Clicking on the column 1 where the new attribute is created.
    myPTable.click(TableCell.row(rowCount).column(1), MouseButton.LEFT_BUTTON);
    myPTable.click(TableCell.row(rowCount).column(1), MouseButton.LEFT_BUTTON); //To reduce flakiness
    //Adding the component and pressing enter.
    myPTable.robot().enterText(constraintComponent);
    myPTable.robot().pressAndReleaseKeys(KeyEvent.VK_ENTER);

    refreshDeclaredAttributesTable(attributeID);
  }

  private void removeAttributeFromDeclaredAttributes(String attributeID, String attributeName) {
    refreshDeclaredAttributesTable(attributeID);

    //find the attribute you need to remove.
    int attributeRow = myPTable.findRowOf(attributeName);

    //Clicking on the row, where attribute is present.
    myPTable.click(TableCell.row(attributeRow).column(0), MouseButton.LEFT_BUTTON);
    myPTable.click(TableCell.row(attributeRow).column(0), MouseButton.LEFT_BUTTON); //To reduce flakiness.

    //Clicking on the remove attribute button.
    declaredAttributes.clickRemoveAttributeActionButton();
  }

  private void refreshDeclaredAttributesTable(String attributeID) {
    //Method to constantly get the latest Table info in declared Attributes
    declaredAttributes =  myNlEditorFixture
      .getAttributesPanel()
      .waitForId(attributeID)
      .findSectionByName("Declared Attributes");

    //Updating the Table information.
    myPTable = declaredAttributes.getPTable();
  }
}