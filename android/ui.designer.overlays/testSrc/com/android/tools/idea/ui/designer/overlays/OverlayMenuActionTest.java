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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.extensions.Extensions;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.Icon;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

public class OverlayMenuActionTest extends AndroidTestCase {
  private Runnable repaint;
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
    repaint = mock(Runnable.class);
    myOverlayConfiguration = mock(OverlayConfiguration.class);
    myData = new OverlayData(new OverlayEntry(DATA_ID, myProvider), DATA_ID, null);
    when(myProvider.getPluginIcon()).thenReturn(mock(Icon.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(true);
  }

  private static void checkAction(@NotNull AnAction action,
                                  @NotNull Class<? extends AnAction> actionClass,
                                  @Nullable String title) {
    assertThat(action.getTemplatePresentation().getText()).isEqualTo(title);
    assertThat(action).isInstanceOf(actionClass);
  }

  public void testActionWithOverlayProvider() {
    Extensions.getRootArea().getExtensionPoint(OverlayConfiguration.EP_NAME)
      .registerExtension(mock(OverlayProvider.class), getTestRootDisposable());

    OverlayMenuAction action = new OverlayMenuAction(myOverlayConfiguration, repaint);
    action.updateActions(DataContext.EMPTY_CONTEXT);
    AnAction[] actions = action.getChildren(ActionManager.getInstance());
    checkAction(actions[0], ActionGroup.class, "Overlays");
    checkAction(actions[1], OverlayMenuAction.AddOverlayAction.class, "Add null Overlay...");
    checkAction(actions[2], OverlayMenuAction.DeleteOverlayAction.class, "Delete Overlay...");
    checkAction(actions[3], Separator.class, null);
    checkAction(actions[4], OverlayMenuAction.UpdateOverlayAction.class, "Reload Overlay");
    checkAction(actions[5], OverlayMenuAction.ToggleCachedOverlayAction.class, "Toggle Overlay");
    checkAction(actions[6], OverlayMenuAction.CancelOverlayAction.class, "Cancel Overlay");
    assertThat(actions).hasLength(7);
  }

  public void testAddOverlayActionSuccess() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();
    OverlayMenuAction.AddOverlayAction action =
      new OverlayMenuAction.AddOverlayAction(myOverlayConfiguration, myProvider);

    when(myProvider.addOverlay()).thenReturn(result);

    action.actionPerformed(myEvent);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).addOverlay(myData);
    verify(myOverlayConfiguration, times(1)).showPlaceholder();
  }

  public void testAddOverlayActionFailed() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();
    OverlayMenuAction.AddOverlayAction action =
      new OverlayMenuAction.AddOverlayAction(myOverlayConfiguration, myProvider);

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
      new OverlayMenuAction.AddOverlayAction(myOverlayConfiguration, myProvider);
    OverlayMenuAction.CancelOverlayAction cancelAction =
      new OverlayMenuAction.CancelOverlayAction(myOverlayConfiguration, repaint);

    when(myProvider.addOverlay()).thenReturn(result);
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);
    when(myOverlayConfiguration.isOverlayPresent()).thenReturn(true);

    action.actionPerformed(myEvent);
    cancelAction.actionPerformed(myEvent);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
    verify(myOverlayConfiguration, times(0)).addOverlay(any(OverlayData.class));
  }

  public void testToggleOverlayActionSuccess() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(myOverlayConfiguration,
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

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);
    when(myOverlayConfiguration.isOverlayPresent()).thenReturn(true);
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.CancelOverlayAction cancelAction =
      new OverlayMenuAction.CancelOverlayAction(myOverlayConfiguration, repaint);
    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(myOverlayConfiguration,
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

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(myOverlayConfiguration,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);


    action.actionPerformed(myEvent);
    result.setError(new OverlayNotFoundException());

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
    verify(myOverlayConfiguration, times(1))
      .removeOverlayFromList(any(OverlayEntry.class));
  }

  public void testToggleOverlayActionOtherException() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(DATA_ID))).thenReturn(result);

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(myOverlayConfiguration,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);

    action.actionPerformed(myEvent);
    result.setError(new Exception());

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
    verify(myOverlayConfiguration, times(0))
      .removeOverlayFromList(any(OverlayEntry.class));
  }

  public void testToggleOffOverlay() {
    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(DATA_ID, myProvider));

    OverlayMenuAction.ToggleOverlayAction action =
      new OverlayMenuAction.ToggleOverlayAction(myOverlayConfiguration,
                                                myData.getOverlayName(),
                                                myData.getOverlayEntry(),
                                                true);

    action.actionPerformed(myEvent);

    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
  }

  public void testUpdateOverlayActionSuccess() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(CURR_ID))).thenReturn(result);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(mock(BufferedImage.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);

    OverlayMenuAction.UpdateOverlayAction action =
      new OverlayMenuAction.UpdateOverlayAction(myOverlayConfiguration);

    action.actionPerformed(myEvent);
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(true);
    result.setResult(myData);

    verify(myOverlayConfiguration, times(1)).showPlaceholder();
    verify(myOverlayConfiguration, times(1)).updateOverlay(myData);
  }

  public void testUpdateOverlayActionFailed() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(CURR_ID))).thenReturn(result);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(mock(BufferedImage.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);

    OverlayMenuAction.UpdateOverlayAction action =
      new OverlayMenuAction.UpdateOverlayAction(myOverlayConfiguration);

    action.actionPerformed(myEvent);
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(true);
    result.setError(new NullPointerException());

    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
    verify(myOverlayConfiguration, times(0)).updateOverlay(myData);
  }

  public void testUpdateOverlayActionOverlayNotFoundException() {
    AsyncPromise<OverlayData> result = new AsyncPromise<>();

    when(myOverlayConfiguration.getCurrentOverlayEntry())
      .thenReturn(new OverlayEntry(CURR_ID, myProvider));
    when(myProvider.getOverlay(eq(CURR_ID))).thenReturn(result);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(mock(BufferedImage.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);

    OverlayMenuAction.UpdateOverlayAction action =
      new OverlayMenuAction.UpdateOverlayAction(myOverlayConfiguration);

    action.actionPerformed(myEvent);
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(true);
    result.setError(new OverlayNotFoundException());

    verify(myOverlayConfiguration, times(1)).hidePlaceholder();
    verify(myOverlayConfiguration, times(1))
      .removeOverlayFromList(any(OverlayEntry.class));
  }

  public void testCancelOverlayAction() {
    OverlayMenuAction.CancelOverlayAction action =
      new OverlayMenuAction.CancelOverlayAction(myOverlayConfiguration, repaint);

    when(myOverlayConfiguration.isOverlayPresent()).thenReturn(true);
    action.actionPerformed(myEvent);

    verify(myOverlayConfiguration, times(1)).clearCurrentOverlay();
  }

  public void testCancelEmptyOverlayAction() {
    OverlayMenuAction.CancelOverlayAction action =
      new OverlayMenuAction.CancelOverlayAction(myOverlayConfiguration, repaint);

    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(false);
    action.actionPerformed(myEvent);

    verify(myOverlayConfiguration, times(0)).clearCurrentOverlay();
  }

  public void testToggleOnCachedOverlayAction() {
    OverlayMenuAction.ToggleCachedOverlayAction action =
      new OverlayMenuAction.ToggleCachedOverlayAction(myOverlayConfiguration, repaint);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(mock(BufferedImage.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);
    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(false);

    action.actionPerformed(mock(AnActionEvent.class));

    verify(myOverlayConfiguration, times(1)).showCachedOverlay();
    verify(repaint, times(1)).run();
  }

  public void testToggleOffCachedOverlayAction() {
    OverlayMenuAction.ToggleCachedOverlayAction action =
      new OverlayMenuAction.ToggleCachedOverlayAction(myOverlayConfiguration, repaint);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(mock(BufferedImage.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);
    when(myOverlayConfiguration.getOverlayVisibility()).thenReturn(true);

    action.actionPerformed(mock(AnActionEvent.class));

    verify(myOverlayConfiguration, times(1)).hideCachedOverlay();
    verify(repaint, times(1)).run();
  }

  public void testToggleCachedOverlayActionNoOverlay() {
    OverlayMenuAction.ToggleCachedOverlayAction action =
      new OverlayMenuAction.ToggleCachedOverlayAction(myOverlayConfiguration, repaint);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(null);
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(false);

    action.actionPerformed(mock(AnActionEvent.class));

    verify(myOverlayConfiguration, never()).showCachedOverlay();
    verify(myOverlayConfiguration, never()).hideCachedOverlay();
  }

  public void testToggleCachedOverlayActionWithPlaceholder() {
    OverlayMenuAction.ToggleCachedOverlayAction action =
      new OverlayMenuAction.ToggleCachedOverlayAction(myOverlayConfiguration, repaint);

    when(myOverlayConfiguration.getOverlayImage()).thenReturn(mock(BufferedImage.class));
    when(myOverlayConfiguration.isPlaceholderVisible()).thenReturn(true);

    action.actionPerformed(mock(AnActionEvent.class));

    verify(myOverlayConfiguration, never()).showCachedOverlay();
    verify(myOverlayConfiguration, never()).hideCachedOverlay();
  }

  public void testDeleteOverlayActionEmptyOverlayList() {
    OverlayMenuAction.DeleteOverlayAction action =
      new OverlayMenuAction.DeleteOverlayAction(myOverlayConfiguration);
    AnActionEvent event = mock(AnActionEvent.class);
    Presentation presentation = mock(Presentation.class);


    when(myOverlayConfiguration.getAllOverlays()).thenReturn(new ArrayList<>());
    when(event.getPresentation()).thenReturn(presentation);
    action.update(event);

    verify(presentation, times(1)).setVisible(false);

    when(myOverlayConfiguration.getAllOverlays())
      .thenReturn(Arrays.asList(mock(OverlayData.class)));
    when(event.getPresentation()).thenReturn(presentation);
    action.update(event);

    verify(presentation, times(1)).setVisible(true);
  }

  public void testOverlayActionsVisibility() {
    OverlayMenuAction.ToggleCachedOverlayAction toggleAction =
      new OverlayMenuAction.ToggleCachedOverlayAction(myOverlayConfiguration, repaint);
    OverlayMenuAction.UpdateOverlayAction updateAction =
      new OverlayMenuAction.UpdateOverlayAction(myOverlayConfiguration);
    OverlayMenuAction.CancelOverlayAction cancelAction =
      new OverlayMenuAction.CancelOverlayAction(myOverlayConfiguration, repaint);

    checkVisibility(toggleAction);
    checkVisibility(updateAction);
    checkVisibility(cancelAction);
  }

  private void checkVisibility(AnAction action) {
    AnActionEvent event = mock(AnActionEvent.class);
    Presentation presentation = mock(Presentation.class);

    when(myOverlayConfiguration.isOverlayPresent()).thenReturn(true);
    when(event.getPresentation()).thenReturn(presentation);
    action.update(event);

    verify(presentation, times(1)).setVisible(true);

    when(myOverlayConfiguration.isOverlayPresent()).thenReturn(false);
    when(event.getPresentation()).thenReturn(presentation);
    action.update(event);

    verify(presentation, times(1)).setVisible(false);
  }
}
