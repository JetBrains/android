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
package com.android.tools.idea.model

import com.android.AndroidProjectTypes
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.testutils.waitForCondition
import com.android.tools.idea.model.AndroidManifestIndex.Companion.queryByPackageName
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.run.activity.IndexedActivityWrapper
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ui.UIUtil
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

private const val LIB_MODULE1_WITH_DEPENDENCY = "withDependency1"
private const val LIB_MODULE2_WITH_DEPENDENCY = "withDependency2"

class AndroidManifestIndexQueryUtilsTest : AndroidTestCase() {
  private lateinit var modificationListener: MergedManifestModificationListener

  override fun setUp() {
    super.setUp()

    MergedManifestModificationListener.ensureSubscribed(project)
    modificationListener = MergedManifestModificationListener(project)
  }

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>,
  ) {
    super.configureAdditionalModules(projectBuilder, modules)
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      LIB_MODULE1_WITH_DEPENDENCY,
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      true,
    )
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      LIB_MODULE2_WITH_DEPENDENCY,
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      true,
    )
  }

  fun testIsMainManifestReady() {
    assertThat(myFacet.queryIsMainManifestIndexReady()).isTrue()
  }

  fun testQueryMinSdkAndTargetSdk() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:debuggable="false" android:enabled='true'>
        <uses-sdk android:minSdkVersion='Lollipop' android:targetSdkVersion='28'/>
      </manifest>
      """
        .trimIndent()

    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) {
      // not sure why it's 21 -1
      myFacet.queryMinSdkAndTargetSdkFromManifestIndex().minSdk.apiLevel == 20
    }
    assertThat(myFacet.queryMinSdkAndTargetSdkFromManifestIndex().targetSdk.apiLevel).isEqualTo(28)
  }

  fun testQueryActivities() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:debuggable="false" android:enabled='true'>
        <application android:theme='@style/Theme.AppCompat'>
          <activity android:name='.EnabledActivity' android:enabled='true' android:exported='true'>
            <intent-filter>
              <action android:name='android.intent.action.MAIN'/>
              <category android:name='android.intent.category.DEFAULT'/>
            </intent-filter>
          </activity>
          <activity android:name='.DisabledActivity' android:enabled='false'>
          </activity>
          <activity-alias android:name='.EnabledAlias' android:enabled='true' android:targetActivity='.DisabledActivity' android:exported='true'>
          </activity-alias>
          <activity-alias android:name='.DisabledAlias' android:enabled='false' android:targetActivity='.EnabledActivity'>
          </activity-alias>
        </application>
      </manifest>
      """
        .trimIndent()

    val mainIntentFilter =
      IntentFilterRawText(
        actionNames = setOf("android.intent.action.MAIN"),
        categoryNames = setOf("android.intent.category.DEFAULT"),
      )

    val overrides = ManifestOverrides(directOverrides = emptyMap(), placeholders = emptyMap())

    val expectedActivities =
      listOf(
        IndexedActivityWrapper(
          name = ".EnabledActivity",
          enabled = "true",
          exported = "true",
          theme = null,
          intentFilters = setOf(mainIntentFilter),
          overrides = overrides,
          resolvedPackage = "com.example",
        ),
        IndexedActivityWrapper(
          name = ".DisabledActivity",
          enabled = "false",
          exported = null,
          theme = null,
          intentFilters = emptySet(),
          overrides = overrides,
          resolvedPackage = "com.example",
        ),
        IndexedActivityWrapper(
          name = ".EnabledAlias",
          enabled = "true",
          exported = "true",
          theme = null,
          intentFilters = emptySet(),
          overrides = overrides,
          resolvedPackage = "com.example",
        ),
        IndexedActivityWrapper(
          name = ".DisabledAlias",
          enabled = "false",
          exported = null,
          theme = null,
          intentFilters = emptySet(),
          overrides = overrides,
          resolvedPackage = "com.example",
        ),
      )

    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) {
      val activities = myFacet.queryActivitiesFromManifestIndex().getJoined()
      activities.containsAll(expectedActivities) && activities.size == expectedActivities.size
    }
  }

  fun testQueryCustomPermissionsAndGroups() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:debuggable="false" android:enabled='true'>
        <permission-group android:name='custom.permissions.CUSTOM_GROUP'/>
        <permission android:name='custom.permissions.IN_CUSTOM_GROUP' android:permissionGroup='custom.permissions.CUSTOM_GROUP'/>
        <permission android:name='custom.permissions.NO_GROUP'/>
      </manifest>
      """
        .trimIndent()
    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) {
      myFacet.queryCustomPermissionsFromManifestIndex() ==
        setOf("custom.permissions.IN_CUSTOM_GROUP", "custom.permissions.NO_GROUP")
    }

    assertThat(myFacet.queryCustomPermissionGroupsFromManifestIndex())
      .isEqualTo(setOf("custom.permissions.CUSTOM_GROUP"))

    // Update the primary manifest file of additional module with dependency.
    // Query results should be the union set of custom permissions and groups declared in primary
    // manifest files of main
    // module and additional module
    val anotherManifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:debuggable="false" android:enabled='true'>
        <permission-group android:name='custom.permissions.CUSTOM_GROUP1'/>
        <permission android:name='custom.permissions.IN_CUSTOM_GROUP1' android:permissionGroup='custom.permissions.CUSTOM_GROUP1'/>
        <permission android:name='custom.permissions.NO_GROUP1'/>
      </manifest>
      """
        .trimIndent()

    assertThat(myAdditionalModules.size).isEqualTo(2)
    val libModule = myAdditionalModules[0]
    updateManifestAndWaitForCondition(
      libModule,
      "additionalModules/$LIB_MODULE1_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML",
      anotherManifestContent,
    ) {
      myFacet.queryCustomPermissionsFromManifestIndex() ==
        setOf(
          "custom.permissions.IN_CUSTOM_GROUP",
          "custom.permissions.NO_GROUP",
          "custom.permissions.IN_CUSTOM_GROUP1",
          "custom.permissions.NO_GROUP1",
        )
    }

    assertThat(myFacet.queryCustomPermissionGroupsFromManifestIndex())
      .isEqualTo(setOf("custom.permissions.CUSTOM_GROUP", "custom.permissions.CUSTOM_GROUP1"))
  }

  fun testQueryApplicationTheme() {
    val manifestContentAppTheme =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:enabled='true'>
          <application android:theme='@style/Theme.AppCompat' android:debuggable="true">
          </application>
      </manifest>
      """
        .trimIndent()
    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContentAppTheme) {
      myFacet.queryApplicationThemeFromManifestIndex() == "@style/Theme.AppCompat"
    }

    val manifestContentNoAppTheme =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:enabled='true'>
          <application android:debuggable="false">
          </application>
      </manifest>
      """
        .trimIndent()
    updateManifestAndWaitForCondition(
      myModule,
      FN_ANDROID_MANIFEST_XML,
      manifestContentNoAppTheme,
    ) {
      myFacet.queryApplicationThemeFromManifestIndex() == null
    }
  }

  fun testQueryPackageName() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:enabled='true'>
      </manifest>
      """
        .trimIndent()
    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) {
      myFacet.queryPackageNameFromManifestIndex() == "com.example"
    }

    updateManifestAndWaitForCondition(
      myModule,
      FN_ANDROID_MANIFEST_XML,
      manifestContent.replace("example", "changed"),
    ) {
      myFacet.queryPackageNameFromManifestIndex() == "com.changed"
    }
  }

  private fun updateManifestAndWaitForCondition(
    module: Module,
    relativePath: String,
    manifestContents: String,
    condition: () -> Boolean,
  ) {
    deleteManifest(module)
    myFixture.addFileToProject(relativePath, manifestContents)

    UIUtil.dispatchAllInvocationEvents()
    modificationListener.waitAllUpdatesCompletedWithTimeout(1, TimeUnit.SECONDS)
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)

    // Unfortunately the above statements don't always ensure the manifest utils have up to date
    // data on Windows. I suspect that VFS updates are delayed and we are queuing the above "wait"
    // calls before the system even knows there anything to wait for. Unfortunately I haven't found
    // any way to explicitly test that the calls have come through; so instead each usage of this
    // method passes in a condition that indicates the updates (and correpsonding utils) are
    // returning the correct information.
    waitForCondition(10.seconds) { condition() }
  }

  fun testQueryAndroidFacets_packageChanged() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:enabled='true'>
      </manifest>
      """
        .trimIndent()
    var facets: List<AndroidFacet> = emptyList()
    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) {
      facets = queryByPackageName(project, "com.example", GlobalSearchScope.projectScope(project))
      facets.size == 1
    }
    assertThat(facets).containsExactly(myFacet)

    // change package name and see if corresponding modules are found or not
    updateManifestAndWaitForCondition(
      myModule,
      FN_ANDROID_MANIFEST_XML,
      manifestContent.replace("example", "changed"),
    ) {
      facets = queryByPackageName(project, "com.changed", GlobalSearchScope.projectScope(project))
      facets.size == 1
    }
    assertThat(facets).containsExactly(myFacet)
  }

  fun testQueryAndroidFacets_multipleModules() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.example' android:enabled='true'>
      </manifest>
      """
        .trimIndent()
    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) { true }

    // update the manifest file of 'additional module1' with dependency
    val manifestContentForLib1 =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.anotherExample' android:debuggable="false" android:enabled='true'>
        <permission-group android:name='custom.permissions.CUSTOM_GROUP1'/>
        <permission android:name='custom.permissions.IN_CUSTOM_GROUP1' android:permissionGroup='custom.permissions.CUSTOM_GROUP1'/>
        <permission android:name='custom.permissions.NO_GROUP1'/>
      </manifest>
      """
        .trimIndent()

    // update the manifest file of 'additional module2' with dependency
    val manifestContentForLib2 =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
        package='com.anotherExample' android:debuggable="false" android:enabled='true'>
        <permission-group android:name='custom.permissions.CUSTOM_GROUP1'/>
        <permission android:name='custom.permissions.IN_CUSTOM_GROUP1' android:permissionGroup='custom.permissions.CUSTOM_GROUP1'/>
        <permission android:name='custom.permissions.NO_GROUP1'/>
      </manifest>
      """
        .trimIndent()

    assertThat(myAdditionalModules.size).isEqualTo(2)
    val libModule1 = myAdditionalModules[0]
    val libFacet1 = AndroidFacet.getInstance(libModule1)
    updateManifestAndWaitForCondition(
      libModule1,
      "additionalModules/$LIB_MODULE1_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML",
      manifestContentForLib1,
    ) {
      true
    }

    val libModule2 = myAdditionalModules[1]
    val libFacet2 = AndroidFacet.getInstance(libModule2)
    var facets: List<AndroidFacet> = emptyList()
    updateManifestAndWaitForCondition(
      libModule2,
      "additionalModules/$LIB_MODULE2_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML",
      manifestContentForLib2,
    ) {
      facets =
        queryByPackageName(project, "com.anotherExample", GlobalSearchScope.projectScope(project))
      facets.size == 2
    }

    facets =
      queryByPackageName(project, "com.anotherExample", GlobalSearchScope.projectScope(project))
    // manifest files in additional 2 modules are with the same package name
    assertThat(facets).containsExactly(libFacet1, libFacet2)

    facets = queryByPackageName(project, "com.example", GlobalSearchScope.projectScope(project))
    assertThat(facets.size).isEqualTo(1)
    assertThat(facets[0]).isEqualTo(myFacet)
  }

  fun testQueryUsedFeatures() {
    val manifestContent =
      // language=XML
      """
      <?xml version='1.0' encoding='utf-8'?>
      <manifest xmlns:android='http://schemas.android.com/apk/res/android'
        package='com.example' android:enabled='true'>
        <uses-feature android:name="android.hardware.type.watch" android:required="true" android:glEsVersion="integer" />
      </manifest>
      """
        .trimIndent()
    updateManifestAndWaitForCondition(myModule, FN_ANDROID_MANIFEST_XML, manifestContent) {
      myFacet.queryUsedFeaturesFromManifestIndex().singleOrNull() ==
        UsedFeatureRawText(name = "android.hardware.type.watch", required = "true")
    }

    // change required value
    updateManifestAndWaitForCondition(
      myModule,
      FN_ANDROID_MANIFEST_XML,
      manifestContent.replace("true", "false"),
    ) {
      myFacet.queryUsedFeaturesFromManifestIndex().singleOrNull() ==
        UsedFeatureRawText(name = "android.hardware.type.watch", required = "false")
    }
  }
}
