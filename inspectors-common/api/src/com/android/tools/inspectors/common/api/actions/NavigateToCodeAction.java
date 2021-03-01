/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.api.actions;

import com.android.tools.inspectors.common.api.stacktrace.CodeLocation;
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public final class NavigateToCodeAction extends AnAction {
  @NotNull private final Supplier<CodeLocation> myLocationSupplier;
  @NotNull private final CodeNavigator myCodeNavigator;

  public NavigateToCodeAction(@NotNull Supplier<CodeLocation> locationSupplier, @NotNull CodeNavigator codeNavigator) {
    myLocationSupplier = locationSupplier;
    myCodeNavigator = codeNavigator;

    String title = ActionsBundle.actionText("EditSource");
    Presentation presentation = getTemplatePresentation();
    presentation.setText(title);
    presentation.setDescription(ActionsBundle.actionDescription("EditSource"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    CodeLocation codeLocation = myLocationSupplier.get();
    if (codeLocation == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    // Because update() is also called right before actonPerformed for another check, if we disable the button here it would cause the
    // action to be ignored. Therefore we always enable the button before checking isNavigatable asynchronously.
    e.getPresentation().setEnabled(true);
    // Check if the code is navigatable in another thread and enable the button accordingly. In most cases, the change from disabled to
    // enabled shouldn't be perceptible. However, we do it as a safe measure as some heavy operations might happen (e.g. searching for the
    // java class/method in the PSI tree or using llvm-symbolizer to get a native function name).
    CompletableFuture.supplyAsync(
      () -> ReadAction.compute(() -> myCodeNavigator.isNavigatable(codeLocation)),
      ApplicationManager.getApplication()::executeOnPooledThread)
      .thenAcceptAsync(isNavigatable -> e.getPresentation().setEnabled(isNavigatable), ApplicationManager.getApplication()::invokeLater);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CodeLocation location = myLocationSupplier.get();
    if (location != null) {
      myCodeNavigator.navigate(location);
    }
  }
}
