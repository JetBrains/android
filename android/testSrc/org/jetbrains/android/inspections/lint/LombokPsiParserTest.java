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
package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.JavaContext;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.ast.ClassDeclaration;
import lombok.ast.CompilationUnit;
import lombok.ast.MethodDeclaration;
import lombok.ast.Node;
import org.jetbrains.android.AndroidTestCase;

public class LombokPsiParserTest extends AndroidTestCase {
  public void testResolve() {
    String source1Path = "src/p1/p2/Target.java";
    String source1 = "package p1.p2;\n" +
                     "\n" +
                     "public final class Target {\n" +
                     "    public void foo(int f) {\n" +
                     "    }\n" +
                     "}";
    myFixture.addFileToProject(source1Path, source1);

    String source2Path = "src/p1/p2/Caller.java";
    String source2 = "package p1.p2;\n" +
                     "\n" +
                     "public final class Caller {\n" +
                     "    public String call(Target target) {\n" +
                     "        target.foo(42);\n" +
                     "    }\n" +
                     "}";
    PsiFile file2 = myFixture.addFileToProject(source2Path, source2);
    PsiCall call = PsiTreeUtil.findChildrenOfType(file2, PsiCall.class).iterator().next();
    Node resolved = LombokPsiParser.resolve(call);
    assertNotNull(resolved);
    assertTrue(resolved instanceof MethodDeclaration);
    MethodDeclaration method = (MethodDeclaration)resolved;
    assertEquals("foo", method.astMethodName().astValue());
    ClassDeclaration surroundingClass = JavaContext.findSurroundingClass(method);
    assertNotNull(surroundingClass);
    assertEquals("Target", surroundingClass.astName().astValue());
    assertTrue(surroundingClass.getParent() instanceof CompilationUnit);
    CompilationUnit unit = (CompilationUnit)surroundingClass.getParent();
    assertNotNull(unit.astPackageDeclaration());
    assertEquals("p1.p2", unit.astPackageDeclaration().getPackageName());
  }
}
