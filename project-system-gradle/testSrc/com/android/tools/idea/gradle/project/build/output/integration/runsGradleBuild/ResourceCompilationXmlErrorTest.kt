/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output.integration.runsGradleBuild

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE
import org.junit.Test

class ResourceCompilationXmlErrorTest : BuildOutputIntegrationTestBase() {

  @Test
  fun testBrokenLayoutXml() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("app/src/main/res/layout/activity_my.xml").let {
      // Missing 'xmlns:android=' entry, see b/280524982
      it.writeText("""
        <RelativeLayout
            xmlns:tools="http://schemas.android.com/tools"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:paddingLeft="@dimen/activity_horizontal_margin"
            android:paddingRight="@dimen/activity_horizontal_margin"
            android:paddingTop="@dimen/activity_vertical_margin"
            android:paddingBottom="@dimen/activity_vertical_margin"
            tools:context=".MyActivity">

            <TextView
                android:text="@string/hello_world"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />

        </RelativeLayout>
      """.trimIndent())
    }
    val projectRoot = preparedProject.root
    preparedProject.open { project ->
      val buildEvents = project.buildCollectingEvents(expectSuccess = false)

      val errorTreePath = "root > [Task :app:mergeDebugResources] > ERROR:'Resource compilation failed (Failed to compile resource file: $projectRoot/app/src/main/res/layout/activity_my.xml: . Cause: javax.xml.stream.XMLStreamException: ParseError at [row,col]:[9,33]'"

      assertThat(buildEvents.printEvents()).isEqualTo("""
$errorTreePath
root > 'failed'
""".trimIndent())
      sequenceOf(errorTreePath).map { buildEvents.findBuildEvent(it) }.forEach { event ->
        assertThat(event.description).startsWith("""
Execution failed for task ':app:mergeDebugResources'.
> A failure occurred while executing com.android.build.gradle.internal.res.ResourceCompilerRunnable
   > Resource compilation failed (Failed to compile resource file: $projectRoot/app/src/main/res/layout/activity_my.xml: . Cause: javax.xml.stream.XMLStreamException: ParseError at [row,col]:[9,33]
     Message: http://www.w3.org/TR/1999/REC-xml-names-19990114#AttributePrefixUnbound?RelativeLayout&android:layout_width&android). Check logs for more details.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
BUILD FAILED in
      """.trimIndent())
      }

      // Failure should be filtered from BOW finish event, it contains useless information for this error.
      assertThat(buildEvents.finishEventFailures()).isEmpty()
      verifyStats(UNKNOWN_ERROR_TYPE)
    }
  }
}