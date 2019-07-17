// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.startup;

import com.android.tools.idea.fd.actions.HotswapAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.ApplyChangesAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class InstantRunAction extends AnAction {

  private final AnAction myDelegate;

  public InstantRunAction() {
    myDelegate = StudioFlags.JVMTI_REFRESH.get() ? new ApplyChangesAction() : new HotswapAction();
    getTemplatePresentation().copyFrom(myDelegate.getTemplatePresentation());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myDelegate.actionPerformed(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    myDelegate.update(e);
  }
}
