/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNewGoogleActivitiesTest {
  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private IdeFrameFixture ideFrame;
  private EditorFixture editorFixture;
  protected static final String EMPTY_VIEWS_ACTIVITY_TEMPLATE = "Empty Views Activity";

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_VIEWS_ACTIVITY_TEMPLATE, Language.Kotlin, BuildConfigurationLanguageForNewProject.KTS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();
    ideFrame.clearNotificationsPresentOnIdeFrame();

    editorFixture = ideFrame.getEditor();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /***
   * Google Pay Views Activity
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 83ee723b-f45d-4efd-9f20-b9d274fe7ff7
   * <pre>
   * Procedure:
   * 1. Create an 'Empty Activity' Project
   * 2. Create 'Google Pay Activity' by first clicking on 'app' (Project View panel) and then clicking on 'File =&gt; New =&gt; Google =&gt;  Google Pay Views Activity
   * Note: Select checkbox making it a launcher activity'
   * 3. Update the theme in AndroidManifest.xml file for newly added activity as android:theme="@style/Theme.AppCompat"
   * 4. Build the project
   *
   * Verification:
   * 1. Verify the newly created activity and its dependencies
   * 2. Build should successful
   * </pre>
   */
  @Test
  public void testMapViews() {
    // Creating a new activity.
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    NewActivityWizardFixture activityWizard = NewActivityWizardFixture
      .createActivity(ideFrame, "Google", "Google Maps Views Activity");
    activityWizard.getConfigureActivityStep("Google Maps Views Activity")
      .setSourceLanguage("Kotlin")
      .selectLauncherActivity();
    activityWizard.clickFinishAndWaitForSyncToFinish();

    // Update the manifest.xml

    editorFixture.open("/app/src/main/AndroidManifest.xml");
    editorFixture.select(".*Theme\\.(MyApplication)")
      .enterText("AppCompat");

    String mapsActivityFileContents = editorFixture.open("/app/src/main/java/com/google/myapplication/MapsActivity.kt")
      .getCurrentFileContents();

    assertThat(mapsActivityFileContents).contains("class MapsActivity");
    assertThat(mapsActivityFileContents).contains("GoogleMap");

    String buildFileContents = editorFixture.open("app/build.gradle.kts")
      .getCurrentFileContents();

    assertThat(buildFileContents).contains("implementation(libs.play.services.maps)");

    String versionCatalogFileContents = editorFixture.open("gradle/libs.versions.toml")
      .getCurrentFileContents();
    assertThat(versionCatalogFileContents).contains("play-services-maps = { group = \"com.google.android.gms\", name = \"play-services-maps\", version.ref = \"playServicesMaps\" }");

    assertThat(ideFrame.invokeProjectMake(Wait.seconds(300)).isBuildSuccessful())
      .isTrue();
  }

  /***
   * Google Pay Views Activity
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 83ee723b-f45d-4efd-9f20-b9d274fe7ff7
   * <pre>
   * Procedure:
   * 1. Create an 'Empty Activity' Project
   * 2. Create 'Google Pay Activity' by first clicking on 'app' (Project View panel) and then clicking on 'File =&gt; New =&gt; Google =&gt;  Google Pay Views Activity
   * Note: Select checkbox making it a launcher activity'
   * 3. Update the theme in AndroidManifest.xml file for newly added activity as android:theme="@style/Theme.AppCompat"
   * 4. Build the project
   *
   * Verification:
   * 1. Verify the newly created activity and its dependencies
   * 2. Build should successful
   * </pre>
   */
  @Test
  public void testPayViews() {
    NewActivityWizardFixture activityWizard = NewActivityWizardFixture
      .createActivity(ideFrame, "Google", "Google Pay Views Activity");
    activityWizard.getConfigureActivityStep("Google Pay Views Activity")
      .selectLauncherActivity()
      .setSourceLanguage("Kotlin");
    activityWizard.clickFinishAndWaitForSyncToFinish();

    editorFixture.open("/app/src/main/AndroidManifest.xml");
    editorFixture.select(".*Theme\\.(MyApplication)")
      .enterText("AppCompat");

    ideFrame.getProjectView()
      .assertFilesExist("/app/src/main/java/com/google/myapplication/Constants.kt");

    String payActivityFileContents = editorFixture.open("/app/src/main/java/com/google/myapplication/CheckoutActivity.kt")
      .getCurrentFileContents();

    assertThat(payActivityFileContents).contains("class CheckoutActivity");
    assertThat(payActivityFileContents).contains("setGooglePayAvailable");
    assertThat(payActivityFileContents).contains("requestPayment");

    String buildFileContents = editorFixture.open("app/build.gradle.kts")
      .getCurrentFileContents();

    assertThat(buildFileContents).contains("implementation(libs.play.services.wallet)");

    String versionCatalogFileContents = editorFixture.open("gradle/libs.versions.toml")
      .getCurrentFileContents();
    assertThat(versionCatalogFileContents).contains("play-services-wallet = { group = \"com.google.android.gms\", name = \"play-services-wallet\", version.ref = \"playServicesWallet\" }");

    assertThat(ideFrame.invokeProjectMake(Wait.seconds(300)).isBuildSuccessful())
      .isTrue();
  }
}
