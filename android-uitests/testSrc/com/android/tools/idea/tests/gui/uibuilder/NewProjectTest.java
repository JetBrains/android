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
package com.android.tools.idea.tests.gui.uibuilder;

import static com.android.SdkConstants.FN_GRADLE_WRAPPER_UNIX;
import static com.android.tools.idea.tests.gui.instantapp.NewInstantAppTest.verifyOnlyExpectedWarnings;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InferNullityDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.google.common.base.Joiner;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class NewProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  private MessageBusConnection notificationsBusConnection;
  private final List<String> balloonsDisplayed = new ArrayList<>();

  @Before
  public void setup() {
    notificationsBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    notificationsBusConnection.subscribe(Notifications.TOPIC, new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        if (notification.getBalloon() != null) {
          balloonsDisplayed.add(notification.getContent());
        }
      }
    });
  }

  @After
  public void tearDown() {
    notificationsBusConnection.disconnect();
    if (!balloonsDisplayed.isEmpty()) {
      verifyOnlyExpectedWarnings(
        Joiner.on('\n').join(balloonsDisplayed),
        "<html>The following components are ready to .*update.*"
      );
    }
  }

  @Test
  public void testNoWarningsInNewProjects() {
    // Creates a new default project, and checks that if we run Analyze > Inspect Code, there are no warnings.
    // This checks that our (default) project templates are warnings-clean.
    // The test then proceeds to make a couple of edits and checks that these do not generate additional
    // warnings either.
    newProject("Test Application").create(guiTest);

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    //noinspection SpellCheckingInspection
    String inspectionResults = guiTest.ideFrame()
      .actAndWaitForGradleProjectSyncToFinish(
        it ->
          it.getEditor()
            .open("app/build.gradle", EditorFixture.Tab.EDITOR)
            .moveBetween("", "applicationId")
            .enterText("resValue \"string\", \"foo\", \"Typpo Here\"\n")
            .awaitNotification(
              "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
            .performAction("Sync Now")
      )
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    //noinspection SpellCheckingInspection
    verifyOnlyExpectedWarnings(inspectionResults,
      "InspectionViewTree",
      // This warning is from the "foo" string we created in the Gradle resValue declaration above
      "    Android",
      "        Lint",
      "            Performance",
      "                Unused resources",
      "                    build.gradle",
      "                        The resource 'R.string.foo' appears to be unused",
      "            Correctness",
      "                Obsolete Gradle Dependency",
      "                    build.gradle",
      "                        A newer version of .*",
      // This warning is unfortunate. We may want to get rid of it.
      "            Security",
      "                AllowBackup/FullBackupContent Problems",
      "                    AndroidManifest.xml",
      "                        On SDK version 23 and up, your app data will be automatically backed up and .*",

      // This warning is wrong: http://b.android.com/192605
      "            Usability",
      "                Missing support for Firebase App Indexing",
      "                    AndroidManifest.xml",
      "                        App is not indexable by Google Search; consider adding at least one Activity with .*");
  }

  @Test
  public void testInferNullity() {
    // Creates a new default project, adds a nullable API and then invokes Infer Nullity and
    // confirms that it adds nullability annotations.
    newProject("Test Infer Nullity Application").withPackageName("my.pkg").create(guiTest);

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    IdeFrameFixture frame = guiTest.ideFrame();
    EditorFixture editor = frame.getEditor();

    editor
      .open("app/src/main/java/my/pkg/MainActivity.java", EditorFixture.Tab.EDITOR)
      .moveBetween(" ", "}")
      .enterText("if (savedInstanceState != null) ;");

    frame
      .openFromMenu(InferNullityDialogFixture::find, "Analyze", "Infer Nullity...")
      .clickOk();

    // Text will be updated when analysis is done
    Wait.seconds(30).expecting("matching nullness")
      .until(() -> {
        String file = editor.getCurrentFileContents();
        return file.contains("@Nullable Bundle savedInstanceState");
      });
  }

  /**
   * Java source compatibility for older Android API levels was 1.6. For Api 21 and above, it should be at least 1.7 by default.
   */
  @Test
  public void testLanguageLevelForApi21() {
    newProject("Test Application").withBriefNames().withMinSdk(21).create(guiTest);

    // Remove "compileOptions { ... }" block, to force gradle "defaults"
    guiTest.ideFrame().getEditor()
      .open("app/build.gradle")
      .select("(compileOptions.*\n.*\n.*\n.*})")
      .typeText(" ")
      .getIdeFrame()
      .requestProjectSyncAndWaitForSyncToFinish();

    Module appModule = guiTest.ideFrame().getModule("app");

    AndroidVersion version = AndroidModel.get(appModule).getMinSdkVersion();

    assertThat(version.getApiString()).named("minSdkVersion API").isEqualTo("21");
    assertThat(LanguageLevelUtil.getCustomLanguageLevel(appModule).getPreviewLevel()).named("Java language level").isAtLeast(LanguageLevel.JDK_1_7);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(guiTest.ideFrame().getProject());
    assertThat(projectExt.getLanguageLevel()).named("Project Java language level").isSameAs(LanguageLevel.JDK_1_8);
    assertThat(LanguageLevelUtil.getCustomLanguageLevel(appModule)).named("Gradle Java language level in module " + appModule.getName())
      .isAtLeast(LanguageLevel.JDK_1_7);
  }

  @Test
  public void testGradleWrapperIsExecutable() {
    Assume.assumeTrue("Is Unix", SystemInfo.isUnix);
    newProject("Test Application").withBriefNames().create(guiTest);

    assertTrue(guiTest.getProjectPath(FN_GRADLE_WRAPPER_UNIX).canExecute());
  }

  /**
   * Verifies studio adds latest support library while DND layouts (RecyclerView)
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c4fafea8-9560-4c40-92c1-58b72b2caaa0
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new project with empty activity with min SDK 26
   *   2. Drag and Drop RecyclerView (Verify)
   *   Verify:
   *   Dependency should be added to build.gradle with latest version from maven
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void latestSupportLibraryWhileDndLayouts() {
    IdeFrameFixture ideFrameFixture = newProject("Test Application").withMinSdk(26).create(guiTest);

    ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .waitForRenderToFinish()
      .dragComponentToSurface("Containers", "RecyclerView");

    MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickOk();

    String contents = ideFrameFixture.getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();

    assertThat(contents).contains("implementation 'androidx.recyclerview:recyclerview:");
  }

  @Test
  public void androidXmlFormatting() {
    newProject("P").create(guiTest);

    String actualXml = guiTest.getProjectFileText("app/src/main/res/layout/activity_main.xml");

    @Language("XML")
    String expectedXml =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\"\n" +
      "    tools:context=\".MainActivity\">\n" +
      "\n" +
      "    <TextView\n" +
      "        android:layout_width=\"wrap_content\"\n" +
      "        android:layout_height=\"wrap_content\"\n" +
      "        android:text=\"Hello World!\"\n" +
      "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
      "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
      "        app:layout_constraintRight_toRightOf=\"parent\"\n" +
      "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
      "\n" +
      "</androidx.constraintlayout.widget.ConstraintLayout>";

    assertThat(actualXml).isEqualTo(expectedXml);
  }

  @Test
  public void hasProjectNameInGradleSettings() {
    newProject("P").create(guiTest);
    assertThat(guiTest.getProjectFileText("settings.gradle")).contains("rootProject.name = \"P\"");
  }

  @NotNull
  private static NewProjectDescriptor newProject(@NotNull String name) {
    return new NewProjectDescriptor(name);
  }
}
