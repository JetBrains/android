/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.repository.Revision;
import com.android.test.testutils.TestUtils;
import com.android.tools.idea.assistant.AssistSidePanel;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.view.StatefulButton;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.ui.UIUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public final class WhatsNewUpdateFlowTest {
  private static final String REVISION = "3.6.0";

  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private WhatsNewBundleCreator myBundleCreator;

  @Before
  public void mockUrlProvider() throws IOException {
    myRule.getFixture().setTestDataPath(TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString());
    myBundleCreator = AssistantBundleCreator.EP_NAME.findExtension(WhatsNewBundleCreator.class);

    WhatsNewURLProvider mockUrlProvider = Mockito.mock(WhatsNewURLProvider.class);

    // Version 3.6.10 with update buttons, to simulate server-side incrementing for auto-show
    File serverFile = new File(myRule.getFixture().getTestDataPath(), "whatsnewassistant/update-flow.xml");
    Mockito.when(mockUrlProvider.getWebConfig(REVISION)).thenReturn(new URL("file:" + serverFile.getPath()));

    // Version 3.6.00 without update buttons
    Mockito.when(mockUrlProvider.getResourceFileAsStream(myBundleCreator, REVISION)).thenAnswer(new Answer<InputStream>() {
      @Override
      @NotNull
      public InputStream answer(@NotNull InvocationOnMock invocation) {
        return new ByteArrayInputStream(myDefaultResource.getBytes(StandardCharsets.UTF_8));
      }
    });

    Path tmpDir = TestUtils.createTempDirDeletedOnExit();
    Path localPath = tmpDir.resolve("local-3.6.0.xml");
    Mockito.when(mockUrlProvider.getLocalConfig(REVISION)).thenReturn(localPath);

    myBundleCreator.setURLProvider(mockUrlProvider);
    myBundleCreator.setStudioRevision(Revision.parseRevision(REVISION));
    myBundleCreator.setAllowDownload(true);
  }

  @Test
  public void doesNotHaveUpdateFlowButtons() {
    // Disabling the downloading means it will be loaded from the default resource
    myBundleCreator.setAllowDownload(false);
    WhatsNewBundle bundle = myBundleCreator.getBundle(ProjectManager.getInstance().getDefaultProject());

    // Verify correct bundle loaded from default resource
    assertNotNull(bundle);
    assertEquals("3.6.0", bundle.getVersion().toString());
    assertEquals("Test What's New from Class Resource", bundle.getName());

    // Needed since creating AssistSidePanel calls metrics
    WhatsNewMetricsTracker.getInstance().open(myRule.getProject(), false);
    AssistSidePanel sidePanel = new AssistSidePanel(myRule.getProject());
    sidePanel.showBundle(myBundleCreator.getBundleId(), null, null);
    List<StatefulButton.ActionButton> actionButtons = UIUtil.findComponentsOfType(sidePanel, StatefulButton.ActionButton.class);

    assertEquals(0, actionButtons.size());
  }

  @Test
  public void hasUpdateFlowButtons() {
    WhatsNewBundle bundle = myBundleCreator.getBundle(ProjectManager.getInstance().getDefaultProject());

    // Verify correct bundle loaded from "server"
    assertNotNull(bundle);
    assertEquals("3.6.10", bundle.getVersion().toString());
    assertEquals("Test What's New Update Flow from Server", bundle.getName());

    // Needed since creating AssistSidePanel calls metrics
    WhatsNewMetricsTracker.getInstance().open(myRule.getProject(), false);
    AssistSidePanel sidePanel = new AssistSidePanel(myRule.getProject());
    sidePanel.showBundle(myBundleCreator.getBundleId(), null, null);
    List<StatefulButton.ActionButton> actionButtons = UIUtil.findComponentsOfType(sidePanel, StatefulButton.ActionButton.class);

    assertEquals(2, actionButtons.size());
    assertEquals("Update and Restart", actionButtons.get(0).getText());
    assertEquals("Dismiss", actionButtons.get(1).getText());
  }

  @Language("XML")
  private static final String myDefaultResource =
    "<?xml version=\"1.0\"?>" +
    "<tutorialBundle\n" +
    "    name=\"Test What's New from Class Resource\"\n" +
    "    logo=\"core/whats_new_logo.png\"\n" +
    "    resourceRoot=\"/\"\n" +
    "    stepByStep=\"false\"\n" +
    "    hideStepIndex=\"true\"\n" +
    "    version=\"3.6.00\">\n" +
    "  <feature\n" +
    "      name=\"What's New in 3.6\">\n" +
    "    <tutorial\n" +
    "        key=\"whats-new\"\n" +
    "        label=\" What's New in 3.6\"\n" +
    "        icon=\"stable/whats_new_icon.png\"\n" +
    "        remoteLink=\"https://d.android.com/r/studio-ui/whats-new-assistant/canary-release-notes.html\"\n" +
    "        remoteLinkLabel=\"Read in a browser\">\n" +
    "      <description>\n" +
    "        <![CDATA[\n" +
    "          This panel describes some of the new features and behavior changes\n" +
    "          included in this update.\n" +
    "\n" +
    "          <br><br>\n" +
    "          To open this panel again later, select <b>Help &gt; What's New in Android Studio</b>\n" +
    "          from the main menu.\n" +
    "          ]]>\n" +
    "      </description>\n" +
    "      <step label=\"Navigation Editor\">\n" +
    "        <stepElement>\n" +
    "          <section>\n" +
    "            <![CDATA[\n" +
    "           This resource file does not contain the update flow buttons.\n" +
    "\n" +
    "          <br><br><hr><br>\n" +
    "          <em>Last updated 8/22/2019</em>\n" +
    "          <br><br>\n" +
    "           ]]>\n" +
    "          </section>\n" +
    "        </stepElement>\n" +
    "      </step>\n" +
    "    </tutorial>\n" +
    "  </feature>\n" +
    "</tutorialBundle>\n";
}
