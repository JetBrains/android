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

import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.util.Key
import com.intellij.util.indexing.FileContent
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidManifestIndexTest {
  @Test
  fun indexer_wellFormedManifest() {
    @Language("xml")
    val manifestContent = """
<?xml version='1.0' encoding='utf-8'?>
<manifest xmlns:android='http://schemas.android.com/apk/res/android'
    xmlns:tools="http://schemas.android.com/tools"
    package='com.example'
    android:debuggable="false"
    android:enabled='true'>

    <application android:theme='@style/Theme.AppCompat'>
        <activity
            android:name='.EnabledActivity'
            android:enabled='true'
            tools:node='merge'>
            <intent-filter>
                <action android:name='android.intent.action.MAIN' />
                <category android:name='android.intent.category.DEFAULT' />
            </intent-filter>
        </activity>
        
        <activity
            android:name='.DisabledActivity'
            android:enabled='false'
            tools:node='merge'>
        </activity>

        <activity-alias
            android:name='.EnabledAlias'
            android:enabled='true'
            android:targetActivity='.DisabledActivity'
            tools:node='merge'>
        </activity-alias>
        
        <activity-alias
            android:name='.DisabledAlias'
            android:enabled='false'
            android:targetActivity='.EnabledActivity'
            tools:node='merge'>
        </activity-alias>
    </application>
    <uses-permission android:name='android.permission.SEND_SMS' />
    <uses-permission-sdk-23 android:name='custom.permissions.NO_GROUP' tools:node='remove'/>
    <permission-group android:name='custom.permissions.CUSTOM_GROUP' tools:node='remove'/>

    <permission
        android:name='custom.permissions.IN_CUSTOM_GROUP'
        android:permissionGroup='custom.permissions.CUSTOM_GROUP' 
        tools:node='remove'/>
    <permission android:name='custom.permissions.NO_GROUP' />

    <uses-sdk
        tools:overrideLibrary="com.google.android.libraries.foo, com.google.android.libraries.bar"
        android:minSdkVersion='22'
        android:targetSdkVersion='28'
        tools:node='merge'/>
</manifest>
    """.trimIndent()
    val manifest = AndroidManifestIndex.Indexer.computeValue(FakeXmlFileContent(manifestContent))
    assertThat(manifest).isEqualTo(
      AndroidManifestRawText(
        activities = setOf(
          ActivityRawText(
            name = ".EnabledActivity",
            enabled = "true",
            nodeMergeRule = "merge",
            intentFilters = setOf(
              IntentFilterRawText(actionNames = setOf("android.intent.action.MAIN"),
                                  categoryNames = setOf("android.intent.category.DEFAULT"))
            )
          ),
          ActivityRawText(name = ".DisabledActivity", enabled = "false", nodeMergeRule = "merge", intentFilters = setOf())
        ),
        activityAliases = setOf(
          ActivityAliasRawText(name = ".EnabledAlias", targetActivity = ".DisabledActivity",
                               enabled = "true", nodeMergeRule = "merge", intentFilters = setOf()),
          ActivityAliasRawText(name = ".DisabledAlias", targetActivity = ".EnabledActivity",
                               enabled = "false", nodeMergeRule = "merge", intentFilters = setOf())
        ),
        customPermissionGroupNames = setOf(AndroidNameWithMergeRules(name = "custom.permissions.CUSTOM_GROUP", nodeMergeRule = "remove")),
        customPermissionNames = setOf(AndroidNameWithMergeRules(name = "custom.permissions.IN_CUSTOM_GROUP", nodeMergeRule = "remove"),
                                      AndroidNameWithMergeRules(name = "custom.permissions.NO_GROUP", nodeMergeRule = null)),
        debuggable = "false",
        enabled = "true",
        packageName = "com.example",
        usedPermissionNames = setOf(AndroidNameWithMergeRules(name = "android.permission.SEND_SMS", nodeMergeRule = null),
                                    AndroidNameWithMergeRules(name = "custom.permissions.NO_GROUP", nodeMergeRule = "remove")),
        theme = "@style/Theme.AppCompat",
        usesSdk = SdkRawText(minSdkLevel = "22",
                             targetSdkLevel = "28",
                             nodeMergeRule = "merge",
                             overrideLibraries = setOf("com.google.android.libraries.foo", "com.google.android.libraries.bar"))
      )
    )
  }
}

private class FakeXmlFileContent(private val content: String) : FileContent {
  private val file = MockVirtualFile("", content)

  override fun getContentAsText() = content
  override fun getContent() = content.toByteArray()
  override fun <T : Any?> getUserData(key: Key<T>): T? = throw UnsupportedOperationException()
  override fun getFileType(): FileType = StdFileTypes.XML
  override fun getFile() = file
  override fun getFileName() = ""
  override fun <T : Any?> putUserData(key: Key<T>, value: T?) = throw UnsupportedOperationException()
  override fun getProject() = throw UnsupportedOperationException()
  override fun getPsiFile() = throw UnsupportedOperationException()
}