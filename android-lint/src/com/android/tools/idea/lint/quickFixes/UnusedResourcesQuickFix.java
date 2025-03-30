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

import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.refactoring.UnusedResourcesProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnusedResourcesQuickFix extends DefaultLintQuickFix {
  private final String myResource;

  public UnusedResourcesQuickFix(@Nullable String resource) {
    super(resource != null ? "Remove Declarations for " + resource : "Remove All Unused Resources");
    myResource = resource;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    Project project = startElement.getProject();
    MyResourcesProcessorFilter filter = myResource != null ? new MyResourcesProcessorFilter(myResource) : null;

    UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, filter);
    processor.setIncludeIds(true);
    processor.run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class MyResourcesProcessorFilter implements UnusedResourcesProcessor.Filter {
    @NotNull
    private final String myResource;

    MyResourcesProcessorFilter(@NotNull String resource) {
      myResource = resource;
    }

    @Override
    public boolean shouldProcessFile(@NotNull PsiFile psiFile) {
      return true;
    }

    @Override
    public boolean shouldProcessResource(@Nullable String resource) {
      return myResource.equals(resource);
    }
  }
}
