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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import java.lang.reflect.Constructor;

/**
 * IElementTypes used in the AST by the parser (as opposed to the types used by the lexer).<br>
 * Modelled on IntelliJ's java and python language support conventions.
 */
public class BuildElementType extends IElementType {

  private static final Class<?>[] PARAMETER_TYPES = new Class<?>[] {ASTNode.class};
  private final Class<? extends PsiElement> psiElementClass;
  private Constructor<? extends PsiElement> constructor;

  public BuildElementType(String name, Class<? extends PsiElement> psiElementClass) {
    super(name, BuildFileType.INSTANCE.getLanguage());
    this.psiElementClass = psiElementClass;
  }

  public PsiElement createElement(ASTNode node) {
    try {
      if (constructor == null) {
        constructor = psiElementClass.getConstructor(PARAMETER_TYPES);
      }
      return constructor.newInstance(node);
    } catch (Exception e) {
      throw new IllegalStateException("No necessary constructor for " + node.getElementType(), e);
    }
  }
}
