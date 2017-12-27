/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.tools.idea.project.AndroidNotification;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class OpenFirebaseAssistantQuickFix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> {
      AnAction openFirebaseAssistant = ActionManager.getInstance().getAction("DeveloperServices.Firebase");
      if (openFirebaseAssistant == null) {
        ApplicationManager.getApplication().invokeLater(this::reportFirebaseNotAvailable);
        return;
      }
      AnActionEvent openFirebaseAssistantEvent = AnActionEvent.createFromAnAction(openFirebaseAssistant, null, "Android Lint QuickFix",
                                                                                  dataContext);
      openFirebaseAssistant.actionPerformed(openFirebaseAssistantEvent);
    });
  }

  private void reportFirebaseNotAvailable() {
    String message = String.format("<html>Firebase Assistant is not available. The Firebase Services plugin " +
                                   "has to be enabled in the <a href=\"plugins\">Plugins</a> dialog in %1$s.</html>",
                                   ShowSettingsUtil.getSettingsMenuName());
    NotificationListener listener = (notification, event) -> {
      ShowSettingsUtil.getInstance().showSettingsDialog(null, PluginManagerConfigurable.class);
      notification.expire();
    };
    Notification notification =
      AndroidNotification.BALLOON_GROUP.createNotification(getName(), message, NotificationType.WARNING, listener);
    notification.notify(null);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return startElement.getText().startsWith("'com.google.android.gms:play-services:");
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.fix.open.firebase.assistant");
  }
}
