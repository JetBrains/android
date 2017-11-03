/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.fixtures;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlBaseComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public abstract class NlEditorFixtureBase {
  private final NlEditingListener myListener;

  private Component myFocusedComponent;
  private Component myFocusedPopupComponent;
  private int myStopEditingCallCount;
  private int myCancelEditingCallCount;

  protected NlEditorFixtureBase(@NotNull NlBaseComponentEditor editor) {
    myListener = editor.getEditingListener();
    KeyboardFocusManager focusManager = mock(KeyboardFocusManager.class);
    doAnswer(invocation -> getFocusedComponent()).when(focusManager).getFocusOwner();
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager);
  }

  public void tearDown() {
    setFocusedComponent(null);
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
  }

  protected void setFocusedComponent(@Nullable Component component) {
    if (myFocusedPopupComponent != null) {
      notifyFocusLoss(myFocusedPopupComponent);
      myFocusedPopupComponent = null;
    }
    if (myFocusedComponent != null && (component == null || (myFocusedComponent != component))) {
      notifyFocusLoss(myFocusedComponent);
      myFocusedComponent = null;
    }
    if (component != null && (myFocusedComponent != component)) {
      notifyFocusGain(component);
      myFocusedComponent = component;
    }
  }

  protected void setFocusedPopupComponent(@NotNull Component component) {
    notifyFocusGain(component);
    myFocusedPopupComponent = component;
  }

  private Component getFocusedComponent() {
    if (myFocusedPopupComponent != null) {
      return myFocusedPopupComponent;
    }
    return myFocusedComponent;
  }

  private static void notifyFocusGain(@NotNull Component component) {
    FocusEvent event = new FocusEvent(component, FocusEvent.FOCUS_GAINED);
    for (FocusListener listener : reverse(component.getFocusListeners())) {
      listener.focusGained(event);
    }
  }

  private static void notifyFocusLoss(@NotNull Component component) {
    FocusEvent event = new FocusEvent(component, FocusEvent.FOCUS_LOST);
    for (FocusListener listener : reverse(component.getFocusListeners())) {
      listener.focusLost(event);
    }
  }

  // The code currently relies on a certain order of the listeners to be called.
  // This is not great. This emulates the order being used in Studio.
  private static List<FocusListener> reverse(@Nullable FocusListener[] listeners) {
    if (listeners == null) {
      return Collections.emptyList();
    }
    List<FocusListener> list = Arrays.asList(listeners);
    Collections.reverse(list);
    return list;
  }

  public NlEditorFixtureBase verifyStopEditingCalled(@NotNull NlBaseComponentEditor componentEditor) {
    verify(myListener, times(++myStopEditingCallCount)).stopEditing(eq(componentEditor), any());
    return this;
  }

  public NlEditorFixtureBase verifyCancelEditingCalled(@NotNull NlBaseComponentEditor componentEditor) {
    verify(myListener, times(++myCancelEditingCallCount)).cancelEditing(eq(componentEditor));
    return this;
  }

  protected static NlEditingListener createListener() {
    NlEditingListener listener = mock(NlEditingListener.class);
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      NlComponentEditor editor = (NlComponentEditor)args[0];
      Object value = args[1];
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      property.setValue(value);
      UIUtil.dispatchAllInvocationEvents();
      return null;
    }).when(listener).stopEditing(any(NlComponentEditor.class), any());
    return listener;
  }

  @NotNull
  protected static <T> T findSubComponent(JComponent panel, Class<T> componentClass) {
    Optional<Component> result = Arrays.stream(panel.getComponents())
      .filter(componentClass::isInstance)
      .findFirst();
    //noinspection unchecked
    return (T)result.orElseThrow(() -> new RuntimeException("No such component found: " + componentClass.getName()));
  }
}
