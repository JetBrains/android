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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.build.attribution.BuildAttributionStateReporter;
import com.android.build.attribution.BuildAttributionStateReporter.State;
import com.android.build.attribution.ui.BuildAttributionUiManager;
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics;
import com.android.repository.Revision;
import com.android.testutils.TestUtils;
import com.android.tools.idea.assistant.AssistSidePanel;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.view.StatefulButton;
import com.android.tools.idea.assistant.view.StatefulButtonMessage;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.truth.Truth;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JEditorPane;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public final class BuildAnalyzerShowActionTest {
  private static final String REVISION = "4.0.0";

  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();
  @Rule
  public final EdtRule edtRule = new EdtRule();

  private WhatsNewBundleCreator myBundleCreator;
  private BuildAttributionUiManager myBuildAttributionUiManagerMock;
  private BuildAttributionStateReporter myBuildAttributionStateReporterMock;
  private ToolWindow myBuildToolWindowMock;
  private GradleBuildInvoker myBuildInvoker;

  @Before
  public void setUp() {
    myBundleCreator = AssistantBundleCreator.EP_NAME.findExtension(WhatsNewBundleCreator.class);

    WhatsNewURLProvider mockUrlProvider = mock(WhatsNewURLProvider.class);

    when(mockUrlProvider.getResourceFileAsStream(myBundleCreator, REVISION)).thenAnswer(new Answer<InputStream>() {
      @Override
      @NotNull
      public InputStream answer(@NotNull InvocationOnMock invocation) {
        return new ByteArrayInputStream(myDefaultResource.getBytes(StandardCharsets.UTF_8));
      }
    });

    File tmpDir = TestUtils.createTempDirDeletedOnExit();
    Path localPath = tmpDir.toPath().resolve("local-4.0.0.xml");
    when(mockUrlProvider.getLocalConfig(REVISION)).thenReturn(localPath);

    myBundleCreator.setURLProvider(mockUrlProvider);
    myBundleCreator.setStudioRevision(Revision.parseRevision(REVISION));
    // Disabling the downloading means it will be loaded from the default resource
    myBundleCreator.setAllowDownload(false);

    myBuildInvoker = myRule.mockProjectService(GradleBuildInvoker.class);
    myBuildAttributionUiManagerMock = mock(BuildAttributionUiManager.class);
    myBuildAttributionStateReporterMock = mock(BuildAttributionStateReporter.class);
    ServiceContainerUtil.registerServiceInstance(myRule.getProject(), BuildAttributionUiManager.class, myBuildAttributionUiManagerMock);
    when(myBuildAttributionUiManagerMock.getStateReporter()).thenReturn(myBuildAttributionStateReporterMock);

    ToolWindowManager toolWindowManagerMock = mock(ToolWindowManager.class);
    myBuildToolWindowMock = mock(ToolWindow.class);
    when(toolWindowManagerMock.getToolWindow(eq(ToolWindowId.BUILD))).thenReturn(myBuildToolWindowMock);
    ServiceContainerUtil.registerComponentInstance(
      myRule.getProject(),
      ToolWindowManager.class,
      toolWindowManagerMock,
      myRule.fixture.getTestRootDisposable()
    );

    myBundleCreator.getBundle(ProjectManager.getInstance().getDefaultProject());

    // Needed since creating AssistSidePanel calls metrics
    WhatsNewMetricsTracker.getInstance().open(myRule.getProject(), false);
  }

  @RunsInEdt
  @Test
  public void testShowBuildAnalyzerButtonWithoutBuild() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA);

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();

    StatefulButton.ActionButton button = getActionButton(statefulButton);
    StatefulButtonMessage message = getActionMessage(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isTrue();
    Truth.assertThat(button.getText()).isEqualTo("Analyze Build");
    Truth.assertThat(message.isVisible()).isTrue();
    List<JEditorPane> textPanes = UIUtil.findComponentsOfType(message, JEditorPane.class);
    Truth.assertThat(cleanUpText(textPanes.get(0).getText())).contains(
      "No report is available.<br>" +
      "Click Analyze Build to build your project<br>" +
      "and open the Build Analyzer with the new report."
    );
  }

  @RunsInEdt
  @Test
  public void testShowBuildAnalyzerButtonWhenResultsExist() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.REPORT_DATA_READY);

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();

    StatefulButton.ActionButton button = getActionButton(statefulButton);
    StatefulButtonMessage message = getActionMessage(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isTrue();
    Truth.assertThat(button.getText()).isEqualTo("Analyze Build");
    Truth.assertThat(message.isVisible()).isTrue();
    List<JEditorPane> textPanes = UIUtil.findComponentsOfType(message, JEditorPane.class);
    Truth.assertThat(cleanUpText(textPanes.get(0).getText())).contains("Previous build's report is available. Click to open.");
  }


  @RunsInEdt
  @Test
  public void testShowBuildAnalyzerButtonWhenBuildStarted() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA_BUILD_RUNNING);

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();

    StatefulButton.ActionButton button = getActionButton(statefulButton);
    StatefulButtonMessage message = getActionMessage(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isFalse();
    Truth.assertThat(button.getText()).isEqualTo("Analyze Build");
    Truth.assertThat(message.isVisible()).isTrue();
    List<JEditorPane> textPanes = UIUtil.findComponentsOfType(message, JEditorPane.class);
    Truth.assertThat(cleanUpText(textPanes.get(0).getText())).contains("Generating build report now.");
  }

  @RunsInEdt
  @Test
  public void testShowBuildAnalyzerButtonWhenBuildFailed() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA_BUILD_FAILED_TO_FINISH);

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();

    StatefulButton.ActionButton button = getActionButton(statefulButton);
    StatefulButtonMessage message = getActionMessage(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isTrue();
    Truth.assertThat(button.getText()).isEqualTo("Analyze Build");
    Truth.assertThat(message.isVisible()).isTrue();
    List<JEditorPane> textPanes = UIUtil.findComponentsOfType(message, JEditorPane.class);
    Truth.assertThat(cleanUpText(textPanes.get(0).getText())).contains(
      "Build failed to complete.<br>" +
      "Resolve any errors and try again."
    );
  }

  @RunsInEdt
  @Test
  public void testButtonWhenAgpVersionTooLow() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.AGP_VERSION_LOW);

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();

    StatefulButton.ActionButton button = getActionButton(statefulButton);
    StatefulButtonMessage message = getActionMessage(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isFalse();
    Truth.assertThat(button.getText()).isEqualTo("Analyze Build");
    Truth.assertThat(message.isVisible()).isTrue();
    List<JEditorPane> textPanes = UIUtil.findComponentsOfType(message, JEditorPane.class);
    Truth.assertThat(cleanUpText(textPanes.get(0).getText())).contains(
      "Android Gradle Plugin 4.0.0 or higher<br>" +
      "is required to use the Build Analyzer."
    );
  }

  @RunsInEdt
  @Test
  public void testShowBuildAnalyzerButtonWhenBuildSuccess() {
    //imitate state change No_Data -> Running -> Success
    MessageBus messageBus = myRule.getProject().getMessageBus();
    Topic<BuildAttributionStateReporter.Notifier> topic = BuildAttributionStateReporter.Companion.getFEATURE_STATE_TOPIC();
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA);

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();

    Truth.assertThat(getActionButton(statefulButton).isVisible()).isTrue();
    Truth.assertThat(getActionButton(statefulButton).isEnabled()).isTrue();
    Truth.assertThat(getActionMessage(statefulButton).isVisible()).isTrue();
    Truth.assertThat(cleanUpText(UIUtil.findComponentsOfType(getActionMessage(statefulButton), JEditorPane.class).get(0).getText()))
      .contains(
        "No report is available.<br>" +
        "Click Analyze Build to build your project<br>" +
        "and open the Build Analyzer with the new report."
      );

    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA_BUILD_RUNNING);
    messageBus.syncPublisher(topic).stateUpdated(State.NO_DATA_BUILD_RUNNING);

    statefulButton = getOpenBuildAnalyzerButton();
    Truth.assertThat(getActionButton(statefulButton).isVisible()).isTrue();
    Truth.assertThat(getActionButton(statefulButton).isEnabled()).isFalse();
    Truth.assertThat(getActionMessage(statefulButton).isVisible()).isTrue();
    Truth.assertThat(cleanUpText(UIUtil.findComponentsOfType(getActionMessage(statefulButton), JEditorPane.class).get(0).getText()))
      .contains("Generating build report now.");

    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.REPORT_DATA_READY);
    messageBus.syncPublisher(topic).stateUpdated(State.REPORT_DATA_READY);

    statefulButton = getOpenBuildAnalyzerButton();
    Truth.assertThat(getActionButton(statefulButton).isVisible()).isTrue();
    Truth.assertThat(getActionButton(statefulButton).isEnabled()).isTrue();
    Truth.assertThat(getActionMessage(statefulButton).isVisible()).isTrue();
    Truth.assertThat(cleanUpText(UIUtil.findComponentsOfType(getActionMessage(statefulButton), JEditorPane.class).get(0).getText()))
      .contains("Previous build's report is available. Click to open.");
  }

  @RunsInEdt
  @Test
  public void testClickTriggerWindowWhenDataExist() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.REPORT_DATA_READY);
    doAnswer(invocation -> {
               invocation.<Runnable>getArgument(0).run();
               return null;
             }
    ).when(myBuildToolWindowMock).show(any());

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();
    StatefulButton.ActionButton button = getActionButton(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isTrue();

    button.doClick();

    verify(myBuildAttributionUiManagerMock).openTab(eq(BuildAttributionUiAnalytics.TabOpenEventSource.WNA_BUTTON));
    verify(myBuildToolWindowMock).show(any(Runnable.class));
  }


  @RunsInEdt
  @Test
  public void testClickTriggerBuildAndOpensWindowAfterBuild() {
    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA);
    doAnswer(invocation -> {
               invocation.<Runnable>getArgument(0).run();
               return null;
             }
    ).when(myBuildToolWindowMock).show(any());
    MessageBus messageBus = myRule.getProject().getMessageBus();
    Topic<BuildAttributionStateReporter.Notifier> topic = BuildAttributionStateReporter.Companion.getFEATURE_STATE_TOPIC();

    StatefulButton statefulButton = getOpenBuildAnalyzerButton();
    StatefulButton.ActionButton button = getActionButton(statefulButton);

    Truth.assertThat(button.isVisible()).isTrue();
    Truth.assertThat(button.isEnabled()).isTrue();

    button.doClick();

    verify(myBuildAttributionUiManagerMock, never()).openTab(eq(BuildAttributionUiAnalytics.TabOpenEventSource.WNA_BUTTON));
    verify(myBuildToolWindowMock, never()).show(any(Runnable.class));
    verify(myBuildInvoker).assemble(any(), eq(TestCompileType.ALL));

    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.NO_DATA_BUILD_RUNNING);
    messageBus.syncPublisher(topic).stateUpdated(State.NO_DATA_BUILD_RUNNING);

    verify(myBuildAttributionUiManagerMock, never()).openTab(eq(BuildAttributionUiAnalytics.TabOpenEventSource.WNA_BUTTON));
    verify(myBuildToolWindowMock, never()).show(any(Runnable.class));

    when(myBuildAttributionStateReporterMock.currentState()).thenReturn(State.REPORT_DATA_READY);
    messageBus.syncPublisher(topic).stateUpdated(State.REPORT_DATA_READY);

    verify(myBuildAttributionUiManagerMock).openTab(eq(BuildAttributionUiAnalytics.TabOpenEventSource.WNA_BUTTON));
    verify(myBuildToolWindowMock).show(any(Runnable.class));
  }

  /**
   * Extracts body, removes all line breaks and extra spaces.
   */
  private static String cleanUpText(String htmlText) {
    return UIUtil.getHtmlBody(htmlText).replaceAll("\\n", " ").replaceAll("\\s+", " ");
  }

  @NotNull
  private StatefulButton getOpenBuildAnalyzerButton() {
    AssistSidePanel sidePanel = new AssistSidePanel(myBundleCreator.getBundleId(), myRule.getProject(), null);
    List<StatefulButton> actionButtons = UIUtil.findComponentsOfType(sidePanel, StatefulButton.class);
    assertEquals(1, actionButtons.size());
    StatefulButton statefulButton = actionButtons.get(0);
    statefulButton.addNotify(); // Trigger subscription to update events
    return statefulButton;
  }

  private static StatefulButtonMessage getActionMessage(StatefulButton statefulButton) {
    return UIUtil.findComponentsOfType(statefulButton, StatefulButtonMessage.class).get(0);
  }

  private static StatefulButton.ActionButton getActionButton(StatefulButton statefulButton) {
    return UIUtil.findComponentsOfType(statefulButton, StatefulButton.ActionButton.class).get(0);
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
    "    version=\"4.0.00\">\n" +
    "  <feature\n" +
    "      name=\"What's New in 4.0\">\n" +
    "    <tutorial\n" +
    "        key=\"whats-new\"\n" +
    "        label=\" What's New in 4.0\"\n" +
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
    "      <step label=\"Build Speed window\">\n" +
    "        <stepElement>\n" +
    "          <section>\n" +
    "            <![CDATA[\n" +
    "            <p>When using Android Studio 4.0 Canary 3 with Android Gradle plugin 4.0.0-alpha03 and higher,\n" +
    "            the <b>Build Speed</b> window is available to help you understand and diagnose issues with your build process," +
    "            such as disabled optimizations and improperly configured tasks. When using Android Studio 4.0 Canary 3 and higher," +
    "            you can open the <b>Build Speed</b> window as follows: </p> <br> " +
    "            <ol> " +
    "            <li>If you haven’t already done so, build your app by selecting <b>Build > Make Project</b> from the menu bar. </li> " +
    "            <li>Select <b>View > Tool Windows > Build</b> from the menu bar </li> " +
    "            <li>In the <b>Build</b> window, open the <b>Build Speed</b> window in one of the following ways: " +
    "                <ul> " +
    "                <li>After Android Studio finishes building your project, click the <b>Build Speed</b> tab. </li>" +
    "                <li>After Android Studio finishes building your project, click the link in the right side of the <b>Build Output</b> window. </li>" +
    "                </ul>" +
    "           </li> " +
    "           </ol>" +
    "           <img src=\"https://d.android.com/studio/releases/assistant/4.0.0/build-speed-chart-wna.png\" width=\"428\" alt=\"Build speed chart\" /> " +
    "           <p><a href=\"https://d.android.com/studio/preview/features#build-attribution\">Learn more</a> </p>\n" +
    "]]>\n" +
    "          </section>\n" +
    "        </stepElement>\n" +
    "        <stepElement>\n" +
    "          <action\n" +
    "              key=\"build.analyzer.show\"\n" +
    "              label=\"Analyze Build\"\n" +
    "              successMessage=\"Build Analyzer was opened with the latest build results!\">\n" +
    "          </action>\n" +
    "        </stepElement>\n" +
    "      </step>" +
    "    </tutorial>\n" +
    "  </feature>\n" +
    "</tutorialBundle>\n";
}
