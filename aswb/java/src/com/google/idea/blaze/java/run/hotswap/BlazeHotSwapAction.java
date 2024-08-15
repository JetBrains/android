/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.hotswap;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.actions.ReplaceActionHelper;
import com.intellij.debugger.actions.HotSwapAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;

/** Overrides the built-in hotswap action for Blaze projects */
public class BlazeHotSwapAction extends AnAction {

  private static final String ACTION_ID = "Hotswap";

  static class Customizer implements ActionConfigurationCustomizer {
    @Override
    public void customize(ActionManager actionManager) {
      AnAction delegate = actionManager.getAction(ACTION_ID);
      if (delegate == null) {
        // HotSwapAction not registered by default for Android Studio, though still in the classpath
        delegate = new HotSwapAction();
      }
      ReplaceActionHelper.conditionallyReplaceAction(
          actionManager, ACTION_ID, new BlazeHotSwapAction(delegate), Blaze::isBlazeProject);
    }
  }

  private BlazeHotSwapAction(AnAction delegate) {
    super(
        delegate.getTemplatePresentation().getTextWithMnemonic(),
        delegate.getTemplatePresentation().getDescription(),
        delegate.getTemplatePresentation().getIcon());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    BlazeHotSwapManager.reloadChangedClasses(e.getProject());
  }

  @Override
  public void update(AnActionEvent e) {
    boolean canHotSwap =
        HotSwapUtils.enableHotSwapping.getValue()
            && BlazeHotSwapManager.findHotSwappableBlazeDebuggerSession(e.getProject()) != null;
    e.getPresentation().setEnabled(canHotSwap);
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
