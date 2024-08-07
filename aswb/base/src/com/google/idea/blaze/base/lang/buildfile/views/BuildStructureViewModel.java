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
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;

/**
 * Implements structure view for a BUILD file. TODO: Include inner build rules for Skylark files
 * (when we can identify them -- e.g. via list of blaze rule types)
 */
public class BuildStructureViewModel extends StructureViewModelBase
    implements StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider {

  public BuildStructureViewModel(BuildFile psiFile, @Nullable Editor editor) {
    this(psiFile, editor, new BuildStructureViewElement(psiFile));
    withSorters(Sorter.ALPHA_SORTER);
    withSuitableClasses(FunctionStatement.class, LoadStatement.class, FuncallExpression.class);
  }

  public BuildStructureViewModel(
      PsiFile file, @Nullable Editor editor, StructureViewTreeElement element) {
    super(file, editor, element);
  }

  @Override
  public Filter[] getFilters() {
    return new Filter[] {new LoadStatementsFilter()};
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    final Object value = element.getValue();
    return value instanceof BuildFile;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return element.getValue() instanceof TargetExpression;
  }

  @Override
  public boolean shouldEnterElement(Object element) {
    return element instanceof BuildFile; // only show top-level elements
  }

  @Override
  public boolean isAutoExpand(StructureViewTreeElement element) {
    return element.getValue() instanceof PsiFile;
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }
}
