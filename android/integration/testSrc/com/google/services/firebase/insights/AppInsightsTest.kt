/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.services.firebase.insights

import com.android.testutils.TestUtils
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSdk
import com.android.tools.asdriver.tests.AndroidStudioInstallation
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.XvfbServer
import com.google.common.truth.Truth.assertThat
import com.google.services.firebase.insights.client.grpc.FAKE_10_HOURS_AGO
import com.google.services.firebase.insights.client.grpc.FakeCrashlyticsDatabase
import com.google.services.firebase.insights.client.grpc.fakeSampleName
import com.google.services.firebase.insights.client.grpc.fakedata.ANDROID_11
import com.google.services.firebase.insights.client.grpc.fakedata.ANDROID_12
import com.google.services.firebase.insights.client.grpc.fakedata.END_USER_1
import com.google.services.firebase.insights.client.grpc.fakedata.END_USER_2
import com.google.services.firebase.insights.client.grpc.fakedata.GOOGLE_DEVICE_PIXEL_4
import com.google.services.firebase.insights.client.grpc.fakedata.GOOGLE_DEVICE_PIXEL_5
import com.google.services.firebase.insights.client.grpc.fakedata.SAMPLE_NON_FATAL_EXCEPTION
import com.google.services.firebase.insights.client.grpc.fakedata.SAMPLE_NON_FATAL_ISSUE
import com.google.services.firebase.insights.client.grpc.toProtoTimestamp
import com.google.services.firebase.insights.proto.Device
import com.google.services.firebase.insights.proto.OperatingSystem
import com.google.services.firebase.insights.proto.User
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

// The configuration here and in the google-services.json of the test project must be
// kept in sync with the connection parameters in the fake crashlytics gRpc service.
// As of writing this comment, the fake gRpc service has a hashmap storage that is
// keyed by FirebaseConnection, and it can only derive the mobileSdkAppId and the
// projectNumber, with appId and projectId hardcoded to dummy_id.
private val TEST_CONNECTION = FirebaseConnection(
  appId = "dummy_id",
  mobileSdkAppId = "app_id1",
  projectId = "dummy_id",
  projectNumber = "12345"
)

class AppInsightsTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @get:Rule
  val grpcServerRule = AppInsightsGrpcServerRule()

  private fun addCrash(
    name: String,
    buildVersion: String = "debug-v0.120 (120)",
    device: Device = GOOGLE_DEVICE_PIXEL_5.build(),
    user: User = END_USER_1.build(),
    os: OperatingSystem = ANDROID_12.build(),
  ) {
    grpcServerRule.database.add(
      firebaseConnection = TEST_CONNECTION,
      crash =
      FakeCrashlyticsDatabase.Crash(
        issue =
        SAMPLE_NON_FATAL_ISSUE
          .apply { sampleEvent = fakeSampleName(TEST_CONNECTION, name) }
          .build(),
        sampleDisplayBuildVersion = buildVersion,
        sampleName = fakeSampleName(TEST_CONNECTION, name),
        sampleTime = FAKE_10_HOURS_AGO.toProtoTimestamp(),
        sampleDevice = device,
        sampleEndUser = user,
        sampleOperatingSystem = os,
        sampleExceptions = SAMPLE_NON_FATAL_EXCEPTION.build()
      )
    )
  }

  private fun setUpDatabase() {
    addCrash(name = "sample_crash_1")
    addCrash(name = "sample_crash_2", device = GOOGLE_DEVICE_PIXEL_4.build())
    addCrash(name = "sample_crash_3", buildVersion = "debug-v0.126 (126)")
    addCrash(name = "sample_crash_4", os = ANDROID_11.build())
    addCrash(name = "sample_crash_5", user = END_USER_2.build())
  }

  @Test
  fun basic() {
    println("Test gRPC server started at localhost:${grpcServerRule.server.port}")
    setUpDatabase()

    val tempDir = tempFolder.newFolder("appinsights-test").toPath()
    val install = AndroidStudioInstallation.fromZip(tempDir)
    install.createFirstRunXml()
    val env = HashMap<String, String>()

    // Set up a simple Android Sdk (an empty one works for just opening and syncing a project)
    val sdk = AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()))
    sdk.install(env)

    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/appinsights")
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip")
    val projectPath = project.install(tempDir)

    // Mark that project as trusted
    install.trustPath(projectPath)

    // Create a maven repo and set it up in the installation and environment
    val mavenRepo = MavenRepo("tools/adt/idea/android/integration/openproject_deps.manifest")
    mavenRepo.install(tempDir, install, env)
    
    install.addVmOption("-Dappinsights.enable.new.crashlytics.api=true")
    install.addVmOption("-Dappinsights.crashlytics.grpc.server=localhost:${grpcServerRule.server.port}")
    install.addVmOption("-Dappinsights.crashlytics.grpc.use.transport.security=false")
    XvfbServer().use { display ->
      env["GOOGLE_LOGIN_USER"] = "test_user@google.com"
      install.run(display, env, arrayOf(projectPath.toString())).use { studio ->
        var matcher = install.ideaLog.waitForMatchingLine(".*Unindexed files update took (.*)", 120, TimeUnit.SECONDS)
        println("Indexing took " + matcher.group(1))
        matcher = install.ideaLog.waitForMatchingLine(".*Gradle sync finished in (.*)", 180, TimeUnit.SECONDS)
        println("Sync took " + matcher.group(1))

        studio.waitForIndex()

        assertThat(studio.showToolWindow("App Quality Insights")).isTrue()

        matcher = install.ideaLog.waitForMatchingLine(".*New app states (.*?activeConnection=debug: \\[dummy_id\\].*)$", 20, TimeUnit.SECONDS)
        println("App states ${matcher.group(1)}")
      }
    }
  }
}