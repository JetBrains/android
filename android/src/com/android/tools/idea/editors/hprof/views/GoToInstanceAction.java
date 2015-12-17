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
package com.android.tools.idea.editors.hprof.views;

import com.android.tools.perflib.heap.Instance;
import com.intellij.openapi.actionSystem.*;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Action to navigate the hprof view to a specific instance.
 */
public class GoToInstanceAction extends AnAction {

  private final EventDispatcher<GoToInstanceListener> myEventDispatcher = EventDispatcher.create(GoToInstanceListener.class);

  public GoToInstanceAction(@NotNull JComponent ancestorComponent) {
    super("Go to Instance");
    // Go to a specific instance details is analogy of go to a class declaration, so reuse the ide shortcut.
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION).getShortcutSet(),
                              ancestorComponent);
  }

  public void addListener(@NotNull GoToInstanceListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(InstanceReferenceTreeView.NAVIGATABLE_INSTANCE) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Instance instance = e.getData(InstanceReferenceTreeView.NAVIGATABLE_INSTANCE);
    if (instance == null) {
      return;
    }
    for (GoToInstanceListener listener : myEventDispatcher.getListeners()) {
      listener.goToInstance(instance);
    }
  }
}
