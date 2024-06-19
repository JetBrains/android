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
package com.android.tools.idea.lint.quickFixes;

import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.lint.checks.UselessViewDetector;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class RemoveUselessViewQuickFix extends DefaultLintQuickFix {
  private final Issue myIssue;

  public RemoveUselessViewQuickFix(@NotNull Issue issue) {
    super(AndroidLintBundle.message("android.lint.fix.remove.unnecessary.view"));
    myIssue = issue;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      return;
    }

    if (myIssue.getId().equals(UselessViewDetector.USELESS_LEAF.getId())) {
      WriteCommandAction.runWriteCommandAction(tag.getProject(), tag::delete);
    }
    else {
      assert false;
      // todo: implement
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return false;
    }
    return tag.getParentTag() != null;
  }
}
