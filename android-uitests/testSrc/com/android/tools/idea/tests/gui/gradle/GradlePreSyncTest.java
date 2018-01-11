/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.ProxySettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ProxySettingsDialogFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.GradleProperties.getUserGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.restoreGlobalGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.backupGlobalGradlePropertiesFile;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradlePreSyncTest {
  @Nullable private File myBackupProperties;

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  /**
   * Generate a backup copy of user gradle.properties since some tests in this class make changes to the proxy that could
   * cause other tests to use an incorrect configuration.
   */
  @Before
  public void backupPropertiesFile() {
    myBackupProperties = backupGlobalGradlePropertiesFile();
  }

  /**
   * Restore user gradle.properties file content to what it had before running the tests, or delete if it did not exist.
   */
  @After
  public void restorePropertiesFile() {
    restoreGlobalGradlePropertiesFile(myBackupProperties);
  }

  // Verifies that the IDE, during sync, asks the user to copy IDE proxy settings to gradle.properties, if applicable.
  // See https://code.google.com/p/android/issues/detail?id=65325
  // Similar to {@link com.android.tools.idea.gradle.util.GradlePropertiesTest#testSetProxySettings} test, but also tests the UI
  // element that is involved.
  @Test
  public void testAddProxyConfigureToPropertyFile() throws IOException {
    guiTest.importSimpleApplication();

    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;
    ideSettings.PROXY_AUTHENTICATION = true;
    ideSettings.setProxyLogin("test");
    ideSettings.setPlainProxyPassword("");

    ProxySettings ideProxySettings = new ProxySettings(ideSettings);

    File userPropertiesFile = getUserGradlePropertiesFile();
    GradleProperties properties = new GradleProperties(userPropertiesFile);
    assertNotEquals(ideProxySettings, properties.getHttpProxySettings());

    guiTest.ideFrame().requestProjectSync();

    ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());

    proxySettingsDialog.enableHttpsProxy();
    proxySettingsDialog.clickOk();

    properties = new GradleProperties(userPropertiesFile);

    assertEquals(ideProxySettings, properties.getHttpProxySettings());

    ideProxySettings.setProxyType(ProxySettings.HTTPS_PROXY_TYPE);
    assertEquals(ideProxySettings, properties.getHttpsProxySettings());

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  @Test
  public void testDoNotShowProxySettingDialog() throws IOException {
    guiTest.importSimpleApplication();
    PropertiesComponent.getInstance(guiTest.ideFrame().getProject()).setValue("show.do.not.copy.http.proxy.settings.to.gradle", "true");

    File gradlePropertiesPath = new File(guiTest.ideFrame().getProjectPath(), "gradle.properties");
    createIfNotExists(gradlePropertiesPath);

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = "myproxy.test.com";
    ideSettings.PROXY_PORT = 443;

    guiTest.ideFrame().requestProjectSync();

    ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());

    proxySettingsDialog.setDoNotShowThisDialog(true);
    proxySettingsDialog.clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish();

    // Force a change on the proxy, otherwise the project sync may be ignored.
    ideSettings.PROXY_HOST = "myproxy2.test.com";

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
  }
}
