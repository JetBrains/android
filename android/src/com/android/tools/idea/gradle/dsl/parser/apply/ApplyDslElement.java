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
package com.android.tools.idea.gradle.dsl.parser.apply;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyDslElement extends GradlePropertiesDslElement {
  @NonNls public static final String APPLY_BLOCK_NAME = "apply";
  // The file that is applied by the element. For apply statements that apply plugins, this is null.
  @Nullable private GradleBuildFile myAppliedBuildFile;

  public ApplyDslElement(@Nullable GradleDslElement parent) {
    super(parent, null, APPLY_BLOCK_NAME);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("plugin")) {
      addToParsedExpressionList("plugin", element);
      return;
    }
    if (property.equals("from")) {
      // Try and find the given file.
      String fileName = attemptToExtractFileName(element);
      if (fileName != null) {
        VirtualFile file = getDslFile().getFile().getParent().findChild(fileName);
        if (file != null) {
          myAppliedBuildFile = new GradleBuildFile(file, getDslFile().getProject(), fileName);
          // Register the applied file.
          getDslFile().registerAppliedFile(this);
        }
      }
    }

    super.addParsedElement(property, element);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return null; // This class is used to just group different kinds of apply statements and is never used to create a new element.
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myParent == null ? null : myParent.create();
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
  }

  @Nullable
  public GradleDslFile getAppliedFile() {
    return myAppliedBuildFile;
  }

  @Nullable
  private static String attemptToExtractFileName(@NotNull GradleDslElement element) {
    if (element instanceof GradleDslLiteral) {
      return ((GradleDslLiteral)element).getValue(String.class);
    }

    return null;
  }
}
