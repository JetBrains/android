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
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.*;
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
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
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
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
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
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
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
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
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
    verifyPasswordModel(signingConfig1.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig1", "type1", signingConfig1.storeType());
    assertEquals("signingConfig1", "myReleaseKey", signingConfig1.keyAlias());
    verifyPasswordModel(signingConfig1.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    SigningConfigModel signingConfig2 = signingConfigs.get(1);
    assertEquals("signingConfig1", "debug.keystore", signingConfig2.storeFile());
    verifyPasswordModel(signingConfig2.storePassword(), "debug_password", PLAIN_TEXT);
    assertEquals("signingConfig1", "type2", signingConfig2.storeType());
    assertEquals("signingConfig1", "myDebugKey", signingConfig2.keyAlias());
    verifyPasswordModel(signingConfig2.keyPassword(), "debugKeyPassword", PLAIN_TEXT);
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
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    signingConfig.storeFile().setValue("debug.keystore");
    signingConfig.storePassword().setValue(PLAIN_TEXT, "debug_password");
    signingConfig.storeType().setValue("debug_type");
    signingConfig.keyAlias().setValue("myDebugKey");
    signingConfig.keyPassword().setValue(PLAIN_TEXT, "debugKeyPassword");

    applyChanges(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "debug.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "debug_password", PLAIN_TEXT);
    assertEquals("signingConfig", "debug_type", signingConfig.storeType());
    assertEquals("signingConfig", "myDebugKey", signingConfig.keyAlias());
    verifyPasswordModel( signingConfig.keyPassword(), "debugKeyPassword", PLAIN_TEXT);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "debug.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "debug_password", PLAIN_TEXT);
    assertEquals("signingConfig", "debug_type", signingConfig.storeType());
    assertEquals("signingConfig", "myDebugKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "debugKeyPassword", PLAIN_TEXT);
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
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    signingConfig.keyAlias().delete();
    signingConfig.keyPassword().delete();
    signingConfig.storeType().delete();
    signingConfig.storeFile().delete();
    signingConfig.storePassword().delete();

    applyChanges(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertMissingProperty(signingConfig.storeFile());
    assertMissingProperty(signingConfig.storePassword());
    assertMissingProperty(signingConfig.storeType());
    assertMissingProperty(signingConfig.keyAlias());
    assertMissingProperty(signingConfig.keyPassword());

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

    assertMissingProperty(signingConfig.storeFile());
    assertMissingProperty(signingConfig.storePassword());
    assertMissingProperty(signingConfig.keyAlias());
    assertMissingProperty(signingConfig.keyPassword());

    signingConfig.storeFile().setValue("release.keystore");
    signingConfig.storePassword().setValue(PLAIN_TEXT, "password");
    signingConfig.storeType().setValue("type");
    signingConfig.keyAlias().setValue("myReleaseKey");
    signingConfig.keyPassword().setValue(PLAIN_TEXT, "releaseKeyPassword");

    applyChanges(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    assertEquals("signingConfig", "release.keystore", signingConfig.storeFile());
    verifyPasswordModel(signingConfig.storePassword(), "password", PLAIN_TEXT);
    assertEquals("signingConfig", "type", signingConfig.storeType());
    assertEquals("signingConfig", "myReleaseKey", signingConfig.keyAlias());
    verifyPasswordModel(signingConfig.keyPassword(), "releaseKeyPassword", PLAIN_TEXT);
  }

  public void testParseEnvironmentVariablePasswordElements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storePassword System.getenv(\"KSTOREPWD\")\n" +
                  "      keyPassword System.getenv(\"KEYPWD\")\n" +
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

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);
  }

  public void testParseConsoleReadPasswordElements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storePassword System.console().readLine(\"\\nKeystore password: \")\n" +
                  "      keyPassword System.console().readLine(\"\\nKey password: \")" +
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

    verifyPasswordModel(signingConfig.storePassword(), "\nKeystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "\nKey password: ", CONSOLE_READ);
  }

  public void testEditEnvironmentVariablePasswordElements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storePassword System.getenv(\"KSTOREPWD\")\n" +
                  "      keyPassword System.getenv(\"KEYPWD\")\n" +
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

    verifyPasswordModel(signingConfig.storePassword(),"KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);

    signingConfig.storePassword().setValue(ENVIRONMENT_VARIABLE, "KSTOREPWD1");
    signingConfig.keyPassword().setValue(ENVIRONMENT_VARIABLE, "KEYPWD1");
    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(),"KSTOREPWD1", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD1", ENVIRONMENT_VARIABLE);
  }

  public void testEditConsoleReadPasswordElements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storePassword System.console().readLine(\"\\nKeystore password: \")\n" +
                  "      keyPassword System.console().readLine(\"\\nKey password: \")" +
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

    verifyPasswordModel(signingConfig.storePassword(), "\nKeystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "\nKey password: ", CONSOLE_READ);

    signingConfig.storePassword().setValue(CONSOLE_READ, "Another Keystore Password: ");
    signingConfig.keyPassword().setValue(CONSOLE_READ, "Another Key Password: ");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "Another Keystore Password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "Another Key Password: ", CONSOLE_READ);
  }

  public void testAddEnvironmentVariablePasswordElements() throws Exception {
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

    assertMissingProperty("signingConfig", signingConfig.storePassword());
    assertMissingProperty("signingConfig", signingConfig.keyPassword());

    signingConfig.storePassword().setValue(ENVIRONMENT_VARIABLE, "KSTOREPWD");
    signingConfig.keyPassword().setValue(ENVIRONMENT_VARIABLE, "KEYPWD");
    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);
  }

  public void testAddConsoleReadPasswordElements() throws Exception {
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

    assertMissingProperty("signingConfig", signingConfig.storePassword());
    assertMissingProperty("signingConfig", signingConfig.keyPassword());

    signingConfig.storePassword().setValue(CONSOLE_READ, /*"\n*/"Keystore password: ");
    signingConfig.keyPassword().setValue(CONSOLE_READ, /*"\n*/"Key password: ");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), /*"\n*/"Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), /*"\n*/"Key password: ", CONSOLE_READ);
  }

  public void testChangeEnvironmentVariablePasswordToConsoleReadPassword() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storePassword System.getenv(\"KSTOREPWD\")\n" +
                  "      keyPassword System.getenv(\"KEYPWD\")\n" +
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

    verifyPasswordModel(signingConfig.storePassword(), "KSTOREPWD", ENVIRONMENT_VARIABLE);
    verifyPasswordModel(signingConfig.keyPassword(), "KEYPWD", ENVIRONMENT_VARIABLE);


    signingConfig.storePassword().setValue(CONSOLE_READ, /*"\n*/"Keystore password: ");
    signingConfig.keyPassword().setValue(CONSOLE_READ, /*"\n*/"Key password: ");
    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), /*"\n*/"Keystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), /*"\n*/"Key password: ", CONSOLE_READ);
  }

  public void testChangeConsoleReadPasswordElementsToPlainTextPasswordElements() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    release {\n" +
                  "      storePassword System.console().readLine(\"\\nKeystore password: \")\n" +
                  "      keyPassword System.console().readLine(\"\\nKey password: \")" +
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

    verifyPasswordModel(signingConfig.storePassword(), "\nKeystore password: ", CONSOLE_READ);
    verifyPasswordModel(signingConfig.keyPassword(), "\nKey password: ", CONSOLE_READ);

    signingConfig.storePassword().setValue(PLAIN_TEXT, "store_password");
    signingConfig.keyPassword().setValue(PLAIN_TEXT, "key_password");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    signingConfigs = android.signingConfigs();
    assertThat(signingConfigs).hasSize(1);
    signingConfig = signingConfigs.get(0);

    verifyPasswordModel(signingConfig.storePassword(), "store_password", PLAIN_TEXT);
    verifyPasswordModel(signingConfig.keyPassword(), "key_password", PLAIN_TEXT);
  }
}
