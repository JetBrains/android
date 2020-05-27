/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenProjectStructureQuickfix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class DaemonContextMismatchIssueCheckerTest : AndroidGradleTestCase() {
  private val daemonContextMismatchIssueChecker = DaemonContextMismatchIssueChecker()

  fun testCheckIssueWithErrorFromBugReport() {
    val errorMessage = """
      The newly created daemon process has a different context than expected.
      It won't be possible to reconnect to this daemon. Context mismatch: 
      Java home is different.
      javaHome=c:\Program Files\Java\jdk,daemonRegistryDir=C:\Users\user.name\.gradle\daemon,pid=7868,idleTimeout=null]
      javaHome=C:\Program Files\Java\jdk\jre,daemonRegistryDir=C:\Users\user.name\.gradle\daemon,pid=4792,idleTimeout=10800000]
      """.trimIndent()
    val expectedNotificationMessage = "Expecting: 'c:\\Program Files\\Java\\jdk' but was: 'C:\\Program Files\\Java\\jdk\\jre'."

    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errorMessage), null, null)
    val buildIssue = daemonContextMismatchIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(expectedNotificationMessage)
    // Verify quickFix
    assertThat(buildIssue.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenProjectStructureQuickfix::class.java)
  }

  fun testCheckIssueWithErrorFromGradleForum() {
    val errorMessage = """
      The newly created daemon process has a different context than expected.
      It won't be possible to reconnect to this daemon. Context mismatch: 
      Java home is different.
      Wanted: DefaultDaemonContext[uid=null,javaHome=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home,daemonRegistryDir=/Users/Nikem/.gradle/daemon,pid=555]
      Actual: DefaultDaemonContext[uid=0f3a0315-c1e6-44d6-962d-9a604d59a158,javaHome=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home/jre,daemonRegistryDir=/Users/Nikem/.gradle/daemon,pid=568]
      """.trimIndent()
    val expectedNotificationMessage = "Expecting: '/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home' but was: '/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home/jre'."

    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errorMessage), null, null)
    val buildIssue = daemonContextMismatchIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(expectedNotificationMessage)
    // Verify quickFix
    assertThat(buildIssue.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenProjectStructureQuickfix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      daemonContextMismatchIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: The newly created daemon process has a different context than expected. \n" +
        "what went wrong: \nJava home is different.\n Please check your build files.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}