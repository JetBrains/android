// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.startup;

import com.android.tools.idea.fd.actions.HotswapAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.ApplyChangesAction;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.actionSystem.*;

import static com.intellij.openapi.actionSystem.Anchor.AFTER;

public class InstantRunActionsInitializer implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    // Since the executor actions are registered dynamically, and we want to insert ourselves in the middle, we have to do this
    // in code as well (instead of xml).
    ActionManager actionManager = ActionManager.getInstance();
    AnAction runnerActions = actionManager.getAction(IdeActions.GROUP_RUNNER_ACTIONS);
    if (runnerActions instanceof DefaultActionGroup) {
      AnAction action;
      if (StudioFlags.JVMTI_REFRESH.get()) {
        action = new ApplyChangesAction();
      } else {
        action = new HotswapAction();
      }
      ((DefaultActionGroup)runnerActions).add(action, new Constraints(AFTER, IdeActions.ACTION_DEFAULT_RUNNER));
    }
  }
}
