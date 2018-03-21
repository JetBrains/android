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
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.AddDestinationMenuFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.DestinationListFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import org.fest.swing.driver.BasicJListCellReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * UI tests for {@link NlEditor} as used in the navigation editor.
 */
@RunWith(GuiTestRunner.class)
public class NavNlEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

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

    ((NavDesignSurfaceFixture)layout.getSurface()).openAddDestinationMenu().selectDestination("fragment_my");

    DestinationListFixture fixture = DestinationListFixture.Companion.create(guiTest.robot());
    fixture.replaceCellReader(new BasicJListCellReader(c -> c.toString()));
    fixture.selectItem("main_activity - Start");
    assertEquals(1, layout.getSelection().size());
    assertEquals("main_activity", layout.getSelection().get(0).getId());

    guiTest.robot().type('\b');

    layout.getAllComponents().forEach(component -> assertNotEquals("main_activity", component.getComponent().getId()));

    List<NlComponent> selectedComponents = fixture.getSelectedComponents();
    assertEquals(selectedComponents.size(), 0);
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/72238573
  @Test
  public void testCreateNew() throws Exception {
    IdeFrameFixture frame = guiTest.importProject("Navigation");
    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor(true);

    // This is separate to catch the case where we have a problem opening the file before sync is complete.
    frame.waitForGradleProjectSyncToFinish();
    layout.waitForRenderToFinish();

    AddDestinationMenuFixture fixture = ((NavDesignSurfaceFixture)layout.getSurface()).openAddDestinationMenu();
    fixture.clickCreateBlank();
    try {
      // click again to make sure the action only actually gets invoked once.
      // But it might already be hidden, so in that case catch the exception and keep going.
      fixture.clickCreateBlank();
    }
    catch (IllegalStateException e) {
      // nothing
    }

    assertEquals(1, layout.getSelection().size());
    assertEquals("fragment", layout.getSelection().get(0).getAttribute(ANDROID_URI, ATTR_LABEL));

    DestinationListFixture destinationListFixture = DestinationListFixture.Companion.create(guiTest.robot());
    List<NlComponent> selectedComponents = destinationListFixture.getSelectedComponents();
    assertEquals(selectedComponents.size(), 1);
    assertEquals(selectedComponents.get(0).getId(), "fragment");
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

    /*
    TODO:
    Unfortunately in gui tests we use the fallback list of packages, which doesn't yet contain the nav editor, and so adding it won't work
    yet. It's at least useful to see the the "add" dialog shows up at this point, though.
    */
    GuiTests.findAndClickOkButton(frame.waitForDialog("Failed to Add Dependency"));

    /*
    This is what should be here eventually:

    EditorFixture editor = guiTest.ideFrame().getEditor();
    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    */
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
    assertTrue(zoomInScale >  zoomOutScale);

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_0);
    double fitScale = fixture.getScale();

    assertTrue(Math.abs(fitScale - scale) < 0.001);
  }
}