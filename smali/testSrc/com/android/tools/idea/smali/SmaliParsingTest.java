/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.smali;

import com.android.tools.idea.smali.psi.SmaliClassName;
import com.android.tools.idea.smali.psi.SmaliClassSpec;
import com.android.tools.idea.smali.psi.SmaliFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Tests for smali parsing.
 */
public class SmaliParsingTest extends IdeaTestCase {
  public void testClassDefinitionParsing() {
    String text = ".class public Lcom/example/SanAngeles/TestClass;\n" +
                  ".super Lcom/example/SanAngeles/DemoActivity;\n" +
                  ".source \"TestClass.java\"";
    SmaliFile smaliFile = parse(text);

    Collection<SmaliClassSpec> smaliClassSpecs = findChildrenOfType(smaliFile, SmaliClassSpec.class);
    assertThat(smaliClassSpecs).hasSize(1);
    SmaliClassSpec smaliClassSpec = getFirstItem(smaliClassSpecs);
    SmaliClassName className = smaliClassSpec.getClassName();
    assertEquals("com.example.SanAngeles.TestClass", className.getJavaClassName());
  }

  @NotNull
  private SmaliFile parse(@NotNull String text) {
    PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText(SmaliLanguage.getInstance(), text);
    assertThat(psiFile).isInstanceOf(SmaliFile.class);
    return (SmaliFile)psiFile;
  }
}
