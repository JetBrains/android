/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile;
import org.jetbrains.kotlin.asJava.KtClsJavaBasedLightClass;

public class ClassFileKtSourceFinder extends SourceFileInWorkspaceFinderBase {

  public ClassFileKtSourceFinder(KtClsFile clsFile) {
    super((PsiFile)clsFile);
  }

  @Override
  ImmutableSet<String> getSourceFileNamesFromClasses() {
    return stream(((KtClsFile)clsFile).getClasses())
      .filter(KtClsJavaBasedLightClass.class::isInstance)
      .map(KtClsJavaBasedLightClass.class::cast)
      .map(KtClsJavaBasedLightClass::getClsDelegate)
      .filter(ClsClassImpl.class::isInstance)
      .map(ClsClassImpl.class::cast)
      .map(ClsClassImpl::getSourceFileName)
      .collect(toImmutableSet());
  }
}
