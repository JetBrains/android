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
package com.google.idea.blaze.android.sync.importer.problems;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import java.io.File;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GeneratedResourceClassifier}. */
@RunWith(JUnit4.class)
public class GeneratedResourceClassifierTest extends BlazeTestCase {

  @Test
  public void rankEmpty() {
    assertThat(GeneratedResourceClassifier.mayHaveNonStringTranslations(files())).isFalse();
  }

  @Test
  public void rankOneValues() {
    assertThat(GeneratedResourceClassifier.mayHaveNonStringTranslations(files("foo/res/values")))
        .isTrue();
  }

  @Test
  public void rankOneTranslation() {
    assertThat(GeneratedResourceClassifier.mayHaveNonStringTranslations(files("foo/res/values-af")))
        .isFalse();
  }

  @Test
  public void rankOneOtherType() {
    assertThat(GeneratedResourceClassifier.mayHaveNonStringTranslations(files("foo/res/raw")))
        .isTrue();
  }

  @Test
  public void onlyTranslations() {
    assertThat(
            GeneratedResourceClassifier.mayHaveNonStringTranslations(
                files("foo/res/values-af", "foo/res/values-ar", "foo/res/values-b+sr+Latn")))
        .isFalse();
  }

  @Test
  public void onlyTranslationsWithOther() {
    assertThat(
            GeneratedResourceClassifier.mayHaveNonStringTranslations(
                files(
                    "foo/res/values-af",
                    "foo/res/values-fr-rCA",
                    "foo/res/values-b+sr+Latn",
                    "foo/res/values-af-sw600dp",
                    "foo/res/values-fr-rCA-sw600dp",
                    "foo/res/values-b+sr+Latn-sw600dp")))
        .isFalse();
  }

  @Test
  public void mixedWithTranslationsAndDefaultValues() {
    assertThat(
            GeneratedResourceClassifier.mayHaveNonStringTranslations(
                files("foo/res/values", "foo/res/values-af", "foo/res/values-fr-rCA")))
        .isTrue();
  }

  @Test
  public void mixedWithTranslationsDrawableXml() {
    assertThat(
            GeneratedResourceClassifier.mayHaveNonStringTranslations(
                files(
                    "foo/res/drawable-xxhdpi",
                    "foo/res/values-af",
                    "foo/res/values-fr-rCA",
                    "foo/res/xml")))
        .isTrue();
  }

  @Test
  public void mixedWithIncorrectConfig() {
    assertThat(
            GeneratedResourceClassifier.mayHaveNonStringTranslations(
                files(
                    "foo/res/values-notaqualifier", "foo/res/values-af", "foo/res/values-fr-rCA")))
        .isTrue();
  }

  @Test
  public void mixedWithIncorrectFolder() {
    assertThat(
            GeneratedResourceClassifier.mayHaveNonStringTranslations(
                files("foo/res/php_scripts", "foo/res/values-af", "foo/res/values-fr-rCA")))
        .isTrue();
  }

  private static File[] files(String... paths) {
    return Arrays.stream(paths).map(File::new).toArray(File[]::new);
  }
}
