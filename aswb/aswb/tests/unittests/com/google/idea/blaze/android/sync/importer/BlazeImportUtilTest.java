/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeImportUtil}. */
@RunWith(JUnit4.class)
public class BlazeImportUtilTest {

  /** Test package inference when /java/ and /javatests/ are missing */
  @Test
  public void inferPackage_pathContainsNoPrefix() {
    String relativePath = "com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when java/ prefix is present */
  @Test
  public void inferPackage_pathContainsJavaPrefix() {
    String relativePath = "java/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when javatests/ prefix is present */
  @Test
  public void inferPackage_pathContainsJavatestsPrefix() {
    String relativePath = "javatests/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when /java/ is in the middle of the path */
  @Test
  public void inferPackage_pathContainsJava() {
    String relativePath = "some/random/path/java/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when /javatests/ is in the middle of the path */
  @Test
  public void inferPackage_pathContainsJavatests() {
    String relativePath = "some/random/path/javatests/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when both /java/ and /javatests/ are present. */
  @Test
  public void inferPackage_pathContainsJavaAndJavatests1() {
    String relativePath = "some/random/path/java/psych/javatests/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when both /java/ and /javatests/ are present. */
  @Test
  public void inferPackage_pathContainsJavaAndJavatests2() {
    String relativePath = "some/random/path/javatests/psych/java/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when both /java/ and /javatests/ are present. */
  @Test
  public void inferPackage_pathContainsJavaAndJavatests3() {
    String relativePath = "some/random/path/java/javatests/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when both /java/ and /javatests/ are present. */
  @Test
  public void inferPackage_pathContainsJavaAndJavatests4() {
    String relativePath = "some/random/path/javatests/java/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("com.package.name.with.slashes");
  }

  /** Test package inference when path contains .../java... */
  @Test
  public void inferPackage_pathContainsDirectoryPrefixJava() {
    String relativePath = "java_src/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("java_src.com.package.name.with.slashes");
  }

  /** Test package inference when path contains ...java/... */
  @Test
  public void inferPackage_pathContainsDirectorySuffixJava() {
    String relativePath = "whyjava/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("whyjava.com.package.name.with.slashes");
  }

  /** Test package inference when path contains .../javatests... */
  @Test
  public void inferPackage_pathContainsDirectoryPrefixJavatests() {
    String relativePath = "javatests_src/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("javatests_src.com.package.name.with.slashes");
  }

  /** Test package inference when path contains ...javatests/... */
  @Test
  public void inferPackage_pathContainsDirectorySuffixJavatests() {
    String relativePath = "whyjavatests/com/package/name/with/slashes";
    String inferredPackage = BlazeImportUtil.inferJavaResourcePackage(relativePath);
    assertThat(inferredPackage).isEqualTo("whyjavatests.com.package.name.with.slashes");
  }
}
