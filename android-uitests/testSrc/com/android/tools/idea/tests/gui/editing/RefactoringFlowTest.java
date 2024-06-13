/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewJavaClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture.ConflictsDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

/** Tests the editing flow of refactoring */
@RunWith(GuiTestRemoteRunner.class)
public class RefactoringFlowTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b0534243-311d-40a7-996d-08347ba918df
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project,
   *   2. Create new 'Person' class and populate it with sample code
   *   3. Go to MyActivity.java and create 'Person' object and use it in the code
   *   4. Inside 'MyActivity.java', Right click on "Person" object > Refactor > Rename
   *   5. Change the name of "Person" object to "Student", enter. (Verify 1)
   *   6. "Select all" and click "OK". (Verify 2)
   *   7. Right click on MyActivity > Refactor > Rename
   *   8. Rename MyActivity to FirstActivity > Enter (Verify 3)
   *   Verify:
   *   1. "Rename Variables" dialog will open.
   *   2. Person class and its all references changed to the new name "Student".
   *   3. MainActivity name changed to FirstActivity in Manifest,activity_main.xml file
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testRefactorRename() throws IOException, InterruptedException {

    String newFileName = "FirstActivity";
    String newObjectName = "Student";
    guiTest.importSimpleApplication();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    PaneClickPath(ideFrame);

    // Create a 'Person' class in the library and check the build.
    invokeJavaClass(ideFrame).enterName("Person").clickOk();
    editor.open("/app/src/main/java/google/simpleapplication/Person.java")
      .moveBetween("public class Person {","")
      .enterText("\nString name;\nint age;\n\npublic void setAge(int age) {\nthis.age = age;\n}\n");

    guiTest.robot().waitForIdle();
    editor.open("/app/src/main/java/google/simpleapplication/MyActivity.java")
      .moveBetween("public class MyActivity extends Activity {", "")
      .enterText("\nPerson person;\n")
      .moveBetween("setContentView(R.layout.activity_my);", "")
      .enterText("\n// create a person object.\nperson = new Person();\n\npassPersonObject(person);\n}\nprivate void passPersonObject(Person person) {\nperson.setAge(5);\n");

    editor.moveBetween("Pers", "on person");
    editor.moveBetween("Pers", "on person"); //To reduce flakiness

    guiTest.robot().waitForIdle();
    //Doing Invoking menu path twice to display refactor dialog box
    ideFrame.invokeMenuPath("Refactor", "Rename\u2026");
    guiTest.robot().waitForIdle();
    ideFrame.invokeMenuPath("Refactor", "Rename\u2026");

    // Rename as action_settings, which is already defined
    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName(newObjectName);
    refactoringDialog.clickRefactor();

    assertThat(refactoringDialog.getTitle()).contains("Rename");
    refactoringDialog.clickSelectAll();
    refactoringDialog.clickOk();

    doFileNameRefactor(newFileName);

    ideFrame.getProjectView().assertFilesExist(
      "app/src/main/java/google/simpleapplication/"+newFileName+".java"
    );

    ideFrame.focus();
    Wait.seconds(5)
      .expecting("IDE Frame to be active again")
      .until(() ->
               GuiQuery.getNonNull(() -> ideFrame.target().hasFocus())
      );

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();

    String activityContents = guiTest.getProjectFileText("/app/src/main/java/google/simpleapplication/"+newFileName+".java");
    assertThat(activityContents).contains("student = new Student();");
    assertThat(activityContents).contains("private void passPersonObject(Student student) {");

    ideFrame.getProjectView().assertFilesExist(
      "/app/src/main/java/google/simpleapplication/"+newObjectName+".java"
    );

    String manifestContents = guiTest.getProjectFileText("app/src/main/AndroidManifest.xml");
    assertThat(manifestContents).contains("android:name=\"."+newFileName+"\"");

    String activityFileContents = guiTest.getProjectFileText("app/src/main/res/layout/activity_my.xml");
    assertThat(activityFileContents).contains("tools:context=\"."+newFileName+"\"");
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testResourceConflict() throws IOException {
    // Try to rename a resource to an existing resource; check that
    // you get a warning in the conflicts dialog first
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/strings.xml");
    editor.moveBetween("hello", "_world");
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename\u2026");

    // Rename as action_settings, which is already defined
    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("action_settings");
    refactoringDialog.clickRefactor();

    ConflictsDialogFixture conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    assertThat(conflictsDialog.getText()).contains("Resource @string/action_settings already exists");
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();
  }

  @NotNull
  private NewJavaClassDialogFixture invokeJavaClass(@NotNull IdeFrameFixture ideFrame) {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    return NewJavaClassDialogFixture.find(ideFrame);
  }

  private ProjectViewFixture.PaneFixture PaneClickPath(@NotNull IdeFrameFixture ideFrame) {
    ProjectViewFixture.PaneFixture paneFixture;
    try {
      paneFixture = ideFrame.getProjectView().selectProjectPane();
    } catch(WaitTimedOutError timeout) {
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

  private void doFileNameRefactor(String newFileName) throws InterruptedException {
    ProjectViewFixture.PaneFixture paneFixture = PaneClickPath(guiTest.ideFrame());

    guiTest.waitForBackgroundTasks();
    paneFixture.clickPath("SimpleApplication", "app", "src", "main", "java", "google.simpleapplication", "MyActivity")
      .invokeMenuPath("Refactor", "Rename\u2026");

    RenameFileDialogFixture.find(guiTest.ideFrame())
      .enterText(newFileName)
      .clickRefactor();

    guiTest.robot().waitForIdle();
  }
}
