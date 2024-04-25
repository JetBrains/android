/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.runsGradle

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_HOST
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PATH
import com.android.SdkConstants.ATTR_SCHEME
import com.android.SdkConstants.ATTR_SHARED_USER_ID
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_MANIFEST
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.MANIFEST_CONFLICT_BUILD_TYPE_AND_FLAVOR
import com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_INCLUDE_FROM_LIB
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class GradleMergedManifestContributorsTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()

  private val mergedManifest
    get() : MergedManifestSnapshot {
      return MergedManifestManager
        .getMergedManifestSupplier(projectRule.gradleModule(":app"))
        .get()
        .get(2, TimeUnit.SECONDS)
    }

  @Test
  fun testBuildVariantPrecedence() {
    // The project has one module with a primary manifest, as well
    // as manifests for a product flavor and a debug build type.
    // Each manifest gives a different value for android:sharedUserId.
    projectRule.load(MANIFEST_CONFLICT_BUILD_TYPE_AND_FLAVOR)

    val sharedUserId = mergedManifest.document
      ?.getOnlyElement(null, TAG_MANIFEST)
      ?.attributes
      ?.getNamedItemNS(ANDROID_URI, ATTR_SHARED_USER_ID)
      ?.nodeValue

    // The manifest from the build type should get the highest priority.
    assertThat(sharedUserId).isEqualTo("com.example.myapplication.debug")
  }

  @Test
  fun testNavigationWithIncludeFromLib() {
    // The project has an app and a lib module, both with navigation files.
    // app depends on lib, and app's navigation file includes lib's navigation file,
    // which defines a deep link to https://www.google.com
    projectRule.load(NAVIGATION_EDITOR_INCLUDE_FROM_LIB)

    val activity = mergedManifest.document?.getOnlyElement(null, TAG_ACTIVITY)
    assertThat(activity).isNotNull()

    // Verify that the merged manifest includes the intent filter generated for
    // the deep link in lib's navigation graph.
    val intent_filter = activity!!.lastChild
    assertThat(intent_filter.childNodes.length).isEqualTo(6)
    intent_filter.childNodes.item(0).apply {
      assertThat(nodeName).isEqualTo(TAG_ACTION)
      assertThat(attrValue(ANDROID_URI, ATTR_NAME)).isEqualTo("android.intent.action.VIEW")
    }
    intent_filter.childNodes.item(1).apply {
      assertThat(nodeName).isEqualTo(TAG_CATEGORY)
      assertThat(attrValue(ANDROID_URI, ATTR_NAME)).isEqualTo("android.intent.category.DEFAULT")
    }
    intent_filter.childNodes.item(2).apply {
      assertThat(nodeName).isEqualTo(TAG_CATEGORY)
      assertThat(attrValue(ANDROID_URI, ATTR_NAME)).isEqualTo("android.intent.category.BROWSABLE")
    }
    intent_filter.childNodes.item(3).apply {
      assertThat(nodeName).isEqualTo(TAG_DATA)
      assertThat(attrValue(ANDROID_URI, ATTR_SCHEME)).isEqualTo("https")
    }
    intent_filter.childNodes.item(4).apply {
      assertThat(nodeName).isEqualTo(TAG_DATA)
      assertThat(attrValue(ANDROID_URI, ATTR_HOST)).isEqualTo("www.google.com")
    }
    intent_filter.childNodes.item(5).apply {
      assertThat(nodeName).isEqualTo(TAG_DATA)
      assertThat(attrValue(ANDROID_URI, ATTR_PATH)).isEqualTo("/")
    }
  }
}

private fun Document.getOnlyElement(namespaceURI: String?, localName: String): Node {
  return getElementsByTagNameNS(namespaceURI, localName).also {
    assertThat(it.length).isEqualTo(1)
  }.item(0)
}

private fun Node.attrValue(namespaceURI: String?, localName: String): String? {
  return attributes?.getNamedItemNS(namespaceURI, localName)?.nodeValue
}