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
package com.android.tools.idea.tests.gui.intellijplatform;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class FindUsagesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  private IdeFrameFixture ideFrame = null;

  private static String MAIN_ACTIVITY_FILE = "app/src/main/java/com/codegeneration/MainActivity.java";

  private static String INVENTORY_FILE = "app/src/main/java/com/codegeneration/Inventory.kt";

  private static String CAR_FILE = "app/src/main/java/com/codegeneration/Car.java";

  @Before
  public void setUpProject() throws Exception {
    ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("CodeGeneration", Wait.seconds(120));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * List find usages for Methods, Class, fields in Project
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 8e4fb543-6518-479b-aa31-6801c3c2bd62
   * <pre>
   *Test Steps:
   *   1) Open Project from Drive.
   *    Added the the required classes in sample project to improve the speed of import.
   *   --Search for method usages in Java/Kotlin classes with below steps
   *   2) Open MainActivity.java file select & Right Click on addNewCar() Method at line #17
   *   3) Select Find Usages (alt+F7) (Verify 1)
   *   4) Open Inventory.kt class select & Right Click on addNewCar() Method at line #9
   *   5) Select Find Usages (alt+F7) (Verify 2)
   *   --Search for class usages in Java/Kotlin classes with below steps
   *   6) Open Car.java class > Select & Right Click Car class at line #3
   *   7) Select Find Usages (alt+F7) (Verify 3)
   *   --Search for field usages in Java/Kotlin classes with below steps
   *   8) Open Car.java class select & Right click on "make" field at line #5
   *   9) Select Find Usages (alt+F7) (Verify 4)
   *
   * Verification:
   *   1) There should be only one usage in MainActivity.java under onCreate Function
   *   2) There should be two usages one in Inventory.kt constructor and one more in MainActivity.java under onCreate Function
   *   3) There should be five usages of this class  two in MainActivity.java, two in Car class itself and one usage in Inventory.kt class
   *   4) There should be one "Value read" and  "Value write" Usages in Car.java class
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void findUsagesTest() throws Exception{
    EditorFixture editor = guiTest.ideFrame()
      .getEditor();

    // Search for method usages in java and kotlin
    editor.open(MAIN_ACTIVITY_FILE, EditorFixture.Tab.EDITOR)
      .select(String.format("addNewCar()"));
    invokeFindUsages();
    TimeUnit.SECONDS.sleep(5); // Wait for search to complete
    // Verify 1 : only one usage in MainActivity.java under onCreate Function
    assertTrue(ideFrame.findToolWindowContains("MainActivity"));
    assertFalse(ideFrame.findToolWindowContains("Inventory"));

    editor.open(INVENTORY_FILE, EditorFixture.Tab.EDITOR)
      .select(String.format("fun (addNewCar)"));
    invokeFindUsages();
    TimeUnit.SECONDS.sleep(5);
    // Verify 2 : two usages one in Inventory.kt constructor and one more in MainActivity.java
    assertTrue(ideFrame.findToolWindowContains("Inventory"));
    assertTrue(ideFrame.findToolWindowContains("MainActivity"));

    // Search for class usages in Java/Kotlin classes
    editor.open(CAR_FILE, EditorFixture.Tab.EDITOR)
      .select(String.format("public class (Car)"));
    invokeFindUsages();
    TimeUnit.SECONDS.sleep(5); // Wait for search to complete
    // Verify 3 : There should be five usages of this class  two in MainActivity.java,
    // two in Car.Java  class itself and one usage in Inventory.kt
    assertTrue(ideFrame.findToolWindowContains("Class static member access"));
    assertTrue(ideFrame.findToolWindowContains("Local variable declaration"));
    assertTrue(ideFrame.findToolWindowContains("Method return type"));
    assertTrue(ideFrame.findToolWindowContains("Nested class/object"));
    assertTrue(ideFrame.findToolWindowContains("New instance creation"));
    assertTrue(ideFrame.findToolWindowContains("MainActivity"));
    assertTrue(ideFrame.findToolWindowContains("Car"));
    assertTrue(ideFrame.findToolWindowContains("Inventory.kt"));


    // Search for field usages in Java/Kotlin classes
    editor.select(String.format("private String (make)"));
    invokeFindUsages();
    TimeUnit.SECONDS.sleep(5); // Wait for search to complete
    assertTrue(ideFrame.findToolWindowContains("Value read"));
    assertTrue(ideFrame.findToolWindowContains("getMake"));
    assertTrue(ideFrame.findToolWindowContains("Value write"));
    assertTrue(ideFrame.findToolWindowContains("setMake"));
  }

  private void invokeFindUsages(){
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    Point mouse = MouseInfo.getPointerInfo().getLocation();
    guiTest.robot().click(mouse, MouseButton.RIGHT_BUTTON, 1);
    new JPopupMenuFixture(guiTest.robot(), guiTest.robot().findActivePopupMenu())
      .menuItemWithPath("Find Usages")
      .click();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}