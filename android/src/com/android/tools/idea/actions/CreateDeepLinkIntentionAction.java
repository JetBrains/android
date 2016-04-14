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

import com.android.tools.idea.stats.UsageTracker;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.actions.DeepLinkCodeGeneratorAction.addDeepLinkAtCaret;
import static com.android.tools.idea.actions.DeepLinkCodeGeneratorAction.isDeepLinkAvailable;

/**
 * An intention action to insert a deep link intent filter for activity.
 */
public class CreateDeepLinkIntentionAction extends AbstractIntentionAction {

  @Override
  public String getText() {
    return "Add URL";
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isDeepLinkAvailable(editor, file);
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) {
    UsageTracker.getInstance().trackEvent(
        UsageTracker.CATEGORY_APP_INDEXING, UsageTracker.ACTION_APP_INDEXING_DEEP_LINK_CREATED, null, null);
    addDeepLinkAtCaret(project, editor, file);
  }
}
