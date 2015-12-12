/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;

/**
 * Tests for {@link ProductFlavorsDslElement}.
 *
 * <p>Both {@code android.defaultConfig {}} and {@code android.productFlavors.xyz {}} uses the same structure with same attributes.
 * In this test, we only test the general structure of {@code android.productFlavors {}}. The product flavor structure defined by
 * {@link ProductFlavorModel} is tested in great deal to cover all combinations in {@link ProductFlavorModelTest} using the
 * {@code android.defaultConfig {}} block.
 */
public class ProductFlavorsElementTest extends GradleFileModelTestCase {
  public void testProductFlavorsWithApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  productFlavors {\n" +
                  "    flavor1 {\n" +
                  "      applicationId \"com.example.myFlavor1\"\n" +
                  "      proguardFiles 'proguard-android-1.txt', 'proguard-rules-1.txt'\n" +
                  "      testInstrumentationRunnerArguments key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "    flavor2 {\n" +
                  "      applicationId \"com.example.myFlavor2\"\n" +
                  "      proguardFiles 'proguard-android-2.txt', 'proguard-rules-2.txt'\n" +
                  "      testInstrumentationRunnerArguments key3:\"value3\", key4:\"value4\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    Collection<ProductFlavorModel> productFlavors = getGradleBuildModel().android().productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());

    boolean flavor1Found = false;
    boolean flavor2Found = false;
    for (ProductFlavorModel flavor : productFlavors) {
      if ("flavor1".equals(flavor.name())) {
        flavor1Found = true;
        assertEquals("applicationId", "com.example.myFlavor1", flavor.applicationId());
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt"), flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2"),
                     flavor.testInstrumentationRunnerArguments());
      }
      else if ("flavor2".equals(flavor.name())) {
        flavor2Found = true;
        assertEquals("applicationId", "com.example.myFlavor2", flavor.applicationId());
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt"), flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key3", "value3", "key4", "value4"),
                     flavor.testInstrumentationRunnerArguments());
      }
    }
    assertTrue("flavor1", flavor1Found);
    assertTrue("flavor2", flavor2Found);
  }

  public void testProductFlavorsWithAssignmentStatements() throws Exception {
    String text = "android.productFlavors {\n" +
                  "  flavor1 {\n" +
                  "    applicationId = \"com.example.myFlavor1\"\n" +
                  "    proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.txt']\n" +
                  "    testInstrumentationRunnerArguments = [key1:\"value1\", key2:\"value2\"]\n" +
                  "  }\n" +
                  "  flavor2 {\n" +
                  "    applicationId = \"com.example.myFlavor2\"\n" +
                  "    proguardFiles = ['proguard-android-2.txt', 'proguard-rules-2.txt']\n" +
                  "    testInstrumentationRunnerArguments = [key3:\"value3\", key4:\"value4\"]\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    Collection<ProductFlavorModel> productFlavors = getGradleBuildModel().android().productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());

    boolean flavor1Found = false;
    boolean flavor2Found = false;
    for (ProductFlavorModel flavor : productFlavors) {
      if ("flavor1".equals(flavor.name())) {
        flavor1Found = true;
        assertEquals("applicationId", "com.example.myFlavor1", flavor.applicationId());
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt"), flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2"),
                     flavor.testInstrumentationRunnerArguments());
      }
      else if ("flavor2".equals(flavor.name())) {
        flavor2Found = true;
        assertEquals("applicationId", "com.example.myFlavor2", flavor.applicationId());
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt"), flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key3", "value3", "key4", "value4"),
                     flavor.testInstrumentationRunnerArguments());
      }
    }
    assertTrue("flavor1", flavor1Found);
    assertTrue("flavor2", flavor2Found);
  }

  public void testProductFlavorsWithOverrideStatements() throws Exception {
    String text = "android {\n" +
                  "  productFlavors {\n" +
                  "    flavor1 {\n" +
                  "      applicationId \"com.example.myFlavor1\"\n" +
                  "      proguardFiles 'proguard-android-1.txt', 'proguard-rules-1.txt'\n" +
                  "      testInstrumentationRunnerArguments key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "    flavor2 {\n" +
                  "      applicationId = \"com.example.myFlavor2\"\n" +
                  "      proguardFiles 'proguard-android-2.txt', 'proguard-rules-2.txt'\n" +
                  "      testInstrumentationRunnerArguments key3:\"value3\", key4:\"value4\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "  productFlavors.flavor1 {\n" +
                  "    applicationId = \"com.example.myFlavor1-1\"\n" +
                  "  }\n" +
                  "  productFlavors.flavor2 {\n" +
                  "    proguardFiles = ['proguard-android-4.txt', 'proguard-rules-4.txt']\n" +
                  "  }\n" +
                  " productFlavors {\n" +
                  "  flavor1.testInstrumentationRunnerArguments = [key5:\"value5\", key6:\"value6\"]\n" +
                  "  flavor2.applicationId = \"com.example.myFlavor2-1\"\n" +
                  " }\n" +
                  "}\n" +
                  "android.productFlavors.flavor1.proguardFiles = ['proguard-android-3.txt', 'proguard-rules-3.txt']\n" +
                  "android.productFlavors.flavor2.testInstrumentationRunnerArguments = [key7:\"value7\", key8:\"value8\"]";

    writeToBuildFile(text);

    Collection<ProductFlavorModel> productFlavors = getGradleBuildModel().android().productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());

    boolean flavor1Found = false;
    boolean flavor2Found = false;
    for (ProductFlavorModel flavor : productFlavors) {
      if ("flavor1".equals(flavor.name())) {
        flavor1Found = true;
        assertEquals("applicationId", "com.example.myFlavor1-1", flavor.applicationId());
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-3.txt", "proguard-rules-3.txt"), flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key5", "value5", "key6", "value6"),
                     flavor.testInstrumentationRunnerArguments());
      }
      else if ("flavor2".equals(flavor.name())) {
        flavor2Found = true;
        assertEquals("applicationId", "com.example.myFlavor2-1", flavor.applicationId());
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-4.txt", "proguard-rules-4.txt"), flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key7", "value7", "key8", "value8"),
                     flavor.testInstrumentationRunnerArguments());
      }
    }
    assertTrue("flavor1", flavor1Found);
    assertTrue("flavor2", flavor2Found);
  }

  public void testProductFlavorsWithAppendStatements() throws Exception {
    String text = "android {\n" +
                  "  productFlavors {\n" +
                  "    flavor1 {\n" +
                  "      proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.txt']\n" +
                  "      testInstrumentationRunnerArguments key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "    flavor2 {\n" +
                  "      proguardFiles 'proguard-android-2.txt', 'proguard-rules-2.txt'\n" +
                  "      testInstrumentationRunnerArguments = [key3:\"value3\", key4:\"value4\"]\n" +
                  "    }\n" +
                  "  }\n" +
                  "  productFlavors.flavor1 {\n" +
                  "    proguardFiles 'proguard-android-3.txt', 'proguard-rules-3.txt'\n" +
                  "  }\n" +
                  "  productFlavors.flavor2 {\n" +
                  "    testInstrumentationRunnerArguments.key6 \"value6\"\n" +
                  "  }\n" +
                  " productFlavors {\n" +
                  "  flavor2.proguardFile 'proguard-android-4.txt'\n" +
                  " }\n" +
                  "}\n" +
                  "android.productFlavors.flavor1.testInstrumentationRunnerArguments.key5 = \"value5\"";

    writeToBuildFile(text);

    Collection<ProductFlavorModel> productFlavors = getGradleBuildModel().android().productFlavors();
    assertNotNull(productFlavors);
    assertEquals("productFlavors", 2, productFlavors.size());

    boolean flavor1Found = false;
    boolean flavor2Found = false;
    for (ProductFlavorModel flavor : productFlavors) {
      if ("flavor1".equals(flavor.name())) {
        flavor1Found = true;
        assertEquals("proguardFiles",
                     ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt", "proguard-android-3.txt", "proguard-rules-3.txt"),
                     flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key1", "value1", "key2", "value2", "key5", "value5"),
                     flavor.testInstrumentationRunnerArguments());
      }
      else if ("flavor2".equals(flavor.name())) {
        flavor2Found = true;
        assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt", "proguard-android-4.txt"),
                     flavor.proguardFiles());
        assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("key3", "value3", "key4", "value4", "key6", "value6"),
                     flavor.testInstrumentationRunnerArguments());
      }
    }
    assertTrue("flavor1", flavor1Found);
    assertTrue("flavor2", flavor2Found);
  }
}
