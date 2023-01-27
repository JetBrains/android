/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.updater.configure

import com.android.repository.api.UpdatablePackage
import com.android.repository.impl.meta.TypeDetails
import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.AndroidSdkHandler
import com.google.common.collect.ImmutableMultimap
import com.intellij.ide.util.PropertiesComponent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals

/**
 * Tests for the node tree inside the [PlatformComponentsPanel]
 */
class PlatformComponentsPanelTest {
  @Mock private lateinit var myConfigurable: SdkUpdaterConfigurable
  @Mock private lateinit var propertiesComponent: PropertiesComponent
  private lateinit var closeable: AutoCloseable

  @Before
  fun setUp() {
    closeable = MockitoAnnotations.openMocks(this)
  }

  @After
  fun tearDown() {
    closeable.close()
  }

  @Test
  fun testSdkExtensions() {
    val panel = PlatformComponentsPanel(propertiesComponent)
    panel.setConfigurable(myConfigurable)
    val typeDetails = AndroidSdkHandler.getRepositoryModule().createLatestFactory().createPlatformDetailsType() as TypeDetails
    panel.setPackages(ImmutableMultimap.of(
      AndroidVersion(30), UpdatablePackage(createLocalPackage("android-30", 1, typeDetails = typeDetails)),
      AndroidVersion(30, null, 1, false), UpdatablePackage(createLocalPackage("android-30-1", 1, typeDetails = typeDetails)),
      AndroidVersion(30, null, 2, false), UpdatablePackage(createLocalPackage("android-30-2", 1, typeDetails = typeDetails)),
      AndroidVersion(30, "Codename"), UpdatablePackage(createLocalPackage("android-Codename", 2, typeDetails = typeDetails))
    ))
    assertEquals("""
      Root
       Android Codename Preview
       Android 11.0 ("R")
    """.trimIndent(), panel.myPlatformSummaryRootNode.asString())

    assertEquals("""
      Root
       Android Codename Preview
        android-Codename
       Android 11.0 ("R")
        android-30
       Android 11.0 ("R")
        android-30-1
       Android 11.0 ("R")
        android-30-2
    """.trimIndent(), panel.myPlatformDetailsRootNode.asString())
  }

  @Test
  fun testInvalidSdk() {
    //SDKs with AndroidVersion api 0 will be ignored (b/191014630)
    val panel = PlatformComponentsPanel(propertiesComponent)
    panel.setConfigurable(myConfigurable)
    val typeDetails = AndroidSdkHandler.getRepositoryModule().createLatestFactory().createPlatformDetailsType() as TypeDetails
    panel.setPackages(ImmutableMultimap.of(
      AndroidVersion(30), UpdatablePackage(createLocalPackage("android-30", 1, typeDetails = typeDetails)),
      AndroidVersion(29), UpdatablePackage(createLocalPackage("android-29", 2, typeDetails = typeDetails)),
      AndroidVersion(AndroidVersion.VersionCodes.UNDEFINED),
      UpdatablePackage(createLocalPackage("android-0", 0, typeDetails = typeDetails)) // Invalid AndroidVersion
    ))
    assertEquals("""
      Root
       Android 11.0 ("R")
       Android 10.0 ("Q")
    """.trimIndent(), panel.myPlatformSummaryRootNode.asString())

    assertEquals("""
      Root
       Android 11.0 ("R")
        android-30
       Android 10.0 ("Q")
        android-29
    """.trimIndent(), panel.myPlatformDetailsRootNode.asString())
  }

  @Test
  fun testValidNodes() {
    val panel = PlatformComponentsPanel(propertiesComponent)
    panel.setConfigurable(myConfigurable)
    val typeDetails = AndroidSdkHandler.getRepositoryModule().createLatestFactory().createPlatformDetailsType() as TypeDetails
    panel.setPackages(ImmutableMultimap.of(
      AndroidVersion(30), UpdatablePackage(createLocalPackage("android-30", 1, typeDetails = typeDetails)),
      AndroidVersion(21), UpdatablePackage(createLocalPackage("android-21", 2, typeDetails = typeDetails)),
      AndroidVersion(21), UpdatablePackage(createLocalPackage("android-21", 1)),
      AndroidVersion(500), UpdatablePackage(createLocalPackage("android-500", 2, typeDetails = typeDetails)),
      AndroidVersion(501, "Codename"), UpdatablePackage(createLocalPackage("android-501", 2, typeDetails = typeDetails))
    ))
    assertEquals("""
      Root
       Android Codename Preview
       Android API 500
       Android 11.0 ("R")
       Android 5.0 ("Lollipop")
    """.trimIndent(), panel.myPlatformSummaryRootNode.asString())

    assertEquals("""
      Root
       Android Codename Preview
        android-501
       Android API 500
        android-500
       Android 11.0 ("R")
        android-30
       Android 5.0 ("Lollipop")
        android-21
        android-21
    """.trimIndent(), panel.myPlatformDetailsRootNode.asString())
  }
}