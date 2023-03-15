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
package com.android.build.attribution.analyzers

import com.android.SdkConstants
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.getSuccessfulResult
import com.android.build.output.DownloadRequestItem
import com.android.build.output.DownloadsInfoExecutionConsole
import com.android.build.output.DownloadsInfoPresentableEvent
import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

class DownloadsAnalyzerTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()
  @get:Rule
  val temporaryFolder = TemporaryFolder()


  private lateinit var server1: HttpServerWrapper
  private lateinit var server2: HttpServerWrapper
  private val tracker = TestUsageTracker(VirtualTimeScheduler())
  private val syncDownloadInfoExecutionConsoles = CopyOnWriteArrayList<DownloadsInfoExecutionConsole>()
  private val buildDownloadInfoExecutionConsoles = CopyOnWriteArrayList<DownloadsInfoExecutionConsole>()

  @Before
  fun setup() {
    StudioFlags.BUILD_OUTPUT_DOWNLOADS_INFORMATION.override(true)
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
    StudioFlags.BUILD_OUTPUT_DOWNLOADS_INFORMATION.clearOverride()
  }

  @Test
  fun testRunningBuildWithDownloadsFromLocalServers() {
    UsageTracker.setWriterForTest(tracker)

    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BUILD_ANALYZER_CHECK_ANALYZERS)

    val gradleHome = temporaryFolder.newFolder("gradleHome").toString()

    // Set up servers.
    server1 = HttpServerWrapper("Server1", projectRule.testRootDisposable)
    server2 = HttpServerWrapper("Server2", projectRule.testRootDisposable)
    setupServerFiles()
    addBuildFileContent(preparedProject)
    addBuildSrcFileContent(preparedProject)

    preparedProject.runTest(updateOptions = { it.copy(syncViewEventHandler = { event ->
      if (event is DownloadsInfoPresentableEvent) {
        val downloadsInfoExecutionConsole = event.presentationData.executionConsole as DownloadsInfoExecutionConsole
        syncDownloadInfoExecutionConsoles.add(downloadsInfoExecutionConsole)
      }
    })}) {
      if (!TestUtils.runningFromBazel()) {
        //TODO (b/240887542): this section seems to be the root cause for the Directory not empty failure.
        //      Changing gradle home starts a new daemon and it seems that sometimes it does not stop before test tries to clean this up.
        //      This actually only needed for running tests locally because gradle home is likely not clean in this case.
        //      Let's try like this and see if it indeed helps with the flake.
        invokeAndWaitIfNeeded {
          ApplicationManager.getApplication().runWriteAction {
            GradleSettings.getInstance(project).serviceDirectoryPath = gradleHome
          }
        }
      }

      verifyDownloadsInformationOnSyncOutput()
      runSecondSyncAndVerifyNoStaleDataIsPresent()
      // Clear any requests happened on sync.
      HttpServerWrapper.detectedHttpRequests.clear()
      invokeBuild()
      verifyInformationOnBuildOutput()

      // Verify analyzer result.
      val buildAnalyzerStorageManager = project.getService(BuildAnalyzerStorageManager::class.java)
      val results = buildAnalyzerStorageManager.getSuccessfulResult()
      val result = results.getDownloadsAnalyzerResult()

      val testRepositoryResult = (result as DownloadsAnalyzer.ActiveResult).repositoryResults.map { TestingRepositoryResult(it) }
      Truth.assertThat(testRepositoryResult).containsExactly(
        // Only one missed because HEAD request is not reported by gradle currently.
        TestingRepositoryResult(DownloadsAnalyzer.OtherRepository(server1.authority), 0, 1, 1),
        TestingRepositoryResult(DownloadsAnalyzer.OtherRepository(server2.authority), 4, 0, 0),
      )
    }
  }

  private fun verifyDownloadsInformationOnSyncOutput() {
    println(HttpServerWrapper.detectedHttpRequests.joinToString(separator = "\n", prefix = "==All Detected requests to local servers on Sync:\n", postfix = "\n===="))
    // Verify interaction with server was as expected.
    // Sometimes requests can change the order so compare without order.
    // There will be many failed requests for all dependencies, but we should check that expected ones are here.
    // `C` is part of project dependency, `D` is part of buildSrc dependency. Both should be requested on Sync.
    // For `-sources` and `-javadoc`:
    //  - android model builder requests both thus we should see both requests for C.
    //  - platform model builder requests only sources by default for buildscript thus we should see only sources for D.
    Truth.assertThat(HttpServerWrapper.detectedHttpRequests.filter { it.contains("/example/") }).containsExactlyElementsIn("""
      Server1: GET on /example/C/1.0/C-1.0.pom - return error 404
      Server1: HEAD on /example/C/1.0/C-1.0.jar - return error 404
      Server2: GET on /example/C/1.0/C-1.0.pom - OK
      Server2: GET on /example/C/1.0/C-1.0.jar - OK
      Server2: GET on /example/D/1.0/D-1.0.pom - OK
      Server2: GET on /example/D/1.0/D-1.0.jar - OK
      Server2: HEAD on /example/C/1.0/C-1.0-sources.jar - return error 404
      Server2: HEAD on /example/C/1.0/C-1.0-javadoc.jar - return error 404
      Server2: HEAD on /example/D/1.0/D-1.0-sources.jar - return error 404
    """.trimIndent().split("\n"))

    // Verify metrics sent on Sync
    val syncSetupStartedEvent = tracker.usages.single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED }.studioEvent.gradleSyncStats
    val syncEndedEvent = tracker.usages.single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED }.studioEvent.gradleSyncStats
    // In case of any failures with metrics check below consult with the content printed from here.
    println("==GRADLE_SYNC_SETUP_STARTED content:")
    println(syncSetupStartedEvent.toString())
    println("==GRADLE_SYNC_ENDED content:")
    println(syncEndedEvent.toString())
    // There are requests to 3 repos, Server1 & Server2 defined below and 'repo.gradle.org'
    Truth.assertThat(syncSetupStartedEvent.downloadsData.repositoriesList).hasSize(3)
    Truth.assertThat(syncSetupStartedEvent.downloadsData.repositoriesList.filter {
      // Looking for content expected for Server2
      it.successRequestsCount == 4 &&
      it.missedRequestsCount == 3 &&
      it.failedRequestsCount == 0
    }).hasSize(1)
    Truth.assertThat(syncSetupStartedEvent.downloadsData.repositoriesList.filter {
      // Looking for content expected for Server1
      it.successRequestsCount == 0 &&
      it.missedRequestsCount == 4 &&
      it.failedRequestsCount == 0
    }).hasSize(1)
    Truth.assertThat(syncSetupStartedEvent.downloadsData).isEqualTo(syncEndedEvent.downloadsData)

    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    // Check SyncView content
    // We should see only one event sent to the view.
    Truth.assertThat(syncDownloadInfoExecutionConsoles).hasSize(1)
    val shownItems = syncDownloadInfoExecutionConsoles.single().uiModel.repositoriesTableModel.summaryItem.requests

    // Dump all content to the test output.
    println(shownItems.joinToString(separator = "\n", prefix = "==All presented requests on Sync:\n", postfix = "\n===="))

    // Check all requests in the model are in finished state.
    Truth.assertThat(shownItems.filterNot { it.completed }).isEmpty()

    //Note: Head requests are not reported on Tooling API currently. (See https://github.com/gradle/gradle/issues/20851)
    Truth.assertThat(shownItems.filter { it.requestKey.url.contains("/example/") }.map { it.toTestString() })
      .containsExactlyElementsIn("""
        ${server1.url}/example/C/1.0/C-1.0.pom - Failed
        ${server2.url}/example/C/1.0/C-1.0.pom - OK
        ${server2.url}/example/C/1.0/C-1.0.jar - OK
        ${server2.url}/example/D/1.0/D-1.0.pom - OK
        ${server2.url}/example/D/1.0/D-1.0.jar - OK
      """.trimIndent().split("\n"))
  }

  private fun TestContext.runSecondSyncAndVerifyNoStaleDataIsPresent() {
    project.requestSyncAndWait()
    Truth.assertThat(syncDownloadInfoExecutionConsoles).hasSize(2)
    Truth.assertThat(syncDownloadInfoExecutionConsoles[1].uiModel.repositoriesTableModel.summaryItem.requests)
      .containsNoneIn(syncDownloadInfoExecutionConsoles[0].uiModel.repositoriesTableModel.summaryItem.requests)
  }

  private fun TestContext.invokeBuild() {
    val invocationResult = project.buildAndWait(
      eventHandler = { event ->
        if (event is DownloadsInfoPresentableEvent) {
          val downloadsInfoExecutionConsole = event.presentationData.executionConsole as DownloadsInfoExecutionConsole
          buildDownloadInfoExecutionConsoles.add(downloadsInfoExecutionConsole)
        }
      }
    ) {
      it.executeTasks(GradleBuildInvoker.Request.builder(project, projectDir, "myTestTask").build())
    }

    invocationResult.buildError?.let { throw it }
  }

  private fun verifyInformationOnBuildOutput() {
    println(HttpServerWrapper.detectedHttpRequests.joinToString(separator = "\n", prefix = "==All Detected requests to local servers on Build:\n", postfix = "\n===="))
    // Verify interaction with server was as expected.
    // Sometimes requests can change the order so compare without order.
    Truth.assertThat(HttpServerWrapper.detectedHttpRequests).containsExactlyElementsIn("""
      Server1: GET on /example/A/1.0/A-1.0.pom - return error 404
      Server1: HEAD on /example/A/1.0/A-1.0.jar - return error 404
      Server2: GET on /example/A/1.0/A-1.0.pom - OK
      Server1: GET on /example/B/1.0/B-1.0.pom - return error 403
      Server2: GET on /example/B/1.0/B-1.0.pom - OK
      Server2: GET on /example/A/1.0/A-1.0.jar - OK
      Server2: GET on /example/B/1.0/B-1.0.jar - OK
    """.trimIndent().split("\n"))
    Truth.assertThat(buildDownloadInfoExecutionConsoles).hasSize(1)
    val shownItems = buildDownloadInfoExecutionConsoles.single().uiModel.repositoriesTableModel.summaryItem.requests
    println(shownItems.joinToString(separator = "\n"))
    Truth.assertThat(shownItems.filterNot { it.completed }).isEmpty()
    Truth.assertThat(shownItems.map { it.toTestString() }).containsExactlyElementsIn("""
      ${server1.url}/example/A/1.0/A-1.0.pom - Failed
      ${server2.url}/example/A/1.0/A-1.0.pom - OK
      ${server1.url}/example/B/1.0/B-1.0.pom - Failed
      ${server2.url}/example/B/1.0/B-1.0.pom - OK
      ${server2.url}/example/A/1.0/A-1.0.jar - OK
      ${server2.url}/example/B/1.0/B-1.0.jar - OK
    """.trimIndent().split("\n"))
  }

  private fun DownloadRequestItem.toTestString(): String = "${requestKey.url} - ${if (failed) "Failed" else "OK"}"

  fun addBuildFileContent(preparedProject: PreparedTestProject) {
    FileUtils.join(preparedProject.root, "app", SdkConstants.FN_BUILD_GRADLE).let { file ->
      val newContent = file.readText()
        .plus("\n\n")
        .plus("""
          repositories {
              maven {
                  url "${server1.url}"
                  allowInsecureProtocol = true
                  metadataSources() {
                      mavenPom()
                      artifact()
                  }
              }
              maven {
                  url "${server2.url}"
                  allowInsecureProtocol = true
                  metadataSources() {
                      mavenPom()
                      artifact()
                  }
              }
          }
          configurations {
              myExtraDependencies
          }
          dependencies {
              //Should be requested during sync
              api 'example:C:1.0'
              //Should be requested during build
              myExtraDependencies 'example:A:1.0'
          }
          tasks.register('myTestTask') {
              dependsOn configurations.myExtraDependencies
              doLast {
                  println "classpath = ${'$'}{configurations.myExtraDependencies.collect { File file -> file.name }}"
              }
          }
        """.trimIndent()
        )
      FileUtil.writeToFile(file, newContent)
    }
  }

  fun addBuildSrcFileContent(preparedProject: PreparedTestProject) {
    FileUtils.join(preparedProject.root, "buildSrc", SdkConstants.FN_BUILD_GRADLE).let { file ->
      val newContent = file.readText()
        .plus("\n\n")
        .plus("""
          repositories {
              maven {
                  url "${server2.url}"
                  allowInsecureProtocol = true
                  metadataSources() {
                      mavenPom()
                      artifact()
                  }
              }
          }
          dependencies {
              //Should be requested during sync
              api 'example:D:1.0'
          }
        """.trimIndent()
        )
      FileUtil.writeToFile(file, newContent)
    }
  }

  fun setupServerFiles() {
    //Add files to server2. Server1 will return errors (404 for everything and 403 for one for a change).
    val emptyJarBytes = Base64.getDecoder().decode("UEsFBgAAAAAAAAAAAAAAAAAAAAAAAA==")
    val aPomBytes = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
          xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId>
        <artifactId>A</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>example</groupId>
            <artifactId>B</artifactId>
            <version>1.0</version>
            <scope>compile</scope>
          </dependency>
        </dependencies>
      </project>
    """.trimIndent().encodeToByteArray()
    val bPomBytes = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
          xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId>
        <artifactId>B</artifactId>
        <version>1.0</version>
        <dependencies>
        </dependencies>
      </project>
    """.trimIndent().encodeToByteArray()
    val cPomBytes = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
          xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId>
        <artifactId>C</artifactId>
        <version>1.0</version>
        <dependencies>
        </dependencies>
      </project>
    """.trimIndent().encodeToByteArray()
    val dPomBytes = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
          xmlns="http://maven.apache.org/POM/4.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <modelVersion>4.0.0</modelVersion>
        <groupId>example</groupId>
        <artifactId>D</artifactId>
        <version>1.0</version>
        <dependencies>
        </dependencies>
      </project>
    """.trimIndent().encodeToByteArray()
    server2.createFileContext(FileRequest(
      path = "/example/A/1.0/A-1.0.jar",
      mime = "application/java-archive",
      bytes = emptyJarBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/A/1.0/A-1.0.pom",
      mime = "application/xml",
      bytes = aPomBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/B/1.0/B-1.0.jar",
      mime = "application/java-archive",
      bytes = emptyJarBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/B/1.0/B-1.0.pom",
      mime = "application/xml",
      bytes = bPomBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/C/1.0/C-1.0.jar",
      mime = "application/java-archive",
      bytes = emptyJarBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/C/1.0/C-1.0.pom",
      mime = "application/xml",
      bytes = cPomBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/D/1.0/D-1.0.jar",
      mime = "application/java-archive",
      bytes = emptyJarBytes
    ))
    server2.createFileContext(FileRequest(
      path = "/example/D/1.0/D-1.0.pom",
      mime = "application/xml",
      bytes = dPomBytes
    ))
    server1.createErrorContext("/example/B/1.0/", 403, "Forbidden")
  }
}

data class TestingRepositoryResult(
  val repository: DownloadsAnalyzer.Repository,
  val successRequestsCount: Int,
  val failedRequestsCount: Int,
  val missedRequestsCount: Int,
) {
  constructor(realResult: DownloadsAnalyzer.RepositoryResult) : this(
    realResult.repository,
    realResult.successRequestsCount,
    realResult.failedRequestsCount,
    realResult.missedRequestsCount
  )
}

class FileRequest(
  val path: String,
  val mime: String,
  val bytes: ByteArray
)

class HttpServerWrapper(
  val name: String,
  val parentDisposable: Disposable
) : Disposable {
  private val LOCALHOST = "127.0.0.1"

  val server: HttpServer = HttpServer.create()

  init {
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
    }
    // Make servers just fail on any not added explicitly file.
    createErrorContext("/", 404, "File not found")
    Disposer.register(parentDisposable, this)
  }

  val authority: String get() = "$LOCALHOST:${server.address.port}"
  val url: String get() = "http://$authority"

  companion object {
    val detectedHttpRequests = mutableListOf<String>()
  }

  fun createFileContext(descriptor: FileRequest) = server.createContext(descriptor.path) { he: HttpExchange ->
    he.responseHeaders["Content-Type"] = descriptor.mime
    when (he.requestMethod) {
      "HEAD" -> {
        recordRequest(he, "OK")
        he.sendResponseHeaders(200, -1)
      }
      "GET" -> {
        recordRequest(he, "OK")
        he.sendResponseHeaders(200, descriptor.bytes.size.toLong())
        he.responseBody.use { it.write(descriptor.bytes) }
      }
      else -> sendError(he, 501, "Unsupported HTTP method")
    }
  }

  fun createErrorContext(path: String, errorCode: Int, message: String) = server.createContext(path) { he: HttpExchange ->
    sendError(he, errorCode, message)
  }

  private fun sendError(he: HttpExchange, rCode: Int, description: String) {
    recordRequest(he, "return error $rCode")
    he.responseHeaders["Content-Type"] = "text/plain; charset=utf-8"
    when (he.requestMethod) {
      "HEAD" -> {
        he.sendResponseHeaders(rCode, -1)
      }
      "GET" -> {
        val message = "HTTP error $rCode: $description"
        val messageBytes = message.toByteArray(charset("UTF-8"))
        he.sendResponseHeaders(rCode, messageBytes.size.toLong())
        he.responseBody.use { it.write(messageBytes) }
      }
    }
  }

  private fun recordRequest(he: HttpExchange, suffix: String) {
    detectedHttpRequests.add("$name: ${he.requestMethod} on ${he.requestURI} - $suffix")
  }

  override fun dispose() {
    println("Disposing server '$name'")
    server.stop(0)
    println("'$name' stopped")
  }
}
