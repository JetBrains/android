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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

/**
 * A heuristic to derive the test size for Kotlin tests. The test size is especially relevant when
 * we map the test to its Blaze target (see {@link
 * com.google.idea.blaze.base.run.TestSizeHeuristic}).
 *
 * <p>This is the Kotlin variant of {@link com.google.idea.blaze.java.run.producers.TestSizeFinder}.
 * We need a separate implementation for Kotlin as the PSI classes of Java and Kotlin don't share a
 * common hierarchy.
 */
class KotlinTestSizeFinder {

  /**
   * Heuristic which maps a given annotation to the test size it indicates. Right now annotations
   * are identified based on their short name.
   *
   * <p>Ideally, we would test the qualified name of the annotation (-> prefixed with
   * com.google.testing.testsize) and not just the short name. Unfortunately, we haven't found a
   * good way to extract the qualified name of the annotation for a Kotlin file yet. The short name
   * should still be a good enough heuristic, so go with that in the meantime.
   */
  private static final ImmutableMap<String, TestSize> ANNOTATION_TO_TEST_SIZE_HEURISTIC =
      ImmutableMap.<String, TestSize>builder()
          .put("SmallTest", TestSize.SMALL)
          .put("MediumTest", TestSize.MEDIUM)
          .put("LargeTest", TestSize.LARGE)
          .put("EnormousTest", TestSize.ENORMOUS)
          .build();

  /**
   * Additional heuristic for when the test size is indicated via the value of the annotation. Right
   * now, we check the text of the value of the annotation entry.
   *
   * <p>{@link com.google.idea.blaze.java.run.producers.TestSizeFinder} indicates why the text
   * comparison was chosen instead of checking the value directly. For Kotlin, we opt to use the
   * same approach as for Java, especially as getting the resolved value of the annotation turned
   * out to be tricky.
   */
  private static final ImmutableMap<Pattern, TestSize> CATEGORY_ANNOTATION_HEURISTIC =
      ANNOTATION_TO_TEST_SIZE_HEURISTIC.entrySet().stream()
          .collect(
              ImmutableMap.toImmutableMap(
                  entry -> getCategoryPattern(entry.getKey()), Entry::getValue));

  private static Pattern getCategoryPattern(String sizeAnnotationName) {
    return Pattern.compile("@.*Category(.*" + sizeAnnotationName + "::class)");
  }

  /**
   * Derives the test size for a given Kotlin method.
   *
   * @param ktFunction the Kotlin method in IntelliJ's PSI
   * @return the test size for the Kotlin method if the Kotlin file contains any indication for the
   *     test size
   */
  public static Optional<TestSize> getTestSize(KtNamedFunction ktFunction) {
    List<KtAnnotationEntry> annotationEntries = ktFunction.getAnnotationEntries();
    Optional<TestSize> testSize = getTestSizeFromAnnotation(annotationEntries);
    if (testSize.isPresent()) {
      return testSize;
    }
    return getEnclosingClass(ktFunction).flatMap(KotlinTestSizeFinder::getTestSize);
  }

  /**
   * Derives the test size for a given Kotlin class.
   *
   * @param ktClass the Kotlin class in IntelliJ's PSI
   * @return the test size for the Kotlin class if the Kotlin file contains any indication for the
   *     test size
   */
  public static Optional<TestSize> getTestSize(KtClass ktClass) {
    List<KtAnnotationEntry> annotationEntries = ktClass.getAnnotationEntries();
    Optional<TestSize> testSize = getTestSizeFromAnnotation(annotationEntries);
    if (testSize.isPresent()) {
      return testSize;
    }
    return getTestSizeFromAnnotationText(annotationEntries);
  }

  private static Optional<KtClass> getEnclosingClass(KtNamedFunction ktFunction) {
    return Optional.ofNullable(
        PsiUtils.getParentOfType(ktFunction, KtClass.class, /* strict= */ false));
  }

  private static Optional<TestSize> getTestSizeFromAnnotation(List<KtAnnotationEntry> annotations) {
    return annotations.stream()
        .map(KtAnnotationEntry::getShortName)
        .filter(Objects::nonNull)
        .map(Name::asString)
        .map(ANNOTATION_TO_TEST_SIZE_HEURISTIC::get)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private static Optional<TestSize> getTestSizeFromAnnotationText(
      List<KtAnnotationEntry> annotations) {
    return annotations.stream()
        .map(KtAnnotationEntry::getText)
        .map(KotlinTestSizeFinder::getTestSizeForAnnotationText)
        .flatMap(Streams::stream)
        .findFirst();
  }

  private static Optional<TestSize> getTestSizeForAnnotationText(String annotationText) {
    return CATEGORY_ANNOTATION_HEURISTIC.entrySet().stream()
        .filter(entry -> entry.getKey().matcher(annotationText).find())
        .findFirst()
        .map(Entry::getValue);
  }

  private KotlinTestSizeFinder() {
    throw new AssertionError();
  }
}
