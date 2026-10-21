/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.diagnostics;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.error.AndroidStudioErrorReportSubmitter;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestActionEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioSystemHealthMonitorUtilitiesTest extends LightPlatformTestCase {

  public void testGetActionName() {
    // normal class in our packages should yield simple name
    assertEquals("AndroidStudioSystemHealthMonitorTest", AndroidStudioSystemHealthMonitor.getActionName(AndroidStudioSystemHealthMonitorTest.class, new Presentation("Foo")));
    // ExecutorAction class should yield simple name plus presentation text.
    assertEquals("ExecutorAction#Run", AndroidStudioSystemHealthMonitor.getActionName(ExecutorAction.class, new Presentation("Run")));
    // Anonymous inner-class should yield name of enclosing class.
    assertEquals("AnAction@AndroidStudioSystemHealthMonitorUtilitiesTest", AndroidStudioSystemHealthMonitor.getActionName(new AnAction(){
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {

      }
    }.getClass(), new Presentation("Foo")));
    // class outside our packages should yield full class name.
    assertEquals("java.lang.String", AndroidStudioSystemHealthMonitor.getActionName(String.class, new Presentation("Foo")));
  }

  // Regression test for b/130834409.
  @SuppressWarnings("UnstableApiUsage")
  public void testAndroidErrorSubmitter() {
    // Our error submitter should be registered.
    assertThat(ErrorReportSubmitter.EP_NAME.findExtension(AndroidStudioErrorReportSubmitter.class)).isNotNull();
    // Platform exceptions should be handled by our error submitter.
    RuntimeException exception = new RuntimeException();
    assertThat(DefaultIdeaErrorLogger.findSubmitter(exception, /*pluginId*/ null)).isInstanceOf(AndroidStudioErrorReportSubmitter.class);
    // Ditto for plugin exceptions (at least for our own plugins).
    var androidPlugin = Objects.requireNonNull(PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.android")));
    assertThat(DefaultIdeaErrorLogger.findSubmitter(exception, androidPlugin)).isInstanceOf(AndroidStudioErrorReportSubmitter.class);
  }

  // Tests that AnAction invocations are logged as Studio usage events.
  public void testActionInvocationMetricsLogged() throws Exception {
    try (TestUsageTracker usageTracker = new com.android.tools.analytics.TestUsageTracker(new VirtualTimeScheduler())) {
      UsageTracker.setWriterForTest(usageTracker);

      var action = new EmptyAction();
      var event = TestActionEvent.createTestEvent();
      ActionUtil.performAction(action, event);

      var usages = usageTracker.getUsages().stream()
        .filter(u -> u.getStudioEvent().getKind() == AndroidStudioEvent.EventKind.STUDIO_UI_ACTION_STATS)
        .toList();
      assertThat(usages).hasSize(1);
      assertThat(usages.getFirst().getStudioEvent().getUiActionStats().getActionClassName()).endsWith(EmptyAction.class.getSimpleName());
    } finally {
      UsageTracker.cleanAfterTesting();
    }
  }
}

/** Class needed for testGetActionName(). */
class ExecutorAction {}
