/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.ComponentMatchersBuilder
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.idea.vitals.client.grpc.MOST_AFFECTED_OS
import com.android.tools.idea.vitals.client.grpc.VitalsGrpcServerRule
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

private val CONNECTION = VitalsConnection("com.example.minapp", "Test App", true)

class VitalsIntegrationTest {

  @get:Rule val system = AndroidSystem.standard()

  @get:Rule val grpcServerRule = VitalsGrpcServerRule(CONNECTION)

  @get:Rule var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun basic() {
    println("Test gRPC server started at localhost:${grpcServerRule.server.port}")
    grpcServerRule.database.addIssue(TEST_ISSUE1)
    grpcServerRule.database.addIssue(TEST_ISSUE2)

    // Create a new android project, and set a fixed distribution
    val project =
      AndroidProject(
        "tools/adt/idea/app-quality-insights/play-vitals/integration-tests/testData/projects/appinsights"
      )

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(
      MavenRepo(
        "tools/adt/idea/app-quality-insights/play-vitals/integration-tests/openproject_deps.manifest"
      )
    )

    val install = system.installation

    install.addVmOption("-Dappinsights.enable.play.vitals=true")
    install.addVmOption(
      "-Dappinsights.play.vitals.grpc.server=localhost:${grpcServerRule.server.port}"
    )
    install.addVmOption("-Dappinsights.play.vitals.grpc.use.transport.security=false")
    system.setEnv("GOOGLE_LOGIN_USER", "test_user@google.com")
    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      assertThat(studio.showToolWindow("App Quality Insights")).isTrue()

      install.ideaLog.waitForMatchingLine(
        "^.*Accessible Android Vitals connections:.*appId=com\\.example\\.minapp.*$",
        20,
        TimeUnit.SECONDS
      )

      // Verify the issues table is displayed.
      studio.waitForComponent(
        ComponentMatchersBuilder().apply {
          addSwingClassRegexMatch(".*AppInsightsIssuesTableView\\\$IssuesTableView$")
        }
      )

      // Verify the stack trace panel is displayed.
      studio.waitForComponent(
        ComponentMatchersBuilder().apply {
          addSwingClassRegexMatch(".*vitals\\.ui\\.VitalsIssueDetailsPanel$")
        }
      )
      // Verify the details panel is displayed.
      studio.waitForComponent(
        ComponentMatchersBuilder().apply {
          addSwingClassRegexMatch(".*insights\\.ui\\.DistributionsContainerPanel$")
        }
      )

      studio.waitForComponentWithExactText("Android 3.1 (API 12)")

      studio.waitForComponentWithExactText("Most affected Android version: $MOST_AFFECTED_OS")
    }
  }
}
