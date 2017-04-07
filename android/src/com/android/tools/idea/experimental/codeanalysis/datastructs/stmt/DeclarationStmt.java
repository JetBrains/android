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
package com.android.tools.idea.experimental.codeanalysis.datastructs.stmt;

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Local;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public interface DeclarationStmt extends Stmt {

  /**
   * Get the type of this declaration.
   * @return The Type.
   */
  @NotNull
  PsiType getType();

  /**
   * Get the PsiLocalVariable reference to this declaration.
   * @return Return the PsiLocalVariable reference.
   */
  @NotNull
  PsiLocalVariable getPsiLocal();

  /**
   * Get the Local of this declaration.
   * @return Return the Local.
   */
  @NotNull
  Local getLocal();
}
