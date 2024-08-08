/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ideinfo;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import javax.annotation.Nullable;

/** Replaces import path from the aspect based on target label and kind. */
public interface ImportPathReplacer {
  ExtensionPointName<ImportPathReplacer> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.GoImportPathReplacer");

  boolean shouldReplace(@Nullable String existingImportPath);

  String getReplacement(Label label, Kind kind);

  @Nullable
  static String fixImportPath(@Nullable String oldImportPath, Label targetLabel, Kind targetKind) {
    for (ImportPathReplacer fixer : ImportPathReplacer.EP_NAME.getExtensions()) {
      if (fixer.shouldReplace(oldImportPath)) {
        return fixer.getReplacement(targetLabel, targetKind);
      }
    }
    return oldImportPath;
  }
}
