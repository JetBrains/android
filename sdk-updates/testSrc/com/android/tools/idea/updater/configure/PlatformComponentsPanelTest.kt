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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Tests for the node tree inside the [PlatformComponentsPanel]
 */
class PlatformComponentsPanelTest {

  @Mock private lateinit var myConfigurable: SdkUpdaterConfigurable
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
  fun testInvalidSdk() {
    //SDKs with AndroidVersion api 0 will be ignored (b/191014630)
    val panel = PlatformComponentsPanel()
    panel.setConfigurable(myConfigurable)
    val typeDetails = AndroidSdkHandler.getRepositoryModule().createLatestFactory().createPlatformDetailsType() as TypeDetails
    panel.setPackages(ImmutableMultimap.of(
      AndroidVersion(30), UpdatablePackage(createLocalPackage("android-30", 1, typeDetails = typeDetails)),
      AndroidVersion(29), UpdatablePackage(createLocalPackage("android-29", 2, typeDetails = typeDetails)),
      AndroidVersion(AndroidVersion.VersionCodes.UNDEFINED),
      UpdatablePackage(createLocalPackage("android-0", 0, typeDetails = typeDetails)) // Invalid AndroidVersion
    ))
    verifyNodes(
      Node("", listOf("Android 11.0 (R)", "Android 10.0 (Q)").map { Node(it) }), panel.myPlatformSummaryRootNode)

    verifyNodes(
      Node("", listOf(
        Node("Android 11.0 (R)", listOf(Node("android-30"))),
        Node("Android 10.0 (Q)", listOf(Node("android-29")))
      )), panel.myPlatformDetailsRootNode)
  }

  @Test
  fun testValidNodes() {
    val panel = PlatformComponentsPanel()
    panel.setConfigurable(myConfigurable)
    val typeDetails = AndroidSdkHandler.getRepositoryModule().createLatestFactory().createPlatformDetailsType() as TypeDetails
    panel.setPackages(ImmutableMultimap.of(
      AndroidVersion(30), UpdatablePackage(createLocalPackage("android-30", 1, typeDetails = typeDetails)),
      AndroidVersion(7), UpdatablePackage(createLocalPackage("android-7", 2, typeDetails = typeDetails)),
      AndroidVersion(21), UpdatablePackage(createLocalPackage("android-21", 2, typeDetails = typeDetails)),
      AndroidVersion(21), UpdatablePackage(createLocalPackage("android-21", 1)),
      AndroidVersion(29), UpdatablePackage(createLocalPackage("android-29", 2, typeDetails = typeDetails)),
    ))
    verifyNodes(
      Node(
        "",
        listOf(
          "Android 11.0 (R)",
          "Android 10.0 (Q)",
          "Android 5.0 (Lollipop)",
          "Android 2.1 (Eclair)").map { Node(it) }
      ), panel.myPlatformSummaryRootNode)

    verifyNodes(
      Node("", listOf(
        Node("Android 11.0 (R)", listOf(Node("android-30"))),
        Node("Android 10.0 (Q)", listOf(Node("android-29"))),
        Node("Android 5.0 (Lollipop)", listOf(Node("android-21"), Node("android-21"))),
        Node("Android 2.1 (Eclair)", listOf(Node("android-7"))))
      ), panel.myPlatformDetailsRootNode)
  }
}