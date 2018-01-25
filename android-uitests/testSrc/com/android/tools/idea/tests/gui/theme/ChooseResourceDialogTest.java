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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ColorPickerFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SlideFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlPropertyInspectorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPropertyFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.*;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.listToString;
import static com.android.tools.idea.tests.gui.framework.GuiTests.tableToString;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.*;

/**
 * UI tests regarding the ChooseResourceDialog
 *
 * Note that {@link ThemeEditorTableTest} also exercises the resource chooser a bit
 */
@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class ChooseResourceDialogTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/71956091
  @Test
  public void testColorStateList() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("StateListApplication");
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(7).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/text_color", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    assertThat(dialog.getTitle()).isEqualTo("Select Resource for android:textColorPrimary");

    StateListPickerFixture stateListPicker = dialog.getStateListPicker();
    java.util.List<StateListComponentFixture> states = stateListPicker.getStateComponents();
    assertThat(states).hasSize(4);

    final StateListComponentFixture state0 = states.get(0);
    assertEquals("Not enabled", state0.getStateName());
    assertEquals("?android:attr/colorForeground", state0.getValue());
    assertFalse(state0.getValueComponent().hasWarningIcon());
    assertTrue(state0.isAlphaVisible());
    assertEquals("@dimen/text_alpha", state0.getAlphaValue());
    assertFalse(state0.getAlphaComponent().hasWarningIcon());

    final StateListComponentFixture state1 = states.get(1);
    assertEquals("Checked", state1.getStateName());
    assertEquals("#5034FAB2", state1.getValue());
    assertFalse(state1.getValueComponent().hasWarningIcon());
    assertFalse(state1.isAlphaVisible());

    final StateListComponentFixture state2 = states.get(2);
    assertEquals("Pressed", state2.getStateName());
    assertEquals("@color/invalidColor", state2.getValue());
    assertTrue(state2.getValueComponent().hasWarningIcon());
    assertFalse(state2.isAlphaVisible());

    final StateListComponentFixture state3 = states.get(3);
    assertEquals("Default", state3.getStateName());
    assertEquals("?attr/myColorAttribute", state3.getValue());
    assertFalse(state3.getValueComponent().hasWarningIcon());
    assertFalse(state3.isAlphaVisible());

    dialog.waitForErrorPanel();
    dialog.clickCancel();
    stateListCell.stopEditing();
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/70699703
  @Test
  public void testEditColorReference() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("StateListApplication");
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/ref_color", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());

    SwatchComponentFixture state1 = dialog.getEditReferencePanel().getSwatchComponent();
    assertEquals("@color/myColor", state1.getText());
    assertFalse(state1.hasWarningIcon());

    dialog.clickCancel();
    stateListCell.stopEditing();
  }

  @Test
  public void testResourcePickerNameError() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    // Cell (1,0) should be some color
    JTableCellFixture colorCell = themeEditorTable.cell(row(1).column(0));

    // click on a color
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    JTextComponentFixture name = dialog.getNameTextField();

    // add mistake into name field
    String badText = "(";
    name.deleteText();
    name.enterText("color" + badText);
    String text = name.text();
    assertThat(text).endsWith(badText);

    final String expectedError = "<html><font color='#ff0000'><left>'" + badText +
                                 "' is not a valid resource name character</left><br/></font></html>";
    Wait.seconds(100).expecting("error to update").until(() -> dialog.getError().equals(expectedError));

    dialog.clickCancel();
    colorCell.cancelEditing();
  }

  /**
   * Test that the alpha slider and the text field are hidden when we are not in ARGB.
   */
  @Test
  public void testColorPickerAlpha() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    ColorPickerFixture colorPicker = dialog.getColorPicker();
    Color color = new Color(200, 0, 0, 200);
    colorPicker.setFormat("ARGB");
    colorPicker.setColorWithIntegers(color);
    JTextComponentFixture alphaLabel = colorPicker.getLabel("A:");
    SlideFixture alphaSlide = colorPicker.getAlphaSlide();
    alphaLabel.requireVisible();
    alphaSlide.requireVisible();
    colorPicker.setFormat("RGB");
    alphaLabel.requireNotVisible();
    alphaSlide.requireNotVisible();
    colorPicker.setFormat("HSB");
    alphaLabel.requireNotVisible();
    alphaSlide.requireNotVisible();

    dialog.clickOK();
    colorCell.stopEditing();
  }

  /**
   * Test the resource table editor, filtering and selection
   */
  @Test
  public void testEditString() throws IOException {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture textView = layout.findView("TextView", 0);
    textView.click();

    // It should be selected now
    assertThat(layout.getSelection()).containsExactly(textView.getComponent());

    // Get property sheet, find text property, open customizer
    NlPropertyFixture property = layout.getPropertiesPanel().openAsInspector().findProperty("text");

    ChooseResourceDialogFixture dialog = property.clickCustomizer();
    JTableFixture nameTable = dialog.getResourceNameTable();

    assertEquals("Project                                                     \n" +
                 "action_settings               Settings                      \n" +
                 "app_name                      Simple Application            \n" +
                 "cancel                        取消                            \n" +
                 "hello_world                   Hello world!                  \n" +
                 "some_id                       12345678                      \n",
                 tableToString(nameTable, 0, 6, 0, 5, 30));

    // Search for "app" and confirm that we only show the header nodes as well as resource names
    // that match. We also used to check that we were highlighting the right portion of the
    // string here, but after switching away from HTML labels to IntelliJ's ColoredTableCellRenderer,
    // this is no longer visible from the table fixture.
    dialog.getSearchField().enterText("app");
    assertEquals("Project                                                                         \n" +
                 "app_name                                                                        \n" +
                 "android                                                                         \n" +
                 "Theme attributes                                                                \n",
                 tableToString(nameTable, 0, 6, 0, 1, 80));

    JTableFixture valueTable = dialog.getResourceValueTable();
    assertEquals("Default                                 Simple Application                      \n" +
                 "English                                 Simple Application(en)                  \n" +
                 "Hebrew                                                                          \n" +
                 "Tamil                                   Simple Application                      \n" +
                 "English, United Kingdom                 Simple Application                      \n" +
                 "Chinese, China                          谷歌 I/O                                  \n",
                 tableToString(valueTable));

    dialog.clickOK();
  }

  /**
   * Test looking at the attributes for a drawable
   */
  @Test
  public void testDrawable() throws IOException {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture imageView = layout.findView("ImageView", 0);
    imageView.click();
    assertThat(layout.getSelection()).containsExactly(imageView.getComponent());

    // Get property sheet, find srcCompat property, open customizer
    NlPropertyInspectorFixture fixture = layout.getPropertiesPanel().openAsInspector();

    NlPropertyFixture property = fixture.findProperty("srcCompat");

    ChooseResourceDialogFixture dialog = property.clickCustomizer();
    JTabbedPaneFixture tabs = dialog.getTabs();
    tabs.requireTabTitles("Drawable", "Color", "Array", "ID", "String", "Style");

    dialog.getSearchField().enterText("che");
    JListFixture projectList = dialog.getList("Project");
    JListFixture frameworkList = dialog.getList("android");

    assertEquals("ic_launcher                             \n",
                 listToString(projectList));

    assertEquals("checkbox_off_background                 \n" +
                 "checkbox_on_background                  \n",
                 listToString(frameworkList));

    // This should jump to the project list and select the first one: ic_launcher
    dialog.getSearchField().pressAndReleaseKeys(KeyEvent.VK_DOWN);
    dialog.getDrawablePreviewName().requireText("ic_launcher");
    assertThat(dialog.getDrawableResolutionChain()).isEqualTo(
      "@drawable/ic_launcher\n" +
      " ⇒ ic_launcher.xml\n");


    // Ensure that the pixel in the middle of the preview area is red
    JLabelFixture previewLabel = dialog.getDrawablePreviewLabel();
    Icon icon = previewLabel.target().getIcon();
    //noinspection UndesirableClassUsage
    BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics graphics = img.getGraphics();
    icon.paintIcon(previewLabel.target(), graphics, 0, 0);
    graphics.dispose();
    assertEquals(0xFFFF0000, img.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2));

    dialog.clickOK();

    Wait.seconds(2).expecting("property to have the new value").until(() -> property.getValue().equals("@drawable/ic_launcher"));
  }

  /**
   * Checks that, when assigning the src for an ImageView we do not allow color state lists (since they crash at runtime).
   * http://b/70650615
   */
  @Test
  public void testDrawableDoesNotAllowColorStateLists() throws IOException {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture imageView = layout.findView("ImageView", 0);
    imageView.click();
    assertThat(layout.getSelection()).containsExactly(imageView.getComponent());

    // Get property sheet, find srcCompat property, open customizer
    NlPropertyInspectorFixture fixture = layout.getPropertiesPanel().openAsInspector();

    NlPropertyFixture property = fixture.findProperty("srcCompat");

    ChooseResourceDialogFixture dialog = property.clickCustomizer();
    JTabbedPaneFixture tabs = dialog.getTabs();
    tabs.requireTabTitles("Drawable", "Color", "Array", "ID", "String", "Style");
    dialog.clickOnTab("Color");

    dialog.getSearchField().enterText("actionMenuTextColor");
    JListFixture themeAttrList = dialog.getList("Theme attributes", 1);

    assertTrue(listToString(themeAttrList).isEmpty());

    dialog.clickCancel();
  }

  /**
   * Test if the color tab is selected by default when selecting a resource for backgroundTint
   */
  @Test
  public void testDefaultProperty() throws IOException {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture textView = layout.findView("Button", 0);
    textView.click();
    assertThat(layout.getSelection()).containsExactly(textView.getComponent());

    // Get property sheet, find srcCompat property, open customizer
    NlPropertyInspectorFixture fixture = layout.getPropertiesPanel().openAsInspector();

    NlPropertyFixture property = fixture.findProperty("backgroundTint");

    ChooseResourceDialogFixture dialog = property.clickCustomizer();
    JTabbedPaneFixture tabs = dialog.getTabs();
    tabs.requireTabTitles("Drawable", "Color");
    assertThat(dialog.getSelectedTabTitle()).isEqualTo("Color");

    dialog.clickCancel();
  }

  @Test
  public void testSearchExactMatch() throws IOException {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture imageView = layout.findView("ImageView", 0);
    imageView.click();
    assertThat(layout.getSelection()).containsExactly(imageView.getComponent());

    // Get property sheet, find srcCompat property, open customizer
    NlPropertyInspectorFixture fixture = layout.getPropertiesPanel().openAsInspector();

    NlPropertyFixture property = fixture.findProperty("srcCompat");

    ChooseResourceDialogFixture dialog = property.clickCustomizer();
    JTabbedPaneFixture tabs = dialog.getTabs();
    tabs.requireTabTitles("Drawable", "Color", "Array", "ID", "String", "Style");

    dialog.getSearchField().enterText("ic_launcher ");
    JListFixture projectList = dialog.getList("Project");

    assertEquals("ic_launcher                             \n",
                 listToString(projectList));

    dialog.clickCancel();
  }

  @RunIn(TestGroup.UNRELIABLE) // b/72499090
  @Test
  public void testArray() throws IOException {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/spinner.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture spinner = layout.findView("Spinner", 0);
    spinner.click();
    assertThat(layout.getSelection()).containsExactly(spinner.getComponent());

    // Get property sheet, find srcCompat property, open customizer
    NlPropertyInspectorFixture fixture = layout.getPropertiesPanel().openAsInspector();

    NlPropertyFixture property = fixture.findProperty("entries");

    ChooseResourceDialogFixture dialog = property.clickCustomizer();
    JTabbedPaneFixture tabs = dialog.getTabs();
    tabs.requireTabTitles("Drawable", "Color", "Array", "ID", "String", "Style");

    dialog.clickOnTab("Array");

    // This should jump to the project list and select the first one: ic_launcher
    JTableFixture nameTable = dialog.getResourceNameTable();

    assertEquals("Project             \n" +
                 "my_array  one       \n",
                 tableToString(nameTable, 0, 2, 0, 5, 10));
    nameTable.cell("my_array").click();

    dialog.clickOK();

    Wait.seconds(2).expecting("property to have the new value").until(() -> property.getValue().equals("@array/my_array"));
  }
}
