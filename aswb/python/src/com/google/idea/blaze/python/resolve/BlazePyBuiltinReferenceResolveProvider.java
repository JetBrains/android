/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.resolve;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.python.PySdkUtils;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A copy of {@link com.jetbrains.python.psi.resolve.PythonBuiltinReferenceResolveProvider}, which
 * handles projects without a python SDK (i.e. with a python facet instead).
 */
public class BlazePyBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {

  @Override
  public List<RatedResolveResult> resolveName(
      PyQualifiedExpression element, TypeEvalContext context) {
    if (!Blaze.isBlazeProject(element.getProject())) {
      return ImmutableList.of();
    }
    PyBuiltinCache cache = getBuiltInCache(element);
    if (cache == null) {
      return ImmutableList.of();
    }
    String referencedName = element.getReferencedName();
    if (referencedName == null) {
      return ImmutableList.of();
    }
    List<RatedResolveResult> result = Lists.newArrayList();
    PyFile bfile = cache.getBuiltinsFile();
    if (bfile != null && !PyUtil.isClassPrivateName(referencedName)) {
      PsiElement resultElement = bfile.getElementNamed(referencedName);
      if (resultElement == null && "__builtins__".equals(referencedName)) {
        resultElement = bfile; // resolve __builtins__ reference
      }
      if (resultElement != null) {
        result.add(
            new ImportedResolveResult(
                resultElement, PyReferenceImpl.getRate(resultElement, context), null));
      }
    }
    return result;
  }

  @Nullable
  private static PyBuiltinCache getBuiltInCache(PyQualifiedExpression element) {
    final PsiElement realContext = PyPsiUtils.getRealContext(element);
    PsiFileSystemItem psiFile = realContext.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    Sdk sdk = PyBuiltinCache.findSdkForFile(psiFile);
    if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
      // this case is already handled by PythonBuiltinReferenceResolveProvider
      return null;
    }
    Sdk pythonSdk = PySdkUtils.getPythonSdk(psiFile.getProject());
    return pythonSdk != null
        ? PythonSdkPathCache.getInstance(psiFile.getProject(), pythonSdk).getBuiltins()
        : null;
  }
}
