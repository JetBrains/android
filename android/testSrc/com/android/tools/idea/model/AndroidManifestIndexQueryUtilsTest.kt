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
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.run.activity.IndexedActivityWrapper
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase

class AndroidManifestIndexQueryUtilsTest : AndroidTestCase() {
  private val LIB_MODULE_WITH_DEPENDENCY = "withDependency"

  override fun setUp() {
    super.setUp()
    MergedManifestModificationListener.ensureSubscribed(project)
  }

  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: MutableList<MyAdditionalModuleData>) {
    super.configureAdditionalModules(projectBuilder, modules)
    addModuleWithAndroidFacet(projectBuilder, modules, LIB_MODULE_WITH_DEPENDENCY, AndroidProjectTypes.PROJECT_TYPE_LIBRARY, true)
  }

  fun testQueryMinSdkAndTargetSdk() {
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
    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:debuggable="false" android:enabled='true'>
      <application android:theme='@style/Theme.AppCompat'>
        <activity android:name='.EnabledActivity' android:enabled='true'>
          <intent-filter>
            <action android:name='android.intent.action.MAIN'/>
            <category android:name='android.intent.category.DEFAULT'/>
          </intent-filter>
        </activity>
        <activity android:name='.DisabledActivity' android:enabled='false'>
        </activity>
        <activity-alias android:name='.EnabledAlias' android:enabled='true' android:targetActivity='.DisabledActivity'>
        </activity-alias>
        <activity-alias android:name='.DisabledAlias' android:enabled='false' android:targetActivity='.EnabledActivity'>
        </activity-alias>
      </application>
    </manifest>
    """.trimIndent()

    updateManifest(myModule, FN_ANDROID_MANIFEST_XML, manifestContent)

    val activities = myFacet.queryActivitiesFromManifestIndex()

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
        intentFilters = setOf(mainIntentFilter),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".DisabledActivity",
        enabled = "false",
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".EnabledAlias",
        enabled = "true",
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".DisabledAlias",
        enabled = "false",
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      )
    )
  }

  fun testQueryCustomPermissionsAndGroups() {
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

    Truth.assertThat(myAdditionalModules.size).isEqualTo(1)
    val libModule = myAdditionalModules[0]
    updateManifest(libModule, "additionalModules/$LIB_MODULE_WITH_DEPENDENCY/$FN_ANDROID_MANIFEST_XML", anotherManifestContent)

    Truth.assertThat(myFacet.queryCustomPermissionsFromManifestIndex()).isEqualTo(
      setOf("custom.permissions.IN_CUSTOM_GROUP",
            "custom.permissions.NO_GROUP",
            "custom.permissions.IN_CUSTOM_GROUP1",
            "custom.permissions.NO_GROUP1"))

    Truth.assertThat(myFacet.queryCustomPermissionGroupsFromManifestIndex()).isEqualTo(
      setOf("custom.permissions.CUSTOM_GROUP", "custom.permissions.CUSTOM_GROUP1"))
  }

  private fun updateManifest(module: Module, relativePath: String, manifestContents: String) {
    deleteManifest(module)
    myFixture.addFileToProject(relativePath, manifestContents)
  }
}
