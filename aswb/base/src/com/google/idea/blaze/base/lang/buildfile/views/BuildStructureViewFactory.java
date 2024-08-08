/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.views;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;

/** PsiStructureViewFactory implementation */
public class BuildStructureViewFactory implements PsiStructureViewFactory {
  @Override
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    if (!(psiFile instanceof BuildFile)) {
      return null;
    }
    return new TreeBasedStructureViewBuilder() {
      @Override
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new BuildStructureViewModel((BuildFile) psiFile, editor);
      }
    };
  }
}
