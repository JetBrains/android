/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.util.ListenerCollection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class LastActionTracker implements Disposable, AnActionListener {

  private final ListenerCollection<Listener> myListeners = ListenerCollection.createWithDirectExecutor();
  private String myCurrentActionId = "(no action)";
  private long myCurrentActionStartNano;

  public LastActionTracker() {
    ActionManager.getInstance().addAnActionListener(this);
  }

  public void registerActionDurationListener(Listener listener) {
    myListeners.add(listener);
  }

  public String getCurrentActionId() {
    return myCurrentActionId;
  }

  public long getCurrentDurationMs() {
    if (myCurrentActionStartNano != 0) {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - myCurrentActionStartNano);
    } else {
      return 0;
    }
  }

  public void unregisterActionDurationListener(Listener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void dispose() {
    ActionManager.getInstance().removeAnActionListener(this);
  }

  @Override
  public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
    String actionId = getActionId(action);
    myCurrentActionId = actionId;
    myCurrentActionStartNano = System.nanoTime();
    myListeners.forEach(l -> l.actionStarted(actionId));
  }

  private static String getActionId(AnAction action) {
    if (action == null) {
      return "<null>";
    }
    return action.getClass().getName() + " (" + ActionManager.getInstance().getId(action) + ")";
  }

  @Override
  public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
    String actionId = getActionId(action);

    myListeners.forEach(l -> l.actionFinished(actionId, getCurrentDurationMs()));
    myCurrentActionId = "(no action)";
    myCurrentActionStartNano = 0;
  }

  public interface Listener {
    void actionStarted(String actionId);
    void actionFinished(String actionId, long durationMs);
  }
}
