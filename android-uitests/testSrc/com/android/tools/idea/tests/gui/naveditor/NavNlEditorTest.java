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

import static com.android.tools.idea.tests.gui.framework.fixture.newpsd.UiTestUtilsKt.waitForIdle;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.AddDestinationMenuFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.DestinationListFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.ui.UIUtil;
import java.awt.event.KeyEvent;
import java.util.List;
import org.fest.swing.driver.BasicJListCellReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI tests for {@link NlEditor} as used in the navigation editor.
 */
@RunWith(GuiTestRemoteRunner.class)
public class NavNlEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testSelectComponent() throws Exception {
    IdeFrameFixture frame = guiTest.importProject("Navigation").waitForGradleProjectSyncToFinish();
    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = frame.getEditor();
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor(true).waitForRenderToFinish();

    NlComponentFixture screen = ((NavDesignSurfaceFixture)layout.getSurface()).findDestination("first_screen");
    screen.click();

    assertThat(layout.getSelection()).containsExactly(screen.getComponent());

    DestinationListFixture destinationListFixture = DestinationListFixture.create(guiTest.robot());
    List<NlComponent> selectedComponents = destinationListFixture.getSelectedComponents();
    assertEquals(1, selectedComponents.size());
    assertEquals("first_screen", selectedComponents.get(0).getId());
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/72238573
  @Test
  public void testCreateAndDelete() throws Exception {
    NlEditorFixture layout = guiTest
      .importProject("Navigation")
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true);

    AddDestinationMenuFixture menuFixture = layout
      .waitForRenderToFinish()
      .getNavSurface()
      .openAddDestinationMenu()
      .waitForContents();

    assertEquals(3, menuFixture.visibleItemCount());
    guiTest.robot().enterText("fragment_my");
    assertEquals(1, menuFixture.visibleItemCount());

    menuFixture.selectDestination("fragment_my");

    DestinationListFixture fixture = DestinationListFixture.create(guiTest.robot());
    fixture.replaceCellReader(new BasicJListCellReader(c -> c.toString()));
    fixture.selectItem("main_activity - Start");
    assertEquals(1, layout.getSelection().size());
    assertEquals("main_activity", layout.getSelection().get(0).getId());

    guiTest.robot().type('\b');

    ApplicationManager.getApplication().invokeAndWait(() -> UIUtil.dispatchAllInvocationEvents());

    layout.getAllComponents().forEach(component -> assertNotEquals("main_activity", component.getComponent().getId()));

    List<NlComponent> selectedComponents = fixture.getSelectedComponents();
    assertEquals(0, selectedComponents.size());
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/137919011
  @Test
  public void testCreateNewFragmentFromWizard() throws Exception {
    StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.override(true);
    try {
      NlEditorFixture layout = guiTest
        .importProject("Navigation")
        .waitForGradleProjectSyncToFinish()
        .getEditor()
        .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
        .getLayoutEditor(true);

      AddDestinationMenuFixture menuFixture = layout
        .waitForRenderToFinish()
        .getNavSurface()
        .openAddDestinationMenu()
        .waitForContents();

      assertEquals(3, menuFixture.visibleItemCount());
      menuFixture.clickCreateNewFragment()
        .waitUntilStepErrorMessageIsGone()
        .chooseFragment("Fullscreen Fragment")
        .clickNextFragment()
        .waitUntilStepErrorMessageIsGone()
        .clickFinish();

      ApplicationManager.getApplication().invokeAndWait(() -> dispatchAllInvocationEventsInIdeEventQueue());
      waitForIdle();

      DestinationListFixture destinationListFixture = DestinationListFixture.create(guiTest.robot());
      List<NlComponent> selectedComponents = destinationListFixture.getSelectedComponents();
      assertEquals(1, selectedComponents.size());
      assertEquals("fullscreenFragment", selectedComponents.get(0).getId());
    } finally {
      StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.clearOverride();
    }
  }

  @Test
  public void testCreateAndDeleteWithSingleVariantSync() throws Exception {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    try {
      IdeFrameFixture frame = guiTest.importProject("Navigation");
      // Open file as XML and switch to design tab, wait for successful render
      final String file = "app/src/main/res/navigation/mobile_navigation.xml";
      NlEditorFixture layout = guiTest
        .ideFrame()
        .waitForGradleProjectSyncToFinish()
        .getEditor()
        .open(file, EditorFixture.Tab.DESIGN)
        .getLayoutEditor(true);

      layout
        .waitForRenderToFinish()
        .getNavSurface()
        .openAddDestinationMenu()
        .waitForContents()
        .clickCreateBlank()
        .getConfigureTemplateParametersStep()
        .enterTextFieldValue("Fragment Name:", "TestSingleVariantSync")
        .selectComboBoxItem("Source Language:", "Java")
        .wizard()
        .clickFinish();

      ApplicationManager.getApplication().invokeAndWait(() -> UIUtil.dispatchAllInvocationEvents());

      // The below verifies that there isn't an exception when interacting with the nav editor after a sync.
      // See b/112451835
      frame
        .waitForGradleProjectSyncToFinish()
        .getEditor()
        // Open the file again in case build.gradle is open after gradle sync
        .open(file, EditorFixture.Tab.DESIGN)
        .getLayoutEditor(true)
        .waitForRenderToFinish()
        .getNavSurface()
        .findDestination("testSingleVariantSync")
        .click();

      guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);
    }
    finally {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
    }
  }

  @Test
  public void testCreateAndCancel() throws Exception {
    final String file = "app/src/main/res/navigation/mobile_navigation.xml";
    IdeFrameFixture frame = guiTest.importProject("Navigation");
    // Open file as XML and switch to design tab, wait for successful render
    NlEditorFixture layout = guiTest
      .ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open(file, EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true);

    layout
      .waitForRenderToFinish()
      .getNavSurface()
      .openAddDestinationMenu()
      .waitForContents()
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
      // Open the file again in case build.gradle is open after gradle sync
      .open(file, EditorFixture.Tab.DESIGN)
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
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(CreateResourceFileDialogFixture::find, "File", "New", "Android Resource File")
      .setFilename("nav")
      .setType("navigation")
      .clickOkAndWaitForDependencyDialog()
      .clickOk()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .getLayoutEditor(false)
      .waitForRenderToFinish()
      .assertCanInteractWithSurface();

    String contents = guiTest
      .ideFrame()
      .getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();

    assertTrue(contents.contains("navigation-fragment"));
    assertTrue(contents.contains("navigation-ui"));
  }

  @Test
  public void testCancelAddDependency() throws Exception {
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(CreateResourceFileDialogFixture::find, "File", "New", "Android Resource File")
      .setFilename("nav")
      .setType("navigation")
      .clickOkAndWaitForDependencyDialog()
      .clickCancel()
      .clickOk();
    assertFalse(guiTest
                  .ideFrame()
                  .getEditor()
                  .getLayoutEditor(false, false)
                  .canInteractWithSurface());

    guiTest.ideFrame()
      .getEditor()
      .open("app/build.gradle")
      .moveBetween("", "testImplementation")
      .enterText("    implementation 'android.arch.navigation:navigation-fragment:+'\n")
      .getIdeFrame()
      .requestProjectSync()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/res/navigation/nav.xml")
      .getLayoutEditor(true)
      .waitForRenderToFinish()
      .assertCanInteractWithSurface();
  }

  @Test
  public void testEmptyDesigner() throws Exception {
    NlEditorFixture layout = guiTest
      .importProject("Navigation")
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/res/navigation/empty_navigation.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true);

    NavDesignSurfaceFixture fixture = layout
      .waitForRenderToFinish()
      .getNavSurface();

    fixture.clickOnEmptyDesignerTarget();

    AddDestinationMenuFixture menuFixture = fixture
      .getAddDestinationMenu()
      .waitForContents();
    assertTrue(menuFixture.isBalloonVisible());
  }
}