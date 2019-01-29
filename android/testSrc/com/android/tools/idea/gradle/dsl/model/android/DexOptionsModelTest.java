/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.DexOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link DexOptionsModel}.
 */
public class DexOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsInApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    javaMaxHeapSize '2048m'\n" +
                  "    jumboMode true\n" +
                  "    keepRuntimeAnnotatedClasses false\n" +
                  "    maxProcessCount 10\n" +
                  "    optimize true\n" +
                  "    preDexLibraries false\n" +
                  "    threadCount 5\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());
  }

  @Test
  public void testParseElementsInAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "    additionalParameters = ['ijkl', 'mnop']\n" +
                  "    javaMaxHeapSize = '1024m'\n" +
                  "    jumboMode = false\n" +
                  "    keepRuntimeAnnotatedClasses = true\n" +
                  "    maxProcessCount = 5\n" +
                  "    optimize = false\n" +
                  "    preDexLibraries = true\n" +
                  "    threadCount = 10\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("ijkl", "mnop"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "1024m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.FALSE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.TRUE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(5), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.FALSE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.TRUE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(10), dexOptions.threadCount());
  }

  @Test
  public void testEditElements() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    javaMaxHeapSize '2048m'\n" +
                  "    jumboMode true\n" +
                  "    keepRuntimeAnnotatedClasses false\n" +
                  "    maxProcessCount 10\n" +
                  "    optimize true\n" +
                  "    preDexLibraries false\n" +
                  "    threadCount 5\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());

    dexOptions.additionalParameters().getListValue("efgh").setValue("xyz");
    dexOptions.javaMaxHeapSize().setValue("1024m");
    dexOptions.jumboMode().setValue(false);
    dexOptions.keepRuntimeAnnotatedClasses().setValue(true);
    dexOptions.maxProcessCount().setValue(5);
    dexOptions.optimize().setValue(false);
    dexOptions.preDexLibraries().setValue(true);
    dexOptions.threadCount().setValue(10);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "xyz"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "1024m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.FALSE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.TRUE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(5), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.FALSE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.TRUE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(10), dexOptions.threadCount());
  }

  @Test
  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertMissingProperty("additionalParameters", dexOptions.additionalParameters());
    assertMissingProperty("javaMaxHeapSize", dexOptions.javaMaxHeapSize());
    assertMissingProperty("jumboMode", dexOptions.jumboMode());
    assertMissingProperty("keepRuntimeAnnotatedClasses", dexOptions.keepRuntimeAnnotatedClasses());
    assertMissingProperty("maxProcessCount", dexOptions.maxProcessCount());
    assertMissingProperty("optimize", dexOptions.optimize());
    assertMissingProperty("preDexLibraries", dexOptions.preDexLibraries());
    assertMissingProperty("threadCount", dexOptions.threadCount());

    dexOptions.additionalParameters().addListValue().setValue("abcd");
    dexOptions.javaMaxHeapSize().setValue("2048m");
    dexOptions.jumboMode().setValue(true);
    dexOptions.keepRuntimeAnnotatedClasses().setValue(false);
    dexOptions.maxProcessCount().setValue(10);
    dexOptions.optimize().setValue(true);
    dexOptions.preDexLibraries().setValue(false);
    dexOptions.threadCount().setValue(5);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());
  }

  @Test
  public void testRemoveElements() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    javaMaxHeapSize '2048m'\n" +
                  "    jumboMode true\n" +
                  "    keepRuntimeAnnotatedClasses false\n" +
                  "    maxProcessCount 10\n" +
                  "    optimize true\n" +
                  "    preDexLibraries false\n" +
                  "    threadCount 5\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    checkForValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());
    assertEquals("javaMaxHeapSize", "2048m", dexOptions.javaMaxHeapSize());
    assertEquals("jumboMode", Boolean.TRUE, dexOptions.jumboMode());
    assertEquals("keepRuntimeAnnotatedClasses", Boolean.FALSE, dexOptions.keepRuntimeAnnotatedClasses());
    assertEquals("maxProcessCount", Integer.valueOf(10), dexOptions.maxProcessCount());
    assertEquals("optimize", Boolean.TRUE, dexOptions.optimize());
    assertEquals("preDexLibraries", Boolean.FALSE, dexOptions.preDexLibraries());
    assertEquals("threadCount", Integer.valueOf(5), dexOptions.threadCount());

    dexOptions.additionalParameters().delete();
    dexOptions.javaMaxHeapSize().delete();
    dexOptions.jumboMode().delete();
    dexOptions.keepRuntimeAnnotatedClasses().delete();
    dexOptions.maxProcessCount().delete();
    dexOptions.optimize().delete();
    dexOptions.preDexLibraries().delete();
    dexOptions.threadCount().delete();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    checkForInValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertMissingProperty("additionalParameters", dexOptions.additionalParameters());
    assertMissingProperty("javaMaxHeapSize", dexOptions.javaMaxHeapSize());
    assertMissingProperty("jumboMode", dexOptions.jumboMode());
    assertMissingProperty("keepRuntimeAnnotatedClasses", dexOptions.keepRuntimeAnnotatedClasses());
    assertMissingProperty("maxProcessCount", dexOptions.maxProcessCount());
    assertMissingProperty("optimize", dexOptions.optimize());
    assertMissingProperty("preDexLibraries", dexOptions.preDexLibraries());
    assertMissingProperty("threadCount", dexOptions.threadCount());
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), dexOptions.additionalParameters());

    dexOptions.additionalParameters().getListValue("abcd").delete();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("efgh"), dexOptions.additionalParameters());
  }

  @Test
  public void testRemoveOnlyElementInTheList() throws Exception {
    String text = "android {\n" +
                  "  dexOptions {\n" +
                  "    additionalParameters 'abcd'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DexOptionsModel dexOptions = android.dexOptions();
    checkForValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd"), dexOptions.additionalParameters());

    dexOptions.additionalParameters().getListValue("abcd").delete();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    checkForInValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertMissingProperty("additionalParameters", dexOptions.additionalParameters());
  }
}
