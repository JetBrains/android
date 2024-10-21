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

import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.backupGlobalGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.getUserGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.restoreGlobalGradlePropertiesFile;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.IdeGradleProxySettingsBridge;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.ProxySettingsDialogFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.net.HttpConfigurable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class GradlePreSyncTest {
  @Nullable private File myBackupProperties;
  @NotNull private HttpConfigurable myBackupIdeSettings;

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Generate a backup copy of user gradle.properties since some tests in this class make changes to the proxy that could
   * cause other tests to use an incorrect configuration.
   */
  @Before
  public void backupPropertiesFile() {
    myBackupProperties = backupGlobalGradlePropertiesFile();
    myBackupIdeSettings = new HttpConfigurable();
    myBackupIdeSettings.loadState(HttpConfigurable.getInstance());
  }

  /**
   * Restore user gradle.properties file content to what it had before running the tests, or delete if it did not exist.
   */
  @After
  public void restorePropertiesFile() {
    restoreGlobalGradlePropertiesFile(myBackupProperties);
    HttpConfigurable.getInstance().loadState(myBackupIdeSettings);
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

    IdeGradleProxySettingsBridge ideProxySettings = new IdeGradleProxySettingsBridge(ideSettings);

    File userPropertiesFile = getUserGradlePropertiesFile();
    GradleProperties properties = new GradleProperties(userPropertiesFile);
    assertNotEquals(ideProxySettings, properties.getHttpProxySettings());

    guiTest.ideFrame().actAndWaitForGradleProjectSyncToFinish(it -> {
      try {
        it.requestProjectSync();
        ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());

        proxySettingsDialog.enableHttpsProxy();
        proxySettingsDialog.clickYes();

        GradleProperties properties1 = new GradleProperties(userPropertiesFile);

        assertEquals(ideProxySettings, properties1.getHttpProxySettings());

        ideProxySettings.setProxyType(IdeGradleProxySettingsBridge.HTTPS_PROXY_TYPE);
        assertEquals(ideProxySettings, properties1.getHttpsProxySettings());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  // Verifies that the IDE, during sync, asks the user to verify proxy settings of gradle.properties, when proxy auto configuration is
  // used. (see b/135102054)
  @Test
  public void testAddProxyConfigureToPropertyFilePAC() throws IOException {
    guiTest.importSimpleApplication();

    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = false;
    ideSettings.USE_PROXY_PAC = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;
    ideSettings.PROXY_AUTHENTICATION = true;
    ideSettings.setProxyLogin("test");
    ideSettings.setPlainProxyPassword("idePass");
    File userPropertiesFile = getUserGradlePropertiesFile();
    String gradlePass = "gradlePass";
    {
      GradleProperties properties = new GradleProperties(userPropertiesFile);
      properties.getProperties().setProperty("systemProp.http.proxyPassword", gradlePass);
      properties.getProperties().setProperty("systemProp.https.proxyPassword", gradlePass);
      properties.save();

      guiTest.ideFrame().actAndWaitForGradleProjectSyncToFinish(it -> {
        it.requestProjectSync();
        ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());

        // Verify settings match with gradle properties
        IdeGradleProxySettingsBridge httpSettings = properties.getHttpProxySettings();
        assertEquals(nullToEmpty(httpSettings.getHost()), proxySettingsDialog.getHttpHost());
        assertEquals(httpSettings.getPort(), proxySettingsDialog.getHttpPort());
        assertEquals(nullToEmpty(httpSettings.getUser()), proxySettingsDialog.getHttpUser());
        assertEquals(nullToEmpty(httpSettings.getExceptions()), proxySettingsDialog.getHttpExceptions());

        IdeGradleProxySettingsBridge httpsSettings = properties.getHttpsProxySettings();
        assertEquals(nullToEmpty(httpsSettings.getHost()), proxySettingsDialog.getHttpsHost());
        assertEquals(httpsSettings.getPort(), proxySettingsDialog.getHttpsPort());
        assertEquals(nullToEmpty(httpsSettings.getUser()), proxySettingsDialog.getHttpsUser());
        assertEquals(nullToEmpty(httpsSettings.getExceptions()), proxySettingsDialog.getHttpsExceptions());

        proxySettingsDialog.clickYes();
      });
    }
    {
      // Confirm password is not changed
      GradleProperties properties = new GradleProperties(userPropertiesFile);
      IdeGradleProxySettingsBridge httpSettings = properties.getHttpProxySettings();
      assertEquals("HTTP proxy password was changed", gradlePass, httpSettings.getPassword());
      IdeGradleProxySettingsBridge httpsSettings = properties.getHttpsProxySettings();
      assertEquals("HTTPS proxy password was changed", gradlePass, httpsSettings.getPassword());
    }
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

    guiTest.ideFrame().actAndWaitForGradleProjectSyncToFinish(it -> {
      it.requestProjectSync();

      ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());

      proxySettingsDialog.setDoNotShowThisDialog(true);
      proxySettingsDialog.clickYes();
    });

    // Force a change on the proxy, otherwise the project sync may be ignored.
    ideSettings.PROXY_HOST = "myproxy2.test.com";

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    guiTest.ideFrame().requestProjectSyncAndWaitForSyncToFinish();
  }

  @Test
  public void testShowProxySettingDialogPAC() throws IOException {
    guiTest.importSimpleApplication();
    PropertiesComponent.getInstance(guiTest.ideFrame().getProject()).setValue("show.do.not.copy.http.proxy.settings.to.gradle", "true");

    File gradlePropertiesPath = new File(guiTest.ideFrame().getProjectPath(), "gradle.properties");
    createIfNotExists(gradlePropertiesPath);

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = false;
    ideSettings.USE_PROXY_PAC = true;


    // ProxySettingsDialog should be shown when no configuration is done
    guiTest.ideFrame().requestProjectSync();
    ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());
    proxySettingsDialog.clickNo();

    // Should be shown when port is not set
    File userPropertiesFile = getUserGradlePropertiesFile();
    GradleProperties gradleProperties = new GradleProperties(userPropertiesFile);
    Properties properties = gradleProperties.getProperties();
    properties.setProperty("systemProp.http.proxyHost", "myproxy.test.com");
    gradleProperties.save();
    guiTest.ideFrame().requestProjectSync();
    proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());
    proxySettingsDialog.clickNo();

    // Should be shown when host is not set
    properties.remove("systemProp.http.proxyHost");
    properties.setProperty("systemProp.http.proxyPort", "443");
    gradleProperties.save();
    guiTest.ideFrame().requestProjectSync();
    proxySettingsDialog = ProxySettingsDialogFixture.find(guiTest.robot());
    proxySettingsDialog.clickNo();

    // Should not be shown when host and port are set
    properties.setProperty("systemProp.http.proxyHost", "myproxy.test.com");
    gradleProperties.save();
    // No dialog should be shown, test will timeout otherwise
    guiTest.ideFrame().requestProjectSyncAndWaitForSyncToFinish();
  }
}
