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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import org.jetbrains.annotations.NotNull;

public interface DefinitionStmt extends Stmt {

  /**
   * Return the Left Hand Side Op. It cannot be null.
   * @return Return the LHS Op.
   */
  @NotNull
  public Value getLOp();

  /**
   * Return the Right Hand Side Op. It cannot be null.
   * @return Return the RHS Op.
   */
  @NotNull
  public Value getROp();

  /**
   * Set the Left Hand Side Op.
   * @param L The LHS Op
   */
  public void setLOp(@NotNull Value L);

  /**
   * Set the Right Hand Side Op.
   * @param R The RHS Op
   */
  public void setROp(@NotNull Value R);
}
