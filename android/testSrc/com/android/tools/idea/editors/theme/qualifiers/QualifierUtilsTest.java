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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.*;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ConstantConditions")
public class QualifierUtilsTest extends AndroidTestCase {
  ConfigurationManager myConfigurationManager;

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myConfigurationManager = ConfigurationManager.create(myFixture.getModule());
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();

    if (myConfigurationManager != null) {
      Disposer.dispose(myConfigurationManager);
      myConfigurationManager = null;
    }
  }

  public void testVersionQualifiers() {
    List<ResourceQualifier> versionQualifiers = ImmutableList.<ResourceQualifier>of(
      new VersionQualifier(21),
      new VersionQualifier(19),
      new VersionQualifier(16));
    assertEquals(15, ((VersionQualifier)QualifierUtils.getIncompatibleVersionQualifier(versionQualifiers)).getVersion());
  }

  public void testDensityQualifiers() {
    List<ResourceQualifier> densityQualifiers = ImmutableList.<ResourceQualifier>of(
      new DensityQualifier(Density.XXXHIGH),
      new DensityQualifier(Density.LOW));

    assertEquals(Density.NODPI, ((DensityQualifier)QualifierUtils.getIncompatibleDensityQualifier(densityQualifiers)).getValue());

    densityQualifiers = ImmutableList.<ResourceQualifier>of(
      new DensityQualifier(Density.XXXHIGH),
      new DensityQualifier(Density.XXHIGH));

    assertEquals(Density.DPI_420, ((DensityQualifier)QualifierUtils.getIncompatibleDensityQualifier(densityQualifiers)).getValue());

    densityQualifiers = ImmutableList.<ResourceQualifier>of(
      new DensityQualifier(Density.NODPI));

    assertNull(QualifierUtils.getIncompatibleDensityQualifier(densityQualifiers));
  }

  /**
   * This method checks the version qualifiers and also the combination with the passed "compatible" folder
   */
  public void testVersionQualifiersFromFolders() {
    List<FolderConfiguration> incompatible = ImmutableList.of(
      FolderConfiguration.getConfigForQualifierString("v21"),
      FolderConfiguration.getConfigForQualifierString("v19"));

    FolderConfiguration compatible = FolderConfiguration.getConfigForQualifierString("land");

    assertEquals("-land", QualifierUtils.restrictConfiguration(compatible, incompatible).getUniqueKey());
  }

  /**
   * Tests a basic enum type like ScreenOrientation
   */
  public void testScreenOrientationQualifiers() {
    List<ResourceQualifier> orientationQualifiers = ImmutableList.<ResourceQualifier>of(
      new ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE), new ScreenOrientationQualifier(ScreenOrientation.PORTRAIT));

    ResourceQualifier result = QualifierUtils.getIncompatibleEnum(ScreenOrientation.class, ScreenOrientationQualifier.class, orientationQualifiers);
    assertEquals(ScreenOrientation.SQUARE, ((ScreenOrientationQualifier)result).getValue());

    orientationQualifiers = ImmutableList.<ResourceQualifier>of(
      new ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE),
      new ScreenOrientationQualifier(ScreenOrientation.PORTRAIT),
      new ScreenOrientationQualifier(ScreenOrientation.SQUARE));
    result = QualifierUtils.getIncompatibleEnum(ScreenOrientation.class, ScreenOrientationQualifier.class, orientationQualifiers);

    // There are not incompatible qualifiers left
    assertNull(result);
  }

  /**
   * Tests the same as {@link #testScreenOrientationQualifiers()} but makes sure that the enum is correctly detected
   * in the restrictConfiguration method.
   */
  public void testScreenOrientationQualifiersFromFolder() {
    List<FolderConfiguration> incompatible = ImmutableList.of(
      FolderConfiguration.getConfigForQualifierString("port"),
      FolderConfiguration.getConfigForQualifierString("land"));

    // default config
    FolderConfiguration compatible = FolderConfiguration.getConfigForQualifierString("");

    assertEquals("-square", QualifierUtils.restrictConfiguration(compatible, incompatible).getUniqueKey());
  }

  private void checkRestrictConfigurationFor(String compatibleQualifier, String answerQualifier, String... incompatibleQualifiers) {
    ArrayList<FolderConfiguration> incompatibles = Lists.newArrayList();
    for (int i = 0; i < incompatibleQualifiers.length; ++i) {
      incompatibles.add(FolderConfiguration.getConfigForQualifierString(incompatibleQualifiers[i]));
      assertNotNull(incompatibles.get(i));
    }
    final FolderConfiguration compatible = FolderConfiguration.getConfigForQualifierString(compatibleQualifier);
    assertNotNull(compatible);
    FolderConfiguration folderConfiguration = QualifierUtils.restrictConfiguration(compatible, incompatibles);
    assertNotNull(folderConfiguration);

    // folderConfiguration.getUniqueKey() returns with a "-"
    if (!answerQualifier.isEmpty()) {
      answerQualifier = "-" + answerQualifier;
    }
    assertEquals(answerQualifier, folderConfiguration.getUniqueKey());
  }

  /**
   * Tests restrictConfiguration method, which should work backward to algorithm from the following link
   * See: http://developer.android.com/guide/topics/resources/providing-resources.html
   */
  public void testRestrictConfiguration() {
    checkRestrictConfigurationFor("en-v21", "en-v21", "ldrtl", "ldltr");
    checkRestrictConfigurationFor("en", "en", "fr-v23", "fr-v19");
    // "__" - is a fake language qualifier, see: LocaleQualifier.FAKE_VALUE
    checkRestrictConfigurationFor("v11", "__-v11", "v7", "en");
    checkRestrictConfigurationFor("en", "en", "v9");
    checkRestrictConfigurationFor("fr", "fr", "en-v7", "en-v11");
    checkRestrictConfigurationFor("land-hdpi", "land-hdpi", "land");
    checkRestrictConfigurationFor("en", "en-port", "en-land", "port");
    checkRestrictConfigurationFor("en", "en-night-v19", "en-notnight-v21", "en-notnight-v21", "en-v20",  "fr-night-v18");
    // TODO (Madiyar): find an answer
    // checkRestrictConfigurationFor("", "", "v21", "de", "sw600dp", "ar", "v15", "pt-rPT", "pt-rBR", "v16");
  }
}
