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
package com.android.tools.idea.common.analytics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.google.wireless.android.sdk.stats.LayoutEditorState;
import com.intellij.mock.MockModule;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

public class CommonUsageTrackerImplTest extends BaseUsageTrackerImplTest {

  protected CommonUsageTracker getUsageTracker() {
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getAnalyticsManager()).thenReturn(new DesignerAnalyticsManager(surface));
    when(surface.getScale()).thenReturn(0.50);
    Configuration configuration = getConfigurationMock();
    when(surface.getConfigurations()).thenReturn(ImmutableList.of(configuration));

    return new CommonUsageTrackerImpl(SYNC_EXECUTOR, surface, usageTracker::logNow) {
      @Override
      boolean shouldLog(int percentage) {
        // Log everything in tests
        return true;
      }
    };
  }

  // b/110242994
  public void ignore_testBasicLogging() {
    CommonUsageTracker tracker = getUsageTracker();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    assertThat(studioEvent.getCategory()).isEqualTo(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR);
    assertThat(studioEvent.getKind()).isEqualTo(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT);
    assertThat(studioEvent.getLayoutEditorEvent().getType()).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);

    // Verify state
    LayoutEditorState state = studioEvent.getLayoutEditorEvent().getState();
    assertThat(state.getConfigZoomLevel()).isEqualTo(SystemInfo.isMac && UIUtil.isRetina() ? 100 : 50);
    assertThat(state.getConfigApiLevel()).isEqualTo("mock");
    assertThat(state.getConfigOrientation()).isEqualTo(LayoutEditorState.Orientation.PORTRAIT);
    usageTracker.getUsages().clear();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
    studioEvent = getLastLogUsage();
    assertThat(studioEvent.getLayoutEditorEvent().getType()).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
  }

  public void testRenderLogging() {
    CommonUsageTracker tracker = getUsageTracker();

    Result renderResult = mock (Result.class);
    when(renderResult.getStatus()).thenReturn(Result.Status.SUCCESS);
    HtmlLinkManager linkManager = mock(HtmlLinkManager.class);
    RenderLogger logger = mock(RenderLogger.class);
    when(logger.getLinkManager()).thenReturn(linkManager);
    ImmutableMap<String, Throwable> brokenClasses = ImmutableMap.of("com.test.mock", new Throwable("mock error"));
    when(logger.getBrokenClasses()).thenReturn(brokenClasses);
    RenderResult result = mock(RenderResult.class);
    ViewInfo rootView = new ViewInfo("ConstraintLayout", null, 0, 0, 50, 50);
    rootView.setChildren(ImmutableList.of(new ViewInfo("TextView", null, 0, 0, 30, 20)));
    when(result.getRootViews()).thenReturn(ImmutableList.of(rootView));
    when(result.getRenderResult()).thenReturn(renderResult);
    when(result.getLogger()).thenReturn(logger);
    when(result.getModule()).thenReturn(new MockModule(getProject(), getTestRootDisposable()));
    when(result.getRenderDuration()).thenReturn(230L);

    tracker.logRenderResult(LayoutEditorRenderResult.Trigger.EDIT, result, false);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutEditorRenderResult loggedResult = studioEvent.getLayoutEditorEvent().getRenderResult();
    assertThat(loggedResult.getResultCode()).isEqualTo(Result.Status.SUCCESS.ordinal());
    assertThat(loggedResult.getTotalRenderTimeMs()).isEqualTo(230);
    assertThat(loggedResult.getComponentCount()).isEqualTo(2);
    assertThat(loggedResult.getTotalIssueCount()).isEqualTo(1);
    assertThat(loggedResult.getErrorCount()).isEqualTo(1);
    assertThat(loggedResult.getFidelityWarningCount()).isEqualTo(0);
  }
}
