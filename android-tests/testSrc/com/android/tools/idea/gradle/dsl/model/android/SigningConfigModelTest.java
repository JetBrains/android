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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link SigningConfigModel}.
 */
public class SigningConfigModelTest extends GradleFileModelTestCase {
  public void testSigningConfigBlockWithApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storeFile file(\"release.keystore\")\n" +
                  "      storePassword \"password\"\n" +
                  "      storeType \"type\"\n" +
                  "      keyAlias \"myReleaseKey\"\n" +
                  "      keyPassword \"releaseKeyPassword\"\n" +
                  "    }\n" +
                  "  }" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());
  }

  public void testSigningConfigBlockWithAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storeFile = file(\"release.keystore\")\n" +
                  "      storePassword = \"password\"\n" +
                  "      storeType = \"type\"\n" +
                  "      keyAlias = \"myReleaseKey\"\n" +
                  "      keyPassword = \"releaseKeyPassword\"\n" +
                  "    }\n" +
                  "  }" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());
  }

  public void testSigningConfigApplicationStatements() throws Exception {
    String text = "android.signingConfigs.release.storeFile file(\"release.keystore\")\n" +
                  "android.signingConfigs.release.storePassword \"password\"\n" +
                  "android.signingConfigs.release.storeType \"type\"\n" +
                  "android.signingConfigs.release.keyAlias \"myReleaseKey\"\n" +
                  "android.signingConfigs.release.keyPassword \"releaseKeyPassword\"\n";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());
  }

  public void testSigningConfigAssignmentStatements() throws Exception {
    String text = "android.signingConfigs.release.storeFile = file(\"release.keystore\")\n" +
                  "android.signingConfigs.release.storePassword = \"password\"\n" +
                  "android.signingConfigs.release.storeType = \"type\"\n" +
                  "android.signingConfigs.release.keyAlias = \"myReleaseKey\"\n" +
                  "android.signingConfigs.release.keyPassword = \"releaseKeyPassword\"\n";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());
  }

  public void testMultipleSigningConfigs() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storeFile file(\"release.keystore\")\n" +
                  "      storePassword \"password\"\n" +
                  "      storeType \"type1\"\n" +
                  "      keyAlias \"myReleaseKey\"\n" +
                  "      keyPassword \"releaseKeyPassword\"\n" +
                  "    }\n" +
                  "    debug {\n" +
                  "      storeFile file(\"debug.keystore\")\n" +
                  "      storePassword \"debug_password\"\n" +
                  "      storeType \"type2\"\n" +
                  "      keyAlias \"myDebugKey\"\n" +
                  "      keyPassword \"debugKeyPassword\"\n" +
                  "    }\n" +
                  "  }" +
                  "}";
    writeToBuildFile(text);
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(2);

    SigningConfigModel signingConfig1 = signingConfigs.get(0);
    assertEquals("signingConfig1", "release", signingConfig1.name());
    assertEquals("signingConfig1", "release.keystore", signingConfig1.storeFile());
    assertEquals("signingConfig1", "password", signingConfig1.storePassword());
    assertEquals("signingConfig1", "type1", signingConfig1.storeType());
    assertEquals("signingConfig1", "myReleaseKey", signingConfig1.keyAlias());
    assertEquals("signingConfig1", "releaseKeyPassword", signingConfig1.keyPassword());

    SigningConfigModel signingConfig2 = signingConfigs.get(1);
    assertEquals("signingConfig1", "debug.keystore", signingConfig2.storeFile());
    assertEquals("signingConfig1", "debug_password", signingConfig2.storePassword());
    assertEquals("signingConfig1", "type2", signingConfig2.storeType());
    assertEquals("signingConfig1", "myDebugKey", signingConfig2.keyAlias());
    assertEquals("signingConfig1", "debugKeyPassword", signingConfig2.keyPassword());
  }

  public void testSetAndApplySigningConfig() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storeFile file(\"release.keystore\")\n" +
                  "      storePassword \"password\"\n" +
                  "      storeType \"type\"\n" +
                  "      keyAlias \"myReleaseKey\"\n" +
                  "      keyPassword \"releaseKeyPassword\"\n" +
                  "    }\n" +
                  "  }" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());

    signingConfig.setStoreFile("debug.keystore");
    signingConfig.setStorePassword("debug_password");
    signingConfig.setStoreType("debug_type");
    signingConfig.setKeyAlias("myDebugKey");
    signingConfig.setKeyPassword("debugKeyPassword");

    applyChanges(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "debug.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "debug_password", signingConfig.storePassword());
    assertEquals("signingConfig", "debug_type", signingConfig.storeType());
    assertEquals("signingConfig", "myDebugKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "debugKeyPassword", signingConfig.keyPassword());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "debug.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "debug_password", signingConfig.storePassword());
    assertEquals("signingConfig", "debug_type", signingConfig.storeType());
    assertEquals("signingConfig", "myDebugKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "debugKeyPassword", signingConfig.keyPassword());
  }

  public void testRemoveAndApplySigningConfig() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storeFile file(\"release.keystore\")\n" +
                  "      storePassword \"password\"\n" +
                  "      storeType \"type\"\n" +
                  "      keyAlias \"myReleaseKey\"\n" +
                  "      keyPassword \"releaseKeyPassword\"\n" +
                  "    }\n" +
                  "  }" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    List<SigningConfigModel> signingConfigs = android.signingConfigs();

    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());

    signingConfig.removeKeyAlias();
    signingConfig.removeKeyPassword();
    signingConfig.removeStoreType();
    signingConfig.removeStoreFile();
    signingConfig.removeStorePassword();

    applyChanges(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertNull(signingConfig.storeFile());
    assertNull(signingConfig.storePassword());
    assertNull(signingConfig.storeType());
    assertNull(signingConfig.keyAlias());
    assertNull(signingConfig.keyPassword());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).isEmpty(); // empty blocks are deleted automatically.
  }

  public void testAddAndApplySigningConfig() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "    }\n" +
                  "  }" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SigningConfigModel> signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    SigningConfigModel signingConfig = signingConfigs.get(0);

    assertNull(signingConfig.storeFile());
    assertNull(signingConfig.storePassword());
    assertNull(signingConfig.keyAlias());
    assertNull(signingConfig.keyPassword());

    signingConfig.setStoreFile("release.keystore");
    signingConfig.setStorePassword("password");
    signingConfig.setStoreType("type");
    signingConfig.setKeyAlias("myReleaseKey");
    signingConfig.setKeyPassword("releaseKeyPassword");

    applyChanges(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    assertEquals("signingConfig", "password", signingConfig.storePassword());
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    assertEquals("signingConfig", "releaseKeyPassword", signingConfig.keyPassword());
  }
}
