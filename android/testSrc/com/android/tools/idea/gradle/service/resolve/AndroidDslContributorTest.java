/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.resolve;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.Set;

/**
 * {@link AndroidDslContributorTest} tests that various elements in the Gradle build script are
 * being resolved to their appropriate elements in the gradle model. We have this as a
 * {@link AndroidGradleTestCase} instead of just a simple PSI test case since resolving these
 * requires that the Android Gradle plugin is actually in the classpath, and that happens
 * only if a Gradle project is actually imported by the IDE.
 */
public class AndroidDslContributorTest extends AndroidGradleTestCase {
  // This test fails on the build server with the error: "Assertion failed: Already disposed", but works fine locally
  // Temporarily disable this until we find the root cause.
  public void disabled_testResolutions() throws Exception {
    loadProject("projects/resolve/simple");
    PsiFile psiFile = getPsiFile("build.gradle");
    assertNotNull(psiFile);

    // symbols inside android block
    validateResolution(psiFile, "compileSdkVersion", "com.android.build.gradle.BaseExtension", "compileSdkVersion");
    validateResolution(psiFile, "buildToolsVersion", "com.android.build.gradle.BaseExtension", "buildToolsVersion");
    validateResolution(psiFile, "defaultConfig", "com.android.build.gradle.BaseExtension", "defaultConfig");

    // symbols inside defaultConfig
    validateResolution(psiFile, "minSdkVersion", "com.android.builder.DefaultProductFlavor", "setMinSdkVersion");

    // symbols inside a build type
    validateResolution(psiFile, "runProguard", "com.android.builder.DefaultBuildType", "setRunProguard");
    validateResolution(psiFile, "proguardFiles", "com.android.build.gradle.internal.dsl.BuildTypeDsl", "proguardFiles");

    // symbols inside a product flavor
    validateResolution(psiFile, "packageName", "com.android.builder.DefaultProductFlavor", "setPackageName");

    // symbols inside lintOptions
    validateResolution(psiFile, "quiet", "com.android.build.gradle.internal.dsl.LintOptionsImpl", "setQuiet");

    // symbols inside signingConfigs
    validateResolution(psiFile, "storeFile", "com.android.builder.signing.DefaultSigningConfig", "setStoreFile");

    // symbols inside sourceSets
    validateResolution(psiFile, "aidl", "com.android.build.gradle.api.AndroidSourceSet", "getAidl");
    validateResolution(psiFile, "setRoot", "com.android.build.gradle.api.AndroidSourceSet", "setRoot");

    validateNoResolution(psiFile, "publishNonDefault");
  }

  // This test fails on the build server with the error: "Assertion failed: Already disposed", but works fine locally
  // Temporarily disable this until we find the root cause.
  public void disabled_testResolutionsInLibrary() throws Exception {
    if (isRunningOnHudson()) {
      logTestDisabled();
      return;
    }

    loadProject("projects/resolve/simple");
    PsiFile psiFile = getPsiFile("lib.gradle");
    assertNotNull(psiFile);

    validateResolution(psiFile, "publishNonDefault", "com.android.build.gradle.LibraryExtension", "publishNonDefault");
  }

  private void logTestDisabled() {
    System.out.println("The test: " + getTestName(false) + " is temporarily disabled on the build server.");
  }

  private static boolean isRunningOnHudson() {
    return System.getenv("HUDSON_HOME") != null;
  }

  private static void validateNoResolution(PsiFile psiFile, String symbol) {
    PsiReference ref = getPsiReference(psiFile, symbol);
    assert ref instanceof GrReferenceExpression : symbol;

    GrReferenceExpression referenceExpression = (GrReferenceExpression)ref;
    PsiElement element = referenceExpression.advancedResolve().getElement();
    assertNull(element);
  }

  // tests that the given symbol in the given psi file resolves to the given method and class
  // Note: This method just does a substring match within the given file to locate the symbol,
  // so make sure that symbols that are being searched for aren't duplicated (in which it'll always use the first one)
  private static void validateResolution(PsiFile psiFile, String symbol, String fqcn, String methodName) {
    PsiReference ref = getPsiReference(psiFile, symbol);
    assert ref instanceof GrReferenceExpression : symbol;

    GrReferenceExpression referenceExpression = (GrReferenceExpression)ref;
    PsiElement element = referenceExpression.advancedResolve().getElement();

    if (element instanceof GrLightMethodBuilder) {
      element = element.getNavigationElement();
    }

    assert element instanceof PsiMethod;
    PsiMethod psiMethod = (PsiMethod)element;
    PsiClass cls = psiMethod.getContainingClass();

    assertNotNull("Unable to find containing class for " + symbol, cls);
    assertEquals("Class names don't match while resolving " + symbol, fqcn, cls.getQualifiedName());
    assertEquals("Method names don't match while resolving " + symbol, methodName, psiMethod.getName());
  }

  // This test fails on the build server with the error: "Assertion failed: Already disposed", but works fine locally
  // Temporarily disable this until we find the root cause.
  public void disabled_testCompletions() throws Exception {
    loadProject("projects/resolve/simple");

    assertHasCompletions("completion/comp.gradle", "compileSdkVersion", "compileOptions");
    assertHasCompletions("completion/suffix.gradle", "packageNameSuffix", "versionNameSuffix");
    assertHasCompletions("completion/ndk.gradle", "ndk", "ndkConfig", "renderscriptNdkMode");
  }

  private void assertHasCompletions(String path, String... expectedCompletions) throws Exception {
    VirtualFile file = getProject().getBaseDir().findFileByRelativePath(path);
    assertNotNull(file);

    myFixture.configureFromExistingVirtualFile(file);

    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertNotNull(elements);

    Set<String> suggestions = Sets.newHashSetWithExpectedSize(elements.length);
    for (LookupElement element : elements) {
      suggestions.add(element.getLookupString());
    }

    for (String expected : expectedCompletions) {
      String msg = String.format("%1$s not in available completions: {%2$s}", expected, Joiner.on(',').join(suggestions));
      assertTrue(msg, suggestions.contains(expected));
    }
  }

  @Nullable
  private GroovyFile getPsiFile(String path) throws Exception {
    VirtualFile buildFile = getProject().getBaseDir().findChild(path);
    assertNotNull(buildFile);

    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(buildFile);
    assertTrue(psiFile instanceof GroovyFile);

    return (GroovyFile)psiFile;
  }

  @Nullable
  private static PsiReference getPsiReference(PsiFile psiFile, String element) {
    int offset = psiFile.getText().indexOf(element);
    if (offset < 0) {
      fail("Symbol " + element + " not found in file.");
    }
    return psiFile.findReferenceAt(offset);
  }
}
