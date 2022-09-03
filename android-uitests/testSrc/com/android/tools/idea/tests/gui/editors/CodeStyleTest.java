// android-uitests/testSrc/com/android/tools/idea/tests/gui/editors/CodeStyleTest.java

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
package com.android.tools.idea.tests.gui.editors;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewJavaClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewKotlinClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CodeStyleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private IdeSettingsDialogFixture mySettingsDialog;
  private static String javaCodeLanguage = "Java";
  private static String kotlincodeLanguage = "Kotlin";
  private static String oldIndent = "4";
  private static String oldTabSize = "4";
  private static String newTabSize = "10";
  private static String newIndent = "20";
  private static String origJavaContent = "\nString name;\nint age;\n\npublic void setAge(int age) {\nthis.age = age;\n}\n";
  private static String origKotlinContent = "\nvar name: String? = null\nvar age = 0\n\nfun setAge(age: Int) {\nthis.age = age\n}\n";
  private static String expectedJavaContent = "package google.simpleapplication;\n" +
                                              "\n" +
                                              "public class Person {\n" +
                                              "                    String name;\n" +
                                              "                    int age;\n" +
                                              "\n" +
                                              "                    public void setAge(int age) {\n" +
                                              "                                        this.age = age;\n" +
                                              "                    }\n" +
                                              "\n" +
                                              "}\n";
  private static String expectedKotlinBeforeFormatContent = "package google.simpleapplication\n" +
                                                            "\n" +
                                                            "class Children {\n" +
                                                            "    var name\u003A String"+"\u003F"+" = null\n" +
                                                            "    var age = 0\n" +
                                                            "\n" +
                                                            "    fun setAge(age: Int) {\n" +
                                                            "        this.age = age\n" +
                                                            "    }\n" +
                                                            "\n" +
                                                            "}\n";
  private static String expectedKotlinContent = "package google.simpleapplication\n" +
                                                "\n" +
                                                "class Children {\n" +
                                                "                    var name\u003A String"+"\u003F"+" = null\n" +
                                                "                    var age = 0\n" +
                                                "\n" +
                                                "                    fun setAge(age: Int) {\n" +
                                                "                                        this.age = age\n" +
                                                "                    }\n" +
                                                "\n" +
                                                "}\n";

  @Before
  public void setUp() throws Exception {
    guiTest.importSimpleApplication();
    guiTest.robot().waitForIdle();
  }

  /**
   * Verifies Android Studio (Java and Kotlin) is respecting code styles specified in settings
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 575546d6-afb2-4b24-b8ac-2be713669188
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project,
   *   2. Open Settings File > Settings on Linux Android Studio > Preferences on Mac
   *   3. Choose Editor > Code Style > Java > Change
   *        Tab size to 10
   *        Indent : 20
   *   4. Go to MyActivity.java and create 'Person' java class and paste some java code (Verify 1)
   *   5. Create 'Children' kotlin class and paster some kotlin code (Verify 2)
   *   6. Remove content of Kotlin class
   *   7. Choose Editor > Code Style > Kotlin  > Change
   *        Tab size to 10
   *        Indent : 20
   *   8. Paste some code in Kotlin class (Verify 1)
   *   Verify:
   *   1. After paste code tab size and indent should change according to the numbers in settings
   *   2. Tab size and indent should not change in Kotlin class
   *   </pre>
   * <p>
   */

  @Test
  public void testModifyJavaCodeStyle() throws IOException, InterruptedException {
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    //Change tab size and indent for Java (only)
    testModifyCodeStyle(javaCodeLanguage, oldTabSize, newTabSize, oldIndent, newIndent);

    PaneClickPath(ideFrame);

    // Create a 'Person' class in the library and check the new style is applied to Java class but not to Kotlin class
    invokeJavaClass(ideFrame).enterName("Person").clickOk();
    editor.open("/app/src/main/java/google/simpleapplication/Person.java")
      .moveBetween("public class Person {","")
      .enterText(origJavaContent);
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    assertEquals(expectedJavaContent, editor.getCurrentFileContents());

    PaneClickPath(ideFrame);
    invokeKotlinClass(ideFrame).enterName("Children").clickOk();
    editor.open("/app/src/main/java/google/simpleapplication/Children.kt")
      .moveBetween("}", "")
      .enterText("\n")
      .moveBetween("class Children {","")
      .enterText(origKotlinContent);
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    assertEquals(expectedKotlinBeforeFormatContent, editor.getCurrentFileContents());

    ideFrame.invokeMenuPath("Edit", "Undo Paste"); //Undo code pasting
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    //Change tab size and indent for Kotlin language
    testModifyCodeStyle(kotlincodeLanguage, oldTabSize, newTabSize, oldIndent, newIndent);

    //Verify new tab size and ident is applied to Kotlin class
    editor.open("/app/src/main/java/google/simpleapplication/Children.kt")
      .moveBetween("class Children {","")
      .enterText(origKotlinContent);

    assertEquals(expectedKotlinContent, editor.getCurrentFileContents());
  }

  public void testModifyCodeStyle(String codeLanguage, String oldTabSize, String newTabSize, String oldIndent, String newIndent) throws IOException, InterruptedException {

    mySettingsDialog = guiTest
      .ideFrame()
      .openIdeSettings()
      .selectCodeStylePage(codeLanguage);

    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    mySettingsDialog.clickTab("Tabs and Indents");

    mySettingsDialog.changeTextFieldContent("Tab size:", oldTabSize, newTabSize);
    mySettingsDialog.changeTextFieldContent("Indent:", oldIndent, newIndent);

    mySettingsDialog.clickButton("Apply");
    mySettingsDialog.clickOK();
  }

  @After
  public void closeDialog() {
    if (mySettingsDialog != null) {
      mySettingsDialog.close();
    }
  }

  @NotNull
  private NewJavaClassDialogFixture invokeJavaClass(@NotNull IdeFrameFixture ideFrame) {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    return NewJavaClassDialogFixture.find(ideFrame);
  }

  @NotNull
  private NewKotlinClassDialogFixture invokeKotlinClass(@NotNull IdeFrameFixture ideFrame) {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Kotlin Class/File");
    return NewKotlinClassDialogFixture.find(ideFrame);
  }


  private ProjectViewFixture.PaneFixture PaneClickPath(@NotNull IdeFrameFixture ideFrame) {
    ProjectViewFixture.PaneFixture paneFixture;
    try {
      paneFixture = ideFrame.getProjectView().selectProjectPane();
    }
    catch (WaitTimedOutError timeout) {
      throw new RuntimeException(getUiHierarchy(ideFrame), timeout);
    }

    Wait.seconds(30).expecting("Path is loaded for clicking").until(() -> {
      try {
        paneFixture.clickPath("SimpleApplication", "app", "src", "main", "java", "google.simpleapplication");
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });
    return paneFixture;
  }

  @NotNull
  private static String getUiHierarchy(@NotNull IdeFrameFixture ideFrame) {
    try(
      ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
      PrintStream printBuffer = new PrintStream(outputBuffer)
    ) {
      ideFrame.robot().printer().printComponents(printBuffer);
      return outputBuffer.toString();
    } catch (java.io.IOException ignored) {
      return "Failed to print UI tree";
    }
  }

}
