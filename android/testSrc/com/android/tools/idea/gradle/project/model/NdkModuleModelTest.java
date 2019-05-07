/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.google.common.truth.Truth.assertThat;

public class NdkModuleModelTest extends AndroidGradleTestCase {
  public void testVariantAbiNames() throws Exception {
    loadProject(HELLO_JNI);
    Module appModule = myModules.getAppModule();
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(appModule);
    assertNotNull(ndkModuleModel);
    // Verify that the name contains both of variant and abi.
    assertThat(ndkModuleModel.getNdkVariantNames()).contains("arm8Release-x86");
  }

  public void testEqualsHash() {
    EqualsVerifier equalsVerifier = EqualsVerifier.forClass(IncludeSet.class);
    equalsVerifier.verify();
  }
}