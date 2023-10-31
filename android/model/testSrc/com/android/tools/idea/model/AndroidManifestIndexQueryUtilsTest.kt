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
import com.android.tools.idea.model.AndroidManifestIndex.Companion.queryByPackageName
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.run.activity.IndexedActivityWrapper
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.TimeUnit

class AndroidManifestIndexQueryUtilsTest : AndroidTestCase() {
  private val LIB_MODULE1_WITH_DEPENDENCY = "withDependency1"
  private val LIB_MODULE2_WITH_DEPENDENCY = "withDependency2"
  private lateinit var modificationListener: MergedManifestModificationListener

  override fun setUp() {
    super.setUp()
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    MergedManifestModificationListener.ensureSubscribed(project)
    modificationListener = MergedManifestModificationListener(project)
  }


  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: MutableList<MyAdditionalModuleData>) {
    super.configureAdditionalModules(projectBuilder, modules)
    addModuleWithAndroidFacet(projectBuilder, modules, LIB_MODULE1_WITH_DEPENDENCY, AndroidProjectTypes.PROJECT_TYPE_LIBRARY, true)
    addModuleWithAndroidFacet(projectBuilder, modules, LIB_MODULE2_WITH_DEPENDENCY, AndroidProjectTypes.PROJECT_TYPE_LIBRARY, true)
  }

  fun testQueryMinSdkAndTargetSdk() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:debuggable="false" android:enabled='true'>
      <uses-sdk android:minSdkVersion='Lollipop' android:targetSdkVersion='28'/>
    </manifest>
    """.trimIndent()

    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)
    // not sure why it's 21 -1
    Truth.assertThat(myFacet.queryMinSdkAndTargetSdkFromManifestIndex().minSdk.apiLevel).isEqualTo(20)
    Truth.assertThat(myFacet.queryMinSdkAndTargetSdkFromManifestIndex().targetSdk.apiLevel).isEqualTo(28)
  }

  fun testQueryActivities() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
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
    """.trimIndent()

    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)

    val activities = myFacet.queryActivitiesFromManifestIndex().getJoined()

    val mainIntentFilter = IntentFilterRawText(
      actionNames = setOf("android.intent.action.MAIN"),
      categoryNames = setOf("android.intent.category.DEFAULT")
    )

    val overrides = ManifestOverrides(
      directOverrides = emptyMap(),
      placeholders = emptyMap()
    )

    Truth.assertThat(activities).containsExactly(
      IndexedActivityWrapper(
        name = ".EnabledActivity",
        enabled = "true",
        exported = "true",
        theme = null,
        intentFilters = setOf(mainIntentFilter),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".DisabledActivity",
        enabled = "false",
        exported = null,
        theme = null,
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".EnabledAlias",
        enabled = "true",
        exported = "true",
        theme = null,
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".DisabledAlias",
        enabled = "false",
        exported = null,
        theme = null,
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      )
    )
  }

  fun testQueryCustomPermissionsAndGroups() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:debuggable="false" android:enabled='true'>
    <permission-group android:name='custom.permissions.CUSTOM_GROUP'/>
    <permission android:name='custom.permissions.IN_CUSTOM_GROUP' android:permissionGroup='custom.permissions.CUSTOM_GROUP'/>
    <permission android:name='custom.permissions.NO_GROUP'/>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)

    Truth.assertThat(myFacet.queryCustomPermissionsFromManifestIndex()).isEqualTo(
      setOf("custom.permissions.IN_CUSTOM_GROUP", "custom.permissions.NO_GROUP"))
    Truth.assertThat(myFacet.queryCustomPermissionGroupsFromManifestIndex()).isEqualTo(
      setOf("custom.permissions.CUSTOM_GROUP"))

    // Update the primary manifest file of additional module with dependency.
    // Query results should be the union set of custom permissions and groups declared in primary manifest files of main
    // module and additional module
    val anotherManifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:debuggable="false" android:enabled='true'>
    <permission-group android:name='custom.permissions.CUSTOM_GROUP1'/>
    <permission android:name='custom.permissions.IN_CUSTOM_GROUP1' android:permissionGroup='custom.permissions.CUSTOM_GROUP1'/>
    <permission android:name='custom.permissions.NO_GROUP1'/>
    </manifest>
    """.trimIndent()

    Truth.assertThat(myAdditionalModules.size).isEqualTo(2)
    val libModule = myAdditionalModules[0]
    updateManifest(libModule, "additionalModules/$LIB_MODULE1_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML", anotherManifestContent)

    Truth.assertThat(myFacet.queryCustomPermissionsFromManifestIndex()).isEqualTo(
      setOf("custom.permissions.IN_CUSTOM_GROUP",
            "custom.permissions.NO_GROUP",
            "custom.permissions.IN_CUSTOM_GROUP1",
            "custom.permissions.NO_GROUP1"))

    Truth.assertThat(myFacet.queryCustomPermissionGroupsFromManifestIndex()).isEqualTo(
      setOf("custom.permissions.CUSTOM_GROUP", "custom.permissions.CUSTOM_GROUP1"))
  }

  fun testQueryApplicationDebuggable() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContentDebuggable = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
        <application android:theme='@style/Theme.AppCompat' android:debuggable="true">
        </application>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContentDebuggable)

    Truth.assertThat(myFacet.queryApplicationDebuggableFromManifestIndex()).isTrue()

    val manifestContentNotDebuggable = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
        <application android:theme='@style/Theme.AppCompat' android:debuggable="false">
        </application>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContentNotDebuggable)

    Truth.assertThat(myFacet.queryApplicationDebuggableFromManifestIndex()).isFalse()

    val manifestContentDebuggableIsNull = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
        <application android:theme='@style/Theme.AppCompat'>
        </application>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContentDebuggableIsNull)

    Truth.assertThat(myFacet.queryApplicationDebuggableFromManifestIndex()).isNull()
  }

  fun testQueryApplicationTheme() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContentAppTheme = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
        <application android:theme='@style/Theme.AppCompat' android:debuggable="true">
        </application>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContentAppTheme)

    Truth.assertThat(myFacet.queryApplicationThemeFromManifestIndex()).isEqualTo("@style/Theme.AppCompat")

    val manifestContentNoAppTheme = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
        <application android:debuggable="false">
        </application>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContentNoAppTheme)

    Truth.assertThat(myFacet.queryApplicationThemeFromManifestIndex()).isNull()
  }

  fun testQueryPackageName() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)
    Truth.assertThat(myFacet.queryPackageNameFromManifestIndex()).isEqualTo("com.example")

    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent.replace("example", "changed"))
    Truth.assertThat(myFacet.queryPackageNameFromManifestIndex()).isEqualTo("com.changed")
  }

  private fun updateManifest(module: Module, relativePath: String, manifestContents: String) {
    deleteManifest(module)
    myFixture.addFileToProject(relativePath, manifestContents)
    modificationListener.waitAllUpdatesCompletedWithTimeout(1, TimeUnit.SECONDS)
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
  }

  fun testQueryAndroidFacets_packageChanged() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)
    var facets = queryByPackageName(project, "com.example", GlobalSearchScope.projectScope(project)).toList()
    Truth.assertThat(facets.size).isEqualTo(1)
    Truth.assertThat(facets[0]).isEqualTo(myFacet)

    // change package name and see if corresponding modules are found or not
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent.replace("example", "changed"))
    facets = queryByPackageName(project, "com.changed", GlobalSearchScope.projectScope(project)).toList()
    Truth.assertThat(facets.size).isEqualTo(1)
    Truth.assertThat(facets[0]).isEqualTo(myFacet)
  }

  fun testQueryAndroidFacets_multipleModules() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:enabled='true'>
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)

    // update the manifest file of 'additional module1' with dependency
    val manifestContentForLib1 = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.anotherExample' android:debuggable="false" android:enabled='true'>
    <permission-group android:name='custom.permissions.CUSTOM_GROUP1'/>
    <permission android:name='custom.permissions.IN_CUSTOM_GROUP1' android:permissionGroup='custom.permissions.CUSTOM_GROUP1'/>
    <permission android:name='custom.permissions.NO_GROUP1'/>
    </manifest>
    """.trimIndent()

    // update the manifest file of 'additional module2' with dependency
    val manifestContentForLib2 = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.anotherExample' android:debuggable="false" android:enabled='true'>
    <permission-group android:name='custom.permissions.CUSTOM_GROUP1'/>
    <permission android:name='custom.permissions.IN_CUSTOM_GROUP1' android:permissionGroup='custom.permissions.CUSTOM_GROUP1'/>
    <permission android:name='custom.permissions.NO_GROUP1'/>
    </manifest>
    """.trimIndent()

    Truth.assertThat(myAdditionalModules.size).isEqualTo(2)
    val libModule1 = myAdditionalModules[0]
    val libFacet1 = AndroidFacet.getInstance(libModule1)
    updateManifest(libModule1, "additionalModules/$LIB_MODULE1_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML", manifestContentForLib1)

    val libModule2 = myAdditionalModules[1]
    val libFacet2 = AndroidFacet.getInstance(libModule2)
    updateManifest(libModule2, "additionalModules/$LIB_MODULE2_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML", manifestContentForLib2)


    var facets = queryByPackageName(project, "com.anotherExample", GlobalSearchScope.projectScope(project)).toList()
    // manifest files in additional 2 modules are with the same package name
    Truth.assertThat(facets.size).isEqualTo(2)
    Truth.assertThat(facets).containsExactly(libFacet1, libFacet2)

    facets = queryByPackageName(project, "com.example", GlobalSearchScope.projectScope(project)).toList()
    Truth.assertThat(facets.size).isEqualTo(1)
    Truth.assertThat(facets[0]).isEqualTo(myFacet)
  }

  fun testQueryUsedFeatures() {
    if (SystemInfo.isWindows) {
      // TODO (b/192850109): AndroidManifestIndexQueryUtilsTest is failing too frequently.
      return
    }

    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android'
      package='com.example' android:enabled='true'>
      <uses-feature android:name="android.hardware.type.watch" android:required="true" android:glEsVersion="integer" />
    </manifest>
    """.trimIndent()
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)
    Truth.assertThat(myFacet.queryUsedFeaturesFromManifestIndex()).isEqualTo(
      setOf(UsedFeatureRawText(name = "android.hardware.type.watch", required = "true"))
    )

    // change required value
    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent.replace("true", "false"))
    Truth.assertThat(myFacet.queryUsedFeaturesFromManifestIndex()).isEqualTo(
      setOf(UsedFeatureRawText(name = "android.hardware.type.watch", required = "false"))
    )
  }
}
