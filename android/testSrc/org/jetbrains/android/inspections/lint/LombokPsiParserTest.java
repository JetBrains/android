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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.ast.CompilationUnit;
import lombok.ast.TypeDeclaration;
import org.jetbrains.android.AndroidTestCase;

import static com.android.tools.lint.client.api.JavaParser.*;

public class LombokPsiParserTest extends AndroidTestCase {
  public void testResolve() {
    String source1Path = "src/p1/p2/Target.java";
    String source1 = "package p1.p2;\n" +
                     "\n" +
                     "public class Target {\n" +
                     "    public void foo(int f) {\n" +
                     "    }\n" +
                     "    public int myField = 5;\n" +
                     "    public static class Target2 extends java.io.File {\n" +
                     "        public Target2() {\n" +
                     "            super(null, null);\n" +
                     "        }\n" +
                     "    }\n" +
                     "}";
    myFixture.addFileToProject(source1Path, source1);

    String source2Path = "src/p1/p2/Caller.java";
    String source2 = "package p1.p2;\n" +
                     "\n" +
                     "public final class Caller extends Target.Target2 {\n" +
                     "    public String call(Target target) {\n" +
                     "        target.foo(42);\n" +
                     "        target.myField = 6;\n" +
                     "    }\n" +
                     "}";
    PsiFile file2 = myFixture.addFileToProject(source2Path, source2);
    PsiCall call = PsiTreeUtil.findChildrenOfType(file2, PsiCall.class).iterator().next();
    ResolvedNode resolved = LombokPsiParser.resolve(call);
    assertNotNull(resolved);
    assertTrue(resolved instanceof ResolvedMethod);
    ResolvedMethod method = (ResolvedMethod)resolved;
    assertEquals("foo", method.getName());
    assertEquals("p1.p2.Target", method.getContainingClass().getName());
    assertTrue(method.isInPackage("p1.p2", true));
    assertTrue(method.isInPackage("p1.p2", false));
    assertTrue(method.isInPackage("p1", true));
    assertFalse(method.isInPackage("p1", false));

    // Resolve "target" as a reference expression in the call() method
    PsiElement element = ((PsiMethodCallExpression)call).getMethodExpression().getQualifier();
    assertNotNull(element);
    resolved = LombokPsiParser.resolve(element);
    assertTrue(resolved instanceof ResolvedVariable);
    ResolvedVariable resolvedVariable = (ResolvedVariable)resolved;
    assertEquals("target", resolvedVariable.getName());
    assertEquals("p1.p2.Target", resolvedVariable.getType().getName());


    PsiMethod callMethod = PsiTreeUtil.findChildrenOfType(file2, PsiMethod.class).iterator().next();
    assertNotNull(callMethod);
    PsiVariable variable = callMethod.getParameterList().getParameters()[0];
    resolved = LombokPsiParser.resolve(variable);
    assertTrue(resolved instanceof ResolvedVariable);
    resolvedVariable = (ResolvedVariable)resolved;
    assertEquals("target", resolvedVariable.getName());
    assertEquals("p1.p2.Target", resolvedVariable.getType().getName());

    @SuppressWarnings("ConstantConditions")
    PsiStatement fieldReferenceStatement = callMethod.getBody().getStatements()[1];
    PsiExpression lExpression =
      ((PsiAssignmentExpression)((PsiExpressionStatement)fieldReferenceStatement).getExpression()).getLExpression();
    resolved = LombokPsiParser.resolve(lExpression);
    assertTrue(resolved instanceof ResolvedField);
    ResolvedField field = (ResolvedField)resolved;
    assertEquals("myField", field.getName());
    assertEquals("int", field.getType().getName());
    assertEquals("p1.p2.Target", field.getContainingClass().getName());
    assertTrue(field.isInPackage("p1.p2", true));
    assertTrue(field.isInPackage("p1.p2", false));
    assertTrue(field.isInPackage("p1", true));
    assertFalse(field.isInPackage("p1", false));

    PsiClass cls = PsiTreeUtil.findChildrenOfType(file2, PsiClass.class).iterator().next();
    @SuppressWarnings("ConstantConditions")
    PsiJavaCodeReferenceElement superReference = cls.getExtendsList().getReferenceElements()[0];
    resolved = LombokPsiParser.resolve(superReference);
    assertTrue(resolved instanceof ResolvedClass);
    ResolvedClass rc = (ResolvedClass)resolved;
    assertTrue(rc.getName().equals("p1.p2.Target.Target2"));
    assertTrue(rc.isInPackage("p1.p2", true));
    assertTrue(rc.isInPackage("p1.p2", false));
    assertTrue(rc.isInPackage("p1", true));
    assertFalse(rc.isInPackage("p1", false));

    CompilationUnit unit = LombokPsiConverter.convert((PsiJavaFile)file2);
    //noinspection ConstantConditions
    TypeDeclaration first = unit.astTypeDeclarations().first();
    resolved = LombokPsiParser.resolve((PsiElement)first.getNativeNode());
    assertTrue(resolved instanceof ResolvedClass);
    rc = (ResolvedClass)resolved;
    assertEquals("p1.p2.Caller", rc.getName());
    //noinspection ConstantConditions
    assertEquals("p1.p2.Target.Target2", rc.getSuperClass().getName());
    assertNull(rc.getContainingClass());
  }
}
