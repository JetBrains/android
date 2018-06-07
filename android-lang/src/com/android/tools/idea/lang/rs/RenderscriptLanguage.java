/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.lang.rs;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;

import java.lang.String;

public class RenderscriptLanguage extends Language {
  public static final RenderscriptLanguage INSTANCE = new RenderscriptLanguage();

  @NonNls
  private static final String ID = "Renderscript";

  protected RenderscriptLanguage() {
    super(ID);
  }
}
