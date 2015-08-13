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
package org.jetbrains.android.intentions;

import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.android.inspections.lint.ParcelableQuickFix.Operation.REIMPLEMENT;

public class RedoParcelableAction extends ImplementParcelableAction {

  public RedoParcelableAction() {
    super(REIMPLEMENT);
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("redo.parcelable.intention.text");
  }
}
