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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.SourceFileDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceFileModel extends GradleDslBlockModel {
  @NonNls private static final String SRC_FILE = "srcFile";
  public SourceFileModel(@NotNull SourceFileDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Nullable
  public String srcFile() {
    return myDslElement.getProperty(SRC_FILE, String.class);
  }

  @NotNull
  public SourceFileModel setSrcFile(@NotNull String srcFile) {
    myDslElement.setNewLiteral(SRC_FILE, srcFile);
    return this;
  }

  @NotNull
  public SourceFileModel removeSrcFile() {
    myDslElement.removeProperty(SRC_FILE);
    return this;
  }
}
