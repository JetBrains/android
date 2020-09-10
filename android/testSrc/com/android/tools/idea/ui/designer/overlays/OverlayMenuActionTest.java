/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.designer.overlays;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.extensions.Extensions;
import javax.swing.Icon;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

public class OverlayMenuActionTest extends AndroidTestCase {
  private EditorDesignSurface mySurface;
  private OverlayProvider myProvider;
  private OverlayData myData;
  private OverlayConfiguration myOverlayConfiguration;
  private AnActionEvent myEvent;

  private static final String DATA_ID = "id1";
  private static final String CURR_ID = "id2";

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myEvent = mock(AnActionEvent.class);
    myProvider = mock(OverlayProvider.class);
    mySurface = mock(EditorDesignSurface.class);
    myOverlayConfiguration = mock(OverlayConfiguration.class);
    myData = new OverlayData(new OverlayEntry(DATA_ID, myProvider), DATA_ID, null);

    when(mySurface.getOverlayConfiguration()).thenReturn(myOverlayConfiguration);
    when(myProvider.getPluginIcon()).thenReturn(mock(Icon.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(true);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      mySurface = null;
    }
    finally {
      super.tearDown();
    }
  }
  private static void checkAction(@NotNull AnAction action, @NotNull Class<? extends AnAction> actionClass, @Nullable String title) {
    assertThat(action.getTemplatePresentation().getText()).isEqualTo(title);
    assertThat(action).isInstanceOf(actionClass);
  }

  public void testActionWithOverlayProvider() {
    Extensions.getRootArea().getExtensionPoint(OverlayConfiguration.EP_NAME).registerExtension(mock(OverlayProvider.class), getTestRootDisposable());

    OverlayMenuAction action = new OverlayMenuAction(mySurface);
    action.updateActions(DataContext.EMPTY_CONTEXT);
    AnAction[] actions = action.getChildren(null);
    checkAction(actions[0], ActionGroup.class, "Overlays");
    checkAction(actions[1], OverlayMenuAction.AddOverlayAction.class, "Add null Overlay...");
    checkAction(actions[2], Separator.class, null);
    checkAction(actions[3], OverlayMenuAction.UpdateOverlayAction.class, "Reload Overlay");
    checkAction(actions[4], OverlayMenuAction.ToggleCachedOverlayAction.class, "Toggle Overlay");
    checkAction(actions[5], OverlayMenuAction.CancelOverlayAction.class, "Cancel Overlay");
    assertThat(actions).hasLength(6);
  }
  public void testAddOverlayActionSuccess() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();
    OverlayMenuAction.AddOverlayAction action =
      new OverlayMenuAction.AddOverlayAction(mySurface, myProvider);

    when(myProvider.addOverlay()).thenReturn(result);

    action.actionPerformed(myEvent);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).addOverlay(myData);
    verify(myOverlayConfiguration, times(1)).showPlaceholder();
  }

  public void testAddOverlayActionFailed() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();
    OverlayMenuAction.AddOverlayAction action =
      new OverlayMenuAction.AddOverlayAction(mySurface, myProvider);

    when(myProvider.addOverlay()).thenReturn(result);

    action.actionPerformed(myEvent);
    result.setError(new NullPointerException());

    verify(myOverlayConfiguration, times(0)).addOverlay(any(OverlayData.class));
    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
  }

  public void testAddOverlayActionCancelled() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();
    OverlayMenuAction.AddOverlayAction action =
      new OverlayMenuAction.AddOverlayAction(mySurface, myProvider);
    OverlayMenuAction.CancelOverlayAction cancelAction = new OverlayMenuAction.CancelOverlayAction(mySurface);

    when(myProvider.addOverlay()).thenReturn(result);
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);
    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(true);

    action.actionPerformed(myEvent);
    cancelAction.actionPerformed(myEvent);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
    verify(myOverlayConfiguration, times(0)).addOverlay(any(OverlayData.class));
  }

  public void testToggleOverlayActionSuccess() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry()).thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(mySurface,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);

    action.actionPerformed(myEvent);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).updateOverlay(myData);
  }

  public void testToggleOverlayActionCancelled() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry()).thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);
    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(true);
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.CancelOverlayAction cancelAction = new OverlayMenuAction.CancelOverlayAction(mySurface);
    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(mySurface,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);

    action.actionPerformed(myEvent);
    cancelAction.actionPerformed(myEvent);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
    verify(myOverlayConfiguration, times(0)).updateOverlay(myData);
  }

  public void testToggleOverlayActionNotFoundException() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry()).thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(mySurface,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);


    action.actionPerformed(myEvent);
    result.setError(new OverlayNotFoundException());

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
    verify(myOverlayConfiguration, times(1)).removeOverlayFromList(any(OverlayEntry.class));

  }

  public void testToggleOverlayActionOtherException() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry()).thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(mySurface,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);

    action.actionPerformed(myEvent);
    result.setError(new Exception());

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
    verify(myOverlayConfiguration, times(0)).removeOverlayFromList(any(OverlayEntry.class));
  }

  public void testToggleOffOverlay() {
    when(myOverlayConfiguration.getCurrentOverlayEntry()).thenReturn(new OverlayEntry(DATA_ID, myProvider));

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(mySurface,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);

    action.actionPerformed(myEvent);

    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
  }

  public void testCancelOverlayAction() {
    OverlayMenuAction.CancelOverlayAction action = new OverlayMenuAction.CancelOverlayAction(mySurface);

    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(true);
    action.actionPerformed(myEvent);

    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
  }

  public void testCancelEmptyOverlayAction() {
    OverlayMenuAction.CancelOverlayAction action = new OverlayMenuAction.CancelOverlayAction(mySurface);

    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(false);
    action.actionPerformed(myEvent);

    verify(myOverlayConfiguration, times(0)).clearCurrentOverlay();
  }
}
