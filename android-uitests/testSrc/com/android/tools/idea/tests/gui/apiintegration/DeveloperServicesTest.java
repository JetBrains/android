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
package com.android.tools.idea.tests.gui.apiintegration;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.NetUtils;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.google.common.truth.Truth.assertThat;
import static java.util.regex.Pattern.DOTALL;

@Ignore("https://code.google.com/p/android/issues/detail?id=228111")
@RunWith(GuiTestRunner.class)
public class DeveloperServicesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @NotNull
  private VirtualFile getBuildGradleFile() {
    return guiTest.ideFrame().getEditor().open("app/build.gradle").getCurrentFile();
  }

  @NotNull
  private String getBuildGradleFileContents() {
    return guiTest.ideFrame().getEditor().open("app/build.gradle").getCurrentFileContents();
  }

  private boolean dependencyIsPresent(@NotNull GradleBuildModel buildModel, @NotNull String dependencyToFind) {
    DependenciesModel dependenciesModel = buildModel.dependencies();
    if (dependenciesModel != null) {
      for (ArtifactDependencyModel dependency : dependenciesModel.artifacts(COMPILE)) {
        String notation = dependency.compactNotation().value();
        if (notation.contains(dependencyToFind)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Verifies that Developer Services dependencies can be added and removed in app build gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581655
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import a project.
   *   2. Open File > Project Structure
   *   3. Developer Services option is listed and contains the following options: Ads, Authentication, Notifications.
   *   4. Enable all services.
   *   5. Click OK button.
   *   6. Open app/build.gradle
   *   7. Dependencies are added for each of the enabled services.
   *   8. Open File > Structure
   *   9. Disable services for Ads, Authentication, Notification.
   *   10. Click OK button.
   *   11. Open app/build.gradle
   *   12. Dependencies are removed for the services.
   *   Verify:
   *   Toggling Developer Services dependencies reflects in app build gradle file.
   *   </pre>
   * <p>
   *   The test checks both the presence of the string and that the build.gradle file is written correctly by parsing it.
   */
  @RunIn(TestGroup.QA)
  @Test
  public void toggleDevServicesDependencies() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    ideFrameFixture.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .setServiceEnabled("Ads", true)
      .setServiceEnabled("Authentication", true)
      .setServiceEnabled("Notifications", true)
      .clickOk();

    String gradleFileContents = getBuildGradleFileContents();
    assertThat(gradleFileContents).contains("compile 'com.google.android.gms:play-services-ads:");
    assertThat(gradleFileContents).contains("compile 'com.google.android.gms:play-services-auth:");
    assertThat(gradleFileContents).contains("compile 'com.google.android.gms:play-services-gcm:");

    // Parsing of the Gradle file needs to be done in a ReadAction.
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(getBuildGradleFile(), ideFrameFixture.getProject());

        assertThat(dependencyIsPresent(buildModel, "play-services-ads")).isTrue();
        assertThat(dependencyIsPresent(buildModel, "play-services-auth")).isTrue();
        assertThat(dependencyIsPresent(buildModel, "play-services-gcm")).isTrue();
      }
    });

    // Disable the services.
    ideFrameFixture.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .setServiceEnabled("Ads", false)
      .setServiceEnabled("Authentication", false)
      .setServiceEnabled("Notifications", false)
      .clickOk();

    gradleFileContents = getBuildGradleFileContents();
    assertThat(gradleFileContents).doesNotContain("compile 'com.google.android.gms:play-services-ads:");
    assertThat(gradleFileContents).doesNotContain("compile 'com.google.android.gms:play-services-auth:");
    assertThat(gradleFileContents).doesNotContain("compile 'com.google.android.gms:play-services-gcm:");

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(getBuildGradleFile(), ideFrameFixture.getProject());

        assertThat(dependencyIsPresent(buildModel, "play-services-ads")).isFalse();
        assertThat(dependencyIsPresent(buildModel, "play-services-auth")).isFalse();
        assertThat(dependencyIsPresent(buildModel, "play-services-gcm")).isFalse();
      }
    });
  }

  /**
   * Common code for the Google Cloud Module tests.
   *
   * @param moduleSubtype The value to choose from the "Module Type" drop down.
   * @param sourceFileName The basename of the generated Java file.
   * @param checkForOwnerDomainAndOwnerName true if the test should check for ownerDomain and ownerName Api parameters.
   */
  private void createGoogleCloudModule(
    @NotNull String moduleSubtype, @NotNull String sourceFileName, boolean checkForOwnerDomainAndOwnerName) throws Exception {
    final String moduleName = "backend";
    final String packageName = "com.google.sampleapp.backend";
    final String domainName = "backend.sampleapp.google.com";

    String filePath = String.format("%s/src/main/java/%s/%s", moduleName, packageName.replace(".", "/"), sourceFileName);

    ProjectViewFixture.PaneFixture pane = guiTest.importSimpleApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Google Cloud Module")
      .clickNextToStep("New Google Cloud Module")
      .chooseModuleSubtype(moduleSubtype)
      .setModuleName(moduleName)
      .setPackageName("com.google.sampleapp.backend")
      .chooseClientModule("app (google.simpleapplication)")
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .getProjectView()
      .selectAndroidPane();
    assertThat(pane.hasModuleRootNode(moduleName)).isTrue();

    String fileContents = guiTest.ideFrame()
      .getEditor()
      .open(filePath)
      .getCurrentFileContents();

    assertThat(fileContents).contains("package " + packageName + ";");
    if (checkForOwnerDomainAndOwnerName) {
      assertThat(fileContents).contains("ownerDomain = \"" + domainName + "\"");
      assertThat(fileContents).contains("ownerName = \"" + domainName + "\"");
    }

    ExecutionToolWindowFixture runToolWindow = guiTest.ideFrame()
      .runNonAndroidApp(moduleName)
      .getRunToolWindow();

    runToolWindow
      .findContent(moduleName)
      .waitForOutput(new PatternTextMatcher(Pattern.compile(".*Dev App Server is now running.*", DOTALL)), 120);

    assertThat(NetUtils.canConnectToRemoteSocket(NetUtils.getLocalHostString(), 8080)).isTrue();

    runToolWindow.findContent(moduleName).stop();
  }

  /**
   * Verifies that App Engine Java Servlet Module backend is added to Android Studio and the backend starts successfully through localhost.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14583297
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. File > New > New Module
   *   2. Select "Google Cloud Module"
   *   3. For Module Type, select "App Engine Java Servlet Module"
   *   4. For Module Name, enter in "backend"
   *   5. For Package Name, enter in "com.google.sampleapp.backend"
   *   6. For Client Module, select "app (google.simpleapplication)"
   *   7. Click "Finish" button. (Verify A)
   *   8. Edit run configurations to select "backend" (Verify B)
   *   Verify:
   *   A: The values entered into Module Name and Package Name fields are used in the class.
   *      At the root of the Project Navigator, there is a module called "backend"
   *   B: Backend starts successfully through telnet localhost 8080.
   *      The test gets a fixture for the output window of the Run tool and waits for the text "Dev App Server is now running" to appear,
   *      which shows that the server is running. After that it verifies the test can connect to port 8080 on localhost.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void createAppEngineJavaServletModule() throws Exception {
    createGoogleCloudModule("App Engine Java Servlet Module", "MyServlet.java", false);
  }

  /**
   * Verifies that App Engine Java Endpoints Module backend is added to Android Studio and the backend starts successfully through localhost.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14583301
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. File > New > New Module
   *   2. Select "Google Cloud Module"
   *   3. For Module Type, select "App Engine Java Endpoints Module"
   *   4. For Module Name, enter in "backend"
   *   5. For Package Name, enter in "com.google.sampleapp.backend"
   *   6. For Client Module, select "app (google.simpleapplication)"
   *   7. Click "Finish" button. (Verify A)
   *   8. Edit run configurations to select "backend" (Verify B)
   *   Verify:
   *   A: The values entered into Module Name and Package Name fields are used in the class.
   *      At the root of the Project Navigator, there is a module called "backend"
   *   B: Backend starts successfully through telnet localhost 8080.
   *      The test gets a fixture for the output window of the Run tool and waits for the text "Dev App Server is now running" to appear,
   *      which shows that the server is running. After that it verifies the test can connect to port 8080 on localhost.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void createAppEngineJavaEndpointsModule() throws Exception {
    createGoogleCloudModule("App Engine Java Endpoints Module", "MyEndpoint.java", true);
  }

  /**
   * Verifies that App Engine Backend with GCM is added to Android Studio and the backend starts successfully through localhost.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14583302
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. File > New > New Module
   *   2. Select "Google Cloud Module"
   *   3. For Module Type, select "App Engine Backend with Google Cloud Messaging"
   *   4. For Module Name, enter in "backend"
   *   5. For Package Name, enter in "com.google.sampleapp.backend"
   *   6. For Client Module, select "app (google.simpleapplication)"
   *   7. Click "Finish" button. (Verify A)
   *   8. Edit run configurations to select "backend" (Verify B)
   *   Verify:
   *   A: The values entered into Module Name and Package Name fields are used in the class.
   *      At the root of the Project Navigator, there is a module called "backend"
   *   B: Backend starts successfully through telnet localhost 8080.
   *      The test gets a fixture for the output window of the Run tool and waits for the text "Dev App Server is now running" to appear,
   *      which shows that the server is running. After that it verifies the test can connect to port 8080 on localhost.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void createAppEngineBackendWithCloudMessaging() throws Exception {
    createGoogleCloudModule("App Engine Backend with Google Cloud Messaging", "MessagingEndpoint.java", true);
  }
}