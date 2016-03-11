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
package org.jetbrains.android.dom;

import com.intellij.lang.Language;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;

/**
 * Fake PSI element to provide used to provide documentation on autocompletion,
 * as described in https://devnet.jetbrains.com/thread/436977
 */
public class ProvidedDocumentationPsiElement extends LightElement {
  @NotNull
  private final String myValue;

  @NotNull
  private final String myDocumentation;

  public ProvidedDocumentationPsiElement(@NotNull PsiManager manager,
                                         @NotNull Language language,
                                         @NotNull String value,
                                         @NotNull String documentation) {
    super(manager, language);
    myValue = value;
    myDocumentation = documentation;
  }

  @NotNull
  public String getDocumentation() {
    return myDocumentation;
  }

  @Override
  public String toString() {
    return myDocumentation;
  }

  /**
   * {@link #getText()} is overridden to modify title of documentation popup
   */
  @Override
  @NotNull
  public String getText() {
    return myValue;
  }
}
