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
package com.android.tools.idea.gradle.editor.parser;

import com.android.tools.idea.gradle.editor.entity.AbstractSimpleGradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class GradleEditorParserTestUtil {

  private GradleEditorParserTestUtil() {
  }

  @NotNull
  public static String text(@NotNull GradleEditorSourceBinding binding) {
    VirtualFile file = binding.getFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      throw new IllegalStateException(String.format("Can't obtain a document for file %s (descriptor '%s')", file, binding));
    }
    RangeMarker marker = binding.getRangeMarker();
    return document.getCharsSequence().subSequence(marker.getStartOffset(), marker.getEndOffset()).toString();
  }

  @NotNull
  public static ExternalDependencyChecker externalDependency() {
    return ExternalDependencyChecker.create();
  }

  @NotNull
  public static <T extends AbstractSimpleGradleEditorEntity> SimpleEntityChecker<T> property(@NotNull String targetDescription) {
    return SimpleEntityChecker.create(targetDescription);
  }
}
