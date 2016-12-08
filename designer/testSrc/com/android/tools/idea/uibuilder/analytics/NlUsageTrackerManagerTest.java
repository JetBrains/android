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
package com.android.tools.idea.uibuilder.analytics;

import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.State;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.google.wireless.android.sdk.stats.LayoutEditorState;
import com.intellij.mock.MockModule;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.LinkedList;
import java.util.concurrent.Executor;

import static com.android.resources.ScreenOrientation.PORTRAIT;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class NlUsageTrackerManagerTest extends JavaCodeInsightFixtureTestCase {
  private static final Executor SYNC_EXECUTOR = Runnable::run;

  private static Configuration getConfigurationMock() {
    IAndroidTarget target = mock(IAndroidTarget.class);
    when(target.getVersion()).thenReturn(new AndroidVersion(0, "mock"));

    State state = mock(State.class);
    when(state.getOrientation()).thenReturn(PORTRAIT);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getTarget()).thenReturn(target);
    when(configuration.getDeviceState()).thenReturn(state);

    return configuration;
  }

  public void testGetInstance() {
    assertEquals(NlUsageTrackerManager.NOP_TRACKER, NlUsageTrackerManager.getInstanceInner(null));

    DesignSurface surface1 = mock(DesignSurface.class);
    DesignSurface surface2 = mock(DesignSurface.class);
    NlUsageTracker nlUsageTracker = NlUsageTrackerManager.getInstanceInner(surface1);
    assertNotEquals(NlUsageTrackerManager.NOP_TRACKER, surface1);
    assertEquals(nlUsageTracker, NlUsageTrackerManager.getInstanceInner(surface1));
    assertNotEquals(nlUsageTracker, NlUsageTrackerManager.getInstanceInner(surface2));
  }

  public void testBasicLogging() {
    UsageTracker usageTracker = mock(UsageTracker.class);
    LinkedList<AndroidStudioEvent> logCalls = new LinkedList<>();
    doAnswer(invocation -> {
      logCalls.add(((AndroidStudioEvent.Builder)invocation.getArguments()[0]).build());
      return null;
    }).when(usageTracker).log(any());

    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getLayoutType()).thenReturn(NlLayoutType.LAYOUT);
    when(surface.getScreenMode()).thenReturn(DesignSurface.ScreenMode.BOTH);
    when(surface.getScale()).thenReturn(0.50);
    Configuration configuration = getConfigurationMock();
    when(surface.getConfiguration()).thenReturn(configuration);
    NlUsageTracker tracker = new NlUsageTrackerManager(SYNC_EXECUTOR, surface, usageTracker);

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
    assertEquals(1, logCalls.size());
    AndroidStudioEvent studioEvent = logCalls.getFirst();
    assertEquals(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR, studioEvent.getCategory());
    assertEquals(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT, studioEvent.getKind());
    assertEquals(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE,
                 studioEvent.getLayoutEditorEvent().getType());
    // Verify state
    LayoutEditorState state = studioEvent.getLayoutEditorEvent().getState();
    assertEquals(LayoutEditorState.Type.LAYOUT, state.getType());
    assertEquals(LayoutEditorState.Surfaces.BOTH, state.getSurfaces());
    assertEquals(50, state.getConfigZoomLevel());
    assertEquals("mock", state.getConfigApiLevel());
    assertEquals(LayoutEditorState.Orientation.PORTRAIT, state.getConfigOrientation());
    logCalls.clear();

    tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
    assertEquals(1, logCalls.size());
    studioEvent = logCalls.getFirst();
    assertEquals(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL,
                 studioEvent.getLayoutEditorEvent().getType());
  }

  public void testRenderLogging() {
    UsageTracker usageTracker = mock(UsageTracker.class);
    LinkedList<AndroidStudioEvent> logCalls = new LinkedList<>();
    doAnswer(invocation -> {
      logCalls.add(((AndroidStudioEvent.Builder)invocation.getArguments()[0]).build());
      return null;
    }).when(usageTracker).log(any());

    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getLayoutType()).thenReturn(NlLayoutType.LAYOUT);
    when(surface.getScreenMode()).thenReturn(DesignSurface.ScreenMode.BOTH);
    when(surface.getScale()).thenReturn(0.50);
    Configuration configuration = getConfigurationMock();
    when(surface.getConfiguration()).thenReturn(configuration);
    NlUsageTracker tracker = new NlUsageTrackerManager(SYNC_EXECUTOR, surface, usageTracker) {
      @Override
      boolean shouldLog(int percent) {
        // Log everything in tests
        return true;
      }
    };

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

    tracker.logRenderResult(NlModel.ChangeType.EDIT, result, 230);
    assertEquals(1, logCalls.size());
    AndroidStudioEvent studioEvent = logCalls.getFirst();
    LayoutEditorRenderResult loggedResult = studioEvent.getLayoutEditorEvent().getRenderResult();
    assertEquals(Result.Status.SUCCESS.ordinal(), loggedResult.getResultCode());
    assertEquals(230, loggedResult.getTotalRenderTimeMs());
    assertEquals(2, loggedResult.getComponentCount());
    assertEquals(1, loggedResult.getTotalIssueCount());
    assertEquals(1, loggedResult.getErrorCount());
    assertEquals(0, loggedResult.getFidelityWarningCount());
  }

  public void testLogDropFromPalette() {

  }

  public void testLogPropertyChange() {

  }
}
