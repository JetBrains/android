/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.util;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * This interface is intended to be implemented by all Dsl models -- whether block, file or property -- and supports
 * only querying for the PsiElement corresponding to the model, if any (there may be no Psi element
 * if the model has been created through use of the API rather than through parsing).
 */
public interface PsiElementHolder {
  /**
   * @return the psiElement for this model.
   */
  @Nullable
  PsiElement getPsiElement();
}
