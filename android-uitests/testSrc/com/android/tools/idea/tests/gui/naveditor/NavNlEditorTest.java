/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.naveditor;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.AddDestinationMenuFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.DestinationListFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.ui.UIUtil;
import org.fest.swing.driver.BasicJListCellReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * UI tests for {@link NlEditor} as used in the navigation editor.
 */
@RunWith(GuiTestRemoteRunner.class)
public class NavNlEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @RunIn(TestGroup.UNRELIABLE)  // b/80343801
  @Test
  public void testSelectComponent() throws Exception {
    IdeFrameFixture frame = guiTest.importProject("Navigation");
    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor(true);

    // This is separate to catch the case where we have a problem opening the file before sync is complete.
    frame.waitForGradleProjectSyncToFinish();

    layout.waitForRenderToFinish();

    NlComponentFixture screen = ((NavDesignSurfaceFixture)layout.getSurface()).findDestination("first_screen");
    screen.click();

    assertThat(layout.getSelection()).containsExactly(screen.getComponent());

    DestinationListFixture destinationListFixture = DestinationListFixture.Companion.create(guiTest.robot());
    List<NlComponent> selectedComponents = destinationListFixture.getSelectedComponents();
    assertEquals(selectedComponents.size(), 1);
    assertEquals(selectedComponents.get(0).getId(), "first_screen");
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/72238573
  @Test
  public void testCreateAndDelete() throws Exception {
    IdeFrameFixture frame = guiTest.importProject("Navigation");
    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor(true);

    // This is separate to catch the case where we have a problem opening the file before sync is complete.
    frame.waitForGradleProjectSyncToFinish();
    layout.waitForRenderToFinish();

    AddDestinationMenuFixture menuFixture = ((NavDesignSurfaceFixture)layout.getSurface()).openAddDestinationMenu();
    assertEquals(4, menuFixture.visibleItemCount());
    guiTest.robot().enterText("fragment_my");
    assertEquals(1, menuFixture.visibleItemCount());

    menuFixture.selectDestination("fragment_my");

    DestinationListFixture fixture = DestinationListFixture.Companion.create(guiTest.robot());
    fixture.replaceCellReader(new BasicJListCellReader(c -> c.toString()));
    fixture.selectItem("main_activity - Start");
    assertEquals(1, layout.getSelection().size());
    assertEquals("main_activity", layout.getSelection().get(0).getId());

    guiTest.robot().type('\b');

    layout.getAllComponents().forEach(component -> assertNotEquals("main_activity", component.getComponent().getId()));

    ApplicationManager.getApplication().invokeAndWait(() -> UIUtil.dispatchAllInvocationEvents());

    List<NlComponent> selectedComponents = fixture.getSelectedComponents();
    assertEquals(selectedComponents.size(), 0);
  }

  @Test
  public void testCreateAndCancel() throws Exception {
    IdeFrameFixture frame = guiTest.importProject("Navigation");
    // Open file as XML and switch to design tab, wait for successful render
    NlEditorFixture layout = guiTest
      .ideFrame()
      .getEditor()
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .frame()
      // This is separate to catch the case where we have a problem opening the file before sync is complete.
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .getLayoutEditor(true);

    layout
      .waitForRenderToFinish()
      .getNavSurface()
      .openAddDestinationMenu()
      .clickCreateBlank()
      .getConfigureTemplateParametersStep()
      .enterTextFieldValue("Fragment Name:", "TestCreateAndCancelFragment")
      .selectComboBoxItem("Source Language:", "Java")
      .wizard()
      .clickFinish();

    ApplicationManager.getApplication().invokeAndWait(() -> UIUtil.dispatchAllInvocationEvents());

    long matchingComponents = frame
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .getLayoutEditor(true)
      .waitForRenderToFinish()
      .getAllComponents().stream()
      .filter(component -> "testCreateAndCancelFragment".equals(component.getComponent().getId()))
      .count();

    assertEquals(1, matchingComponents);

    int countAfterAdd = layout.getAllComponents().size();

    layout
      .getNavSurface()
      .openAddDestinationMenu()
      .clickCreateBlank()
      .getConfigureTemplateParametersStep()
      .enterTextFieldValue("Fragment Name:", "TestCreateAndCancelFragment2")
      .selectComboBoxItem("Source Language:", "Java")
      .wizard()
      .clickCancel();

    ApplicationManager.getApplication().invokeAndWait(() -> UIUtil.dispatchAllInvocationEvents());
    layout.waitForRenderToFinish();

    assertEquals(countAfterAdd, layout.getAllComponents().size());
  }

  @Test
  public void testAddDependency() throws Exception {
    IdeFrameFixture frame = guiTest.importSimpleLocalApplication();
    frame.getProjectView().selectAndroidPane().clickPath("app");
    frame.invokeMenuPath("File", "New", "Android Resource File");
    CreateResourceFileDialogFixture.find(guiTest.robot())
                                   .setFilename("nav")
                                   .setType("navigation")
                                   .clickOk();
    GuiTests.findAndClickOkButton(frame.waitForDialog("Add Project Dependency"));
    EditorFixture editor = guiTest.ideFrame().getEditor();
    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
  }

  @Test
  public void testKeyMappings() throws Exception {
    IdeFrameFixture frame = guiTest.importProject("Navigation");
    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor(true);

    // This is separate to catch the case where we have a problem opening the file before sync is complete.
    frame.waitForGradleProjectSyncToFinish();
    layout.waitForRenderToFinish();

    DesignSurfaceFixture fixture = layout.getSurface();
    NlComponentFixture screen = ((NavDesignSurfaceFixture)fixture).findDestination("first_screen");
    screen.click();

    double scale = fixture.getScale();

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_MINUS);
    double zoomOutScale = fixture.getScale();
    assertTrue(zoomOutScale < scale);

    guiTest.robot().pressKey(KeyEvent.VK_SHIFT);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_PLUS);
    guiTest.robot().releaseKey(KeyEvent.VK_SHIFT);
    double zoomInScale = fixture.getScale();
    assertTrue(zoomInScale > zoomOutScale);

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_0);
    double fitScale = fixture.getScale();

    assertTrue(Math.abs(fitScale - scale) < 0.001);
  }
}