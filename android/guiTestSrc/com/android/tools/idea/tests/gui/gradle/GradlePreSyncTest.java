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

import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.ProxySettings;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.ProxySettingsDialogFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.net.HttpConfigurable;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.junit.Assert.*;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradlePreSyncTest extends GuiTestCase {

  // Verifies that the IDE, during sync, asks the user to copy IDE proxy settings to gradle.properties, if applicable.
  // See https://code.google.com/p/android/issues/detail?id=65325
  // Similar to {@link com.android.tools.idea.gradle.util.GradlePropertiesTest#testSetProxySettings} test, but also tests the UI
  // element that is involved.
  @Test @IdeGuiTest
  public void testAddProxyConfigureToPropertyFile() throws IOException {
    myProjectFrame = importSimpleApplication();

    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;
    ideSettings.PROXY_AUTHENTICATION = true;
    ideSettings.PROXY_LOGIN = "test";
    ideSettings.setPlainProxyPassword("testPass");

    ProxySettings ideProxySettings = new ProxySettings(ideSettings);

    GradleProperties properties = new GradleProperties(myProjectFrame.getProject());
    assertNotEquals(ideProxySettings, properties.getHttpProxySettings());

    myProjectFrame.requestProjectSync();

    ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(myRobot);
    assertNotNull(proxySettingsDialog);

    proxySettingsDialog.enableHttpsProxy();
    proxySettingsDialog.clickOk();

    properties = new GradleProperties(myProjectFrame.getProject());

    assertEquals(ideProxySettings, properties.getHttpProxySettings());

    ideProxySettings.setProxyType(ProxySettings.HTTPS_PROXY_TYPE);
    assertEquals(ideProxySettings, properties.getHttpsProxySettings());
  }

  @Test @IdeGuiTest
  public void testDoNotShowProxySettingDialog() throws IOException {
    myProjectFrame = importSimpleApplication();
    PropertiesComponent.getInstance(myProjectFrame.getProject()).setValue("show.do.not.copy.http.proxy.settings.to.gradle", "true");

    File gradlePropertiesPath = new File(myProjectFrame.getProjectPath(), "gradle.properties");
    createIfNotExists(gradlePropertiesPath);

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = "myproxy.test.com";
    ideSettings.PROXY_PORT = 443;

    myProjectFrame.requestProjectSync();

    ProxySettingsDialogFixture proxySettingsDialog = ProxySettingsDialogFixture.find(myRobot);
    assertNotNull(proxySettingsDialog);

    proxySettingsDialog.setDoNotShowThisDialog(true);
    proxySettingsDialog.clickOk();

    myProjectFrame.waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish();

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    myProjectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
  }
}
