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
package com.android.tools.idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.SystemInfo;

/**
 * Override to disable when not on a supported OS.
 */
public class SendFeedbackAction extends com.intellij.ide.actions.SendFeedbackAction {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().isEnabled()) {
      e.getPresentation().setEnabled(SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows);
    }
  }
}
