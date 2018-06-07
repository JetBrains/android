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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.DestinationListFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * UI tests for {@link NlEditor} as used in the navigation editor.
 */
@Ignore("b/71804935")
@RunWith(GuiTestRunner.class)
public class NavNlEditorTest {

  private final GuiTestRule guiTest = new GuiTestRule();
  private final ExpectedException exception = ExpectedException.none();
  @Rule public final RuleChain ruleChain = RuleChain.emptyRuleChain().around(exception).around(guiTest);

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

    // b/69000941: Investigate leak caused by not closing editor explicitly
    editor.close();

    assertThat(layout.getSelection()).containsExactly(screen.getComponent());
  }

  @Ignore("b/70305086")
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

    ((NavDesignSurfaceFixture)layout.getSurface()).openAddExistingMenu().selectDestination("MyFragment");

    DestinationListFixture.Companion.create(guiTest.robot()).selectItem("Main Activity");
    assertEquals(1, layout.getSelection().size());
    assertEquals("main_activity", layout.getSelection().get(0).getId());

    guiTest.robot().type('\b');

    layout.getAllComponents().forEach(component -> assertNotEquals("main_activity", component.getComponent().getId()));
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

    There are some exceptions generated currently. Probably this should be cleaned up slightly, but you're still going to be in a bad state.
    */
    exception.expectMessage(allOf(
      containsString("NavigationSchema must be created first")));
    GuiTests.findAndClickOkButton(frame.waitForDialog("Failed to Add Dependency"));

    /*
    This is what should be here eventually:

    EditorFixture editor = guiTest.ideFrame().getEditor();
    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    */
  }
}