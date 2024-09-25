/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.producers;

import static com.google.common.truth.Truth8.assertThat;

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiFile;
import java.util.Optional;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for identifying the test size of Kotlin tests. */
@RunWith(JUnit4.class)
public class KotlinTestSizeFinderTest extends BlazeIntegrationTestCase {
  @Before
  public final void setup() {
    // Required for IntelliJ to recognize annotations, JUnit version, etc.
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runner/RunWith.java"),
        "package org.junit.runner;",
        "public @interface RunWith {",
        "    Class<? extends Runner> value();",
        "}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/Test.java"),
        "package org.junit;",
        "public @interface Test {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runners/JUnit4.java"),
        "package org.junit.runners;",
        "public class JUnit4 {}");
    workspace.createPsiFile(
        new WorkspacePath("com/google/testing/testsize/MediumTest.java"),
        "package com.google.testing.testsize;",
        "public @interface MediumTest {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/experimental/categories/Category.java"),
        "package org.junit.experimental.categories;",
        "public @interface Category {",
        "     Class<?>[] value();",
        "}");
  }

  @Test
  public void smallTestIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.SmallTest;",
            /*annotation=*/ "@SmallTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.SMALL);
  }

  @Test
  public void mediumTestIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.MediumTest;",
            /*annotation=*/ "@MediumTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void largeTestIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.LargeTest;",
            /*annotation=*/ "@LargeTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.LARGE);
  }

  @Test
  public void enormousTestIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.EnormousTest;",
            /*annotation=*/ "@EnormousTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.ENORMOUS);
  }

  @Test
  public void annotationMayBeFullyQualified() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "", /*annotation=*/ "@com.google.testing.testsize.MediumTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void annotationMayRelyOnImport() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.MediumTest;",
            /*annotation=*/ "@MediumTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void annotationOnClassLevelIsRecognizedForClass() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.MediumTest;",
            /*annotation=*/ "@MediumTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void annotationOnMethodLevelIsIgnoredForClass() {
    PsiFile testFile =
        createTestKotlinFileWithMethodAnnotation(
            /*importLine=*/ "", /*annotation=*/ "@com.google.testing.testsize.MediumTest");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).isEmpty();
  }

  @Test
  public void annotationOnClassLevelIsRecognizedForMethod() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.MediumTest;",
            /*annotation=*/ "@MediumTest");
    KtNamedFunction testMethod = findFirstMethod(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testMethod);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void annotationOnMethodLevelIsRecognizedForMethod() {
    PsiFile testFile =
        createTestKotlinFileWithMethodAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.MediumTest;",
            /*annotation=*/ "@MediumTest");
    KtNamedFunction testMethod = findFirstMethod(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testMethod);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void annotationOnMethodLevelTakesPrecedenceForMethod() {
    PsiFile testFile =
        createTestKotlinFileWithAnnotations(
            /*importLine=*/ "",
            /*classAnnotation=*/ "@com.google.testing.testsize.SmallTest",
            /*methodAnnotation=*/ "@com.google.testing.testsize.MediumTest");
    KtNamedFunction testMethod = findFirstMethod(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testMethod);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void smallTestCategoryIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.SmallTest;\n"
                + "import org.junit.experimental.categories.Category;",
            /*annotation=*/ "@Category(SmallTest::class)");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.SMALL);
  }

  @Test
  public void mediumTestCategoryIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.MediumTest;\n"
                + "import org.junit.experimental.categories.Category;",
            /*annotation=*/ "@Category(MediumTest::class)");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.MEDIUM);
  }

  @Test
  public void largeTestCategoryIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.LargeTest;\n"
                + "import org.junit.experimental.categories.Category;",
            /*annotation=*/ "@Category(LargeTest::class)");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.LARGE);
  }

  @Test
  public void enormousTestCategoryIsRecognized() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.EnormousTest;\n"
                + "import org.junit.experimental.categories.Category;",
            /*annotation=*/ "@Category(EnormousTest::class)");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.ENORMOUS);
  }

  @Test
  public void categoryAnnotationMayBeFullyQualified() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "",
            /*annotation=*/ "@org.junit.experimental.categories.Category(com.google.testing.testsize.SmallTest.SmallTest::class)");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.SMALL);
  }

  @Test
  public void categoryAnnotationMayRelyOnImport() {
    PsiFile testFile =
        createTestKotlinFileWithClassAnnotation(
            /*importLine=*/ "import com.google.testing.testsize.SmallTest;\n"
                + "import org.junit.experimental.categories.Category;",
            /*annotation=*/ "@Category(SmallTest::class)");
    KtClass testClass = findClass(testFile);

    Optional<TestSize> testSize = KotlinTestSizeFinder.getTestSize(testClass);
    assertThat(testSize).hasValue(TestSize.SMALL);
  }

  private PsiFile createTestKotlinFileWithClassAnnotation(String importLine, String annotation) {
    return createTestKotlinFileWithAnnotations(
        importLine, /*classAnnotation=*/ annotation, /*methodAnnotation=*/ "");
  }

  private PsiFile createTestKotlinFileWithMethodAnnotation(String importLine, String annotation) {
    return createTestKotlinFileWithAnnotations(
        importLine, /*classAnnotation=*/ "", /*methodAnnotation=*/ annotation);
  }

  private PsiFile createTestKotlinFileWithAnnotations(
      String importLine, String classAnnotation, String methodAnnotation) {
    String testFilePath = "com/google/test/TestClass.kt";
    return workspace.createPsiFile(
        new WorkspacePath(testFilePath),
        "package com.google.test;",
        importLine,
        classAnnotation,
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4::class)",
        "class TestClass {",
        "  @org.junit.Test",
        methodAnnotation,
        "  fun testMethod() {}",
        "}");
  }

  private static KtClass findClass(PsiFile kotlinFile) {
    KtClass kotlinClass = PsiUtils.findFirstChildOfClassRecursive(kotlinFile, KtClass.class);
    Preconditions.checkNotNull(kotlinClass, "KtClass not found in given test file.");
    Preconditions.checkNotNull(
        kotlinClass.getFqName(),
        "KtClass not properly parsed. Check the definition of the file for errors.");
    return kotlinClass;
  }

  private static KtNamedFunction findFirstMethod(PsiFile kotlinFile) {
    KtNamedFunction kotlinMethod =
        PsiUtils.findFirstChildOfClassRecursive(kotlinFile, KtNamedFunction.class);
    Preconditions.checkNotNull(kotlinMethod, "KtNamedFunction not found in given test file.");
    Preconditions.checkNotNull(
        kotlinMethod.getFqName(),
        "KtNamedFunction not properly parsed. Check the definition of the file for errors.");
    return kotlinMethod;
  }
}
