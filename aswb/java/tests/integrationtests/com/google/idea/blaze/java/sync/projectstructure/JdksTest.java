/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.projectstructure;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.java.sync.sdk.BlazeJdkProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.UnknownSdkType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import java.io.File;
import java.util.Optional;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link Jdks}. */
@RunWith(JUnit4.class)
public class JdksTest extends BlazeIntegrationTestCase {

  @Test
  public void testLowerJdkIgnored() {
    setJdkTable(IdeaTestUtil.getMockJdk14());
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_7)).isNull();
  }

  @Test
  public void testEqualJdkChosen() {
    Sdk jdk7 = IdeaTestUtil.getMockJdk17();
    setJdkTable(jdk7);
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_7)).isEqualTo(jdk7);
  }

  @Test
  public void testHigherJdkChosen() {
    Sdk jdk8 = IdeaTestUtil.getMockJdk18();
    setJdkTable(jdk8);
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_7)).isEqualTo(jdk8);
  }

  @Test
  public void testClosestJdkOfAtLeastSpecifiedLevelChosen() {
    Sdk jdk7 = IdeaTestUtil.getMockJdk17();
    // Ordering retained in final list; add jdk7 last to ensure first Jdk of at least the specified
    // language level isn't automatically chosen.
    setJdkTable(IdeaTestUtil.getMockJdk18(), IdeaTestUtil.getMockJdk14(), jdk7);
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_6)).isEqualTo(jdk7);
  }

  @Test
  public void testChooseSameJdkForSameLevel() {
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);

    setJdkTable(
        getUniqueMockJdk(LanguageLevel.JDK_1_4),
        getUniqueMockJdk(LanguageLevel.JDK_1_7), // different JDK of the same version
        getUniqueMockJdk(LanguageLevel.JDK_1_8),
        currentJdk7);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isEqualTo(currentJdk7);
  }

  @Test
  public void testChooseSameJdkForLowerLevel() {
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);

    setJdkTable(
        getUniqueMockJdk(LanguageLevel.JDK_1_4),
        getUniqueMockJdk(LanguageLevel.JDK_1_7),
        getUniqueMockJdk(LanguageLevel.JDK_1_8),
        currentJdk7);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_4);
    assertThat(chosenSdk).isEqualTo(currentJdk7);
  }

  @Test
  public void testChooseHigherJdkForHigherLevel() {
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk jdk8 = getUniqueMockJdk(LanguageLevel.JDK_1_8);

    setJdkTable(
        getUniqueMockJdk(LanguageLevel.JDK_1_4),
        getUniqueMockJdk(LanguageLevel.JDK_1_7),
        jdk8,
        currentJdk7);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_8);
    assertThat(chosenSdk).isNotEqualTo(currentJdk7);
    assertThat(chosenSdk).isEqualTo(jdk8);
  }

  @Test
  public void testChooseDifferentSdkIfCurrentNotJdk() {
    Sdk currentSdk = getNonJavaMockSdk();

    setJdkTable(
        getUniqueMockJdk(LanguageLevel.JDK_1_4),
        getUniqueMockJdk(LanguageLevel.JDK_1_7),
        getUniqueMockJdk(LanguageLevel.JDK_1_8),
        currentSdk);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentSdk, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isNotEqualTo(currentSdk);
  }

  @Test
  public void testChooseDifferentJdkIfCurrentNotInTable() {
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);

    setJdkTable(
        getUniqueMockJdk(LanguageLevel.JDK_1_4),
        getUniqueMockJdk(LanguageLevel.JDK_1_7),
        getUniqueMockJdk(LanguageLevel.JDK_1_8));

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isNotEqualTo(currentJdk7);
  }

  @Test
  public void testChooseSdkIfCurrentIsNull() {
    setJdkTable(
        getUniqueMockJdk(LanguageLevel.JDK_1_4),
        getUniqueMockJdk(LanguageLevel.JDK_1_7),
        getUniqueMockJdk(LanguageLevel.JDK_1_8));

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(null, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isNotNull();
  }

  @Test
  public void testChooseHigherJdkIfLevelNotInTable() {
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk jdk8 = getUniqueMockJdk(LanguageLevel.JDK_1_8);

    setJdkTable(getUniqueMockJdk(LanguageLevel.JDK_1_4), jdk8);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isNotEqualTo(currentJdk7);
    assertThat(chosenSdk).isEqualTo(jdk8);
  }

  @Test
  public void testChooseSameJdkIfProvidedByProvider() {
    Sdk jdk4 = getUniqueMockJdk(LanguageLevel.JDK_1_4);
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk jdk8 = getUniqueMockJdk(LanguageLevel.JDK_1_8);

    registerJdkProvider(
        ImmutableMap.of(
            LanguageLevel.JDK_1_4, jdk4,
            LanguageLevel.JDK_1_7, currentJdk7,
            LanguageLevel.JDK_1_8, jdk8));
    setJdkTable(jdk4, jdk8, currentJdk7);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isEqualTo(currentJdk7);
  }

  @Test
  public void testChooseSameJdkIfProvidedByLastProvider() {
    Sdk jdk4 = getUniqueMockJdk(LanguageLevel.JDK_1_4);
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk differentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk jdk8 = getUniqueMockJdk(LanguageLevel.JDK_1_8);

    registerJdkProvider(
        ImmutableMap.of(
            LanguageLevel.JDK_1_4, jdk4,
            LanguageLevel.JDK_1_7, differentJdk7,
            LanguageLevel.JDK_1_8, jdk8));
    registerJdkProvider(
        ImmutableMap.of(
            LanguageLevel.JDK_1_4, jdk4,
            LanguageLevel.JDK_1_7, currentJdk7,
            LanguageLevel.JDK_1_8, jdk8));
    setJdkTable(jdk4, differentJdk7, jdk8, currentJdk7);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isEqualTo(currentJdk7);
  }

  @Test
  public void testChooseDifferentJdkIfNotProvidedByProvider() {
    Sdk jdk4 = getUniqueMockJdk(LanguageLevel.JDK_1_4);
    Sdk currentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk differentJdk7 = getUniqueMockJdk(LanguageLevel.JDK_1_7);
    Sdk jdk8 = getUniqueMockJdk(LanguageLevel.JDK_1_8);

    registerJdkProvider(
        ImmutableMap.of(
            LanguageLevel.JDK_1_4, jdk4,
            LanguageLevel.JDK_1_7, differentJdk7,
            LanguageLevel.JDK_1_8, jdk8));

    setJdkTable(jdk4, differentJdk7, jdk8, currentJdk7);

    Sdk chosenSdk = Jdks.chooseOrCreateJavaSdk(currentJdk7, LanguageLevel.JDK_1_7);
    assertThat(chosenSdk).isNotEqualTo(currentJdk7);
  }

  private void setJdkTable(Sdk... jdks) {
    WriteAction.run(
        () -> {
          ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
          jdkTable.getSdksOfType(JavaSdk.getInstance()).forEach(jdkTable::removeJdk);
          stream(jdks).forEach(jdkTable::addJdk);
        });
  }

  private void registerJdkProvider(ImmutableMap<LanguageLevel, Sdk> jdkProvider) {
    registerExtension(
        BlazeJdkProvider.EP_NAME,
        level ->
            Optional.ofNullable(jdkProvider.get(level))
                .map(Sdk::getHomePath)
                .map(File::new)
                .orElse(null));
  }

  private static Sdk getUniqueMockJdk(LanguageLevel languageLevel) {
    Sdk jdk = IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion());
    try {
      jdk = (Sdk) jdk.clone(); // Clone to allow modifications below.
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    SdkModificator jdkModificator = jdk.getSdkModificator();
    jdkModificator.setName(jdk.getName() + "." + jdk.hashCode());
    jdkModificator.setHomePath(jdk.getHomePath() + "." + jdk.hashCode());
    WriteAction.run(jdkModificator::commitChanges);
    return jdk;
  }

  private static Sdk getNonJavaMockSdk() {
    return ProjectJdkTable.getInstance()
        .createSdk("nonJavaSdk", UnknownSdkType.getInstance("nonJavaSdkType"));
  }
}
