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

import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;

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

    assertEquals(15, ((VersionQualifier)QualifierUtils.getIncompatibleVersionQualifier(21, versionQualifiers)).getVersion());
    assertEquals(15, ((VersionQualifier)QualifierUtils.getIncompatibleVersionQualifier(22, versionQualifiers)).getVersion());
    assertEquals(14, ((VersionQualifier)QualifierUtils.getIncompatibleVersionQualifier(14, versionQualifiers)).getVersion());
  }

  public void testDensityQualifiers() {
    List<ResourceQualifier> densityQualifiers = ImmutableList.<ResourceQualifier>of(
      new DensityQualifier(Density.XXXHIGH),
      new DensityQualifier(Density.LOW));

    assertEquals(Density.NODPI, ((DensityQualifier)QualifierUtils.getIncompatibleDensityQualifier(densityQualifiers)).getValue());

    densityQualifiers = ImmutableList.<ResourceQualifier>of(
      new DensityQualifier(Density.XXXHIGH),
      new DensityQualifier(Density.XXHIGH));

    assertEquals(Density.DPI_400, ((DensityQualifier)QualifierUtils.getIncompatibleDensityQualifier(densityQualifiers)).getValue());

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

    assertEquals("-land-v18", QualifierUtils.restrictConfiguration(myConfigurationManager, compatible, incompatible).getUniqueKey());
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

    assertEquals("-square", QualifierUtils.restrictConfiguration(myConfigurationManager, compatible, incompatible).getUniqueKey());
  }

  /**
   * Tests that if the compatible configuration has a qualifier (ex. "landscape") and one of the incompatible ones contains
   * the same qualifier, we don't override the one in the compatible one with portrait.
   */
  public void testContradictingQualifiers() {
    ConfigurationManager manager = ConfigurationManager.create(myFixture.getModule());

    List<FolderConfiguration> incompatible = ImmutableList.of(
      FolderConfiguration.getConfigForQualifierString("land"));

    // default config
    FolderConfiguration compatible = FolderConfiguration.getConfigForQualifierString("land-hdpi");

    assertEquals("-land-hdpi", QualifierUtils.restrictConfiguration(manager, compatible, incompatible).getUniqueKey());
  }
}
