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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.io.*;

import static com.google.common.truth.Truth.assertThat;

public class AndroidModuleModelSerializationTest extends AndroidGradleTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSerialization() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(false);
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(false);

    loadSimpleApplication();

    Module appModule = getModule("app");
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);

    AndroidModuleModel androidModelCopy = serializeAndDeserialize(androidModel);
    assertAreEqual(androidModel, androidModelCopy);
  }

  public void testSerializationWithSingleVariantSyncEnabled() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    loadSimpleApplication();

    Module appModule = getModule("app");
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertTrue(androidModel.isUsingSingleVariantSync());

    AndroidModuleModel androidModelCopy = serializeAndDeserialize(androidModel);
    assertAreEqual(androidModel, androidModelCopy);

    assertTrue(androidModelCopy.isUsingSingleVariantSync());
    assertThat(androidModelCopy.getVariantNames()).containsExactly("debug", "release");
  }

  @NotNull
  private static AndroidModuleModel serializeAndDeserialize(@NotNull AndroidModuleModel androidModel) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
      oos.writeObject(androidModel);
    }

    AndroidModuleModel newAndroidModel;
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
      try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
        newAndroidModel = (AndroidModuleModel)ois.readObject();
      }
    }
    return newAndroidModel;
  }

  private static void assertAreEqual(@NotNull AndroidModuleModel androidModel1, @NotNull AndroidModuleModel androidModel2) {
    assertEquals(androidModel1.getProjectSystemId(), androidModel2.getProjectSystemId());
    assertEquals(androidModel1.getModuleName(), androidModel2.getModuleName());
    assertEquals(androidModel1.getRootDirPath(), androidModel2.getRootDirPath());
    assertEquals(androidModel1.getSelectedVariant(), androidModel2.getSelectedVariant());
    assertEquals(androidModel1.isUsingSingleVariantSync(), androidModel2.isUsingSingleVariantSync());
    assertEquals(androidModel1.getVariantNames(), androidModel2.getVariantNames());
    assertEquals(androidModel1.getAndroidProject(), androidModel2.getAndroidProject());
  }
}