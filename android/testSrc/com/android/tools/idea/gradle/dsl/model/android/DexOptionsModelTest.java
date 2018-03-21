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

/**
 * Tests for {@link DexOptionsModel}.
 */
public class DexOptionsModelTest extends GradleFileModelTestCase {
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

    dexOptions.replaceAdditionalParameter("efgh", "xyz");
    dexOptions.setJavaMaxHeapSize("1024m");
    dexOptions.setJumboMode(false);
    dexOptions.setKeepRuntimeAnnotatedClasses(true);
    dexOptions.setMaxProcessCount(5);
    dexOptions.setOptimize(false);
    dexOptions.setPreDexLibraries(true);
    dexOptions.setThreadCount(10);

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
    assertNull("additionalParameters", dexOptions.additionalParameters());
    assertNull("javaMaxHeapSize", dexOptions.javaMaxHeapSize());
    assertNull("jumboMode", dexOptions.jumboMode());
    assertNull("keepRuntimeAnnotatedClasses", dexOptions.keepRuntimeAnnotatedClasses());
    assertNull("maxProcessCount", dexOptions.maxProcessCount());
    assertNull("optimize", dexOptions.optimize());
    assertNull("preDexLibraries", dexOptions.preDexLibraries());
    assertNull("threadCount", dexOptions.threadCount());

    dexOptions.addAdditionalParameter("abcd");
    dexOptions.setJavaMaxHeapSize("2048m");
    dexOptions.setJumboMode(true);
    dexOptions.setKeepRuntimeAnnotatedClasses(false);
    dexOptions.setMaxProcessCount(10);
    dexOptions.setOptimize(true);
    dexOptions.setPreDexLibraries(false);
    dexOptions.setThreadCount(5);

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

    dexOptions.removeAllAdditionalParameters();
    dexOptions.removeJavaMaxHeapSize();
    dexOptions.removeJumboMode();
    dexOptions.removeKeepRuntimeAnnotatedClasses();
    dexOptions.removeMaxProcessCount();
    dexOptions.removeOptimize();
    dexOptions.removePreDexLibraries();
    dexOptions.removeThreadCount();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    checkForInValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertNull("additionalParameters", dexOptions.additionalParameters());
    assertNull("javaMaxHeapSize", dexOptions.javaMaxHeapSize());
    assertNull("jumboMode", dexOptions.jumboMode());
    assertNull("keepRuntimeAnnotatedClasses", dexOptions.keepRuntimeAnnotatedClasses());
    assertNull("maxProcessCount", dexOptions.maxProcessCount());
    assertNull("optimize", dexOptions.optimize());
    assertNull("preDexLibraries", dexOptions.preDexLibraries());
    assertNull("threadCount", dexOptions.threadCount());
  }

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

    dexOptions.removeAdditionalParameter("abcd");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    assertEquals("additionalParameters", ImmutableList.of("efgh"), dexOptions.additionalParameters());
  }

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

    dexOptions.removeAdditionalParameter("abcd");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dexOptions = android.dexOptions();
    checkForInValidPsiElement(dexOptions, DexOptionsModelImpl.class);
    assertNull("additionalParameters", dexOptions.additionalParameters());
  }
}
