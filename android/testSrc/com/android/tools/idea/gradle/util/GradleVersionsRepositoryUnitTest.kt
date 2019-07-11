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
package com.android.tools.idea.gradle.util

import com.google.common.truth.Truth
import org.intellij.lang.annotations.Language
import org.junit.Test

class GradleVersionsRepositoryUnitTest {

  @Test
  fun testParse() {
    @Language("JSON")
    val response = """
[ {
  "version" : "5.5-20190702010046+0000",
  "buildTime" : "20190702010046+0000",
  "current" : false,
  "snapshot" : true,
  "nightly" : false,
  "releaseNightly" : true,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-5.5-20190702010046+0000-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-5.5-20190702010046+0000-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-5.5-20190702010046+0000-wrapper.jar.sha256"
}, {
  "version" : "5.6-20190702000118+0000",
  "buildTime" : "20190702000118+0000",
  "current" : false,
  "snapshot" : true,
  "nightly" : true,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-5.6-20190702000118+0000-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-5.6-20190702000118+0000-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-5.6-20190702000118+0000-wrapper.jar.sha256"
}, {
  "version" : "5.5",
  "buildTime" : "20190628173605+0000",
  "current" : true,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.5-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-wrapper.jar.sha256"
}, {
  "version" : "5.5-rc-4",
  "buildTime" : "20190624152432+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.5",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-4-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-4-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-4-wrapper.jar.sha256"
}, {
  "version" : "5.5-rc-3",
  "buildTime" : "20190614231538+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.5",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-3-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-3-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-3-wrapper.jar.sha256"
}, {
  "version" : "5.5-rc-2",
  "buildTime" : "20190607090657+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.5",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-2-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-2-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-2-wrapper.jar.sha256"
}, {
  "version" : "5.5-rc-1",
  "buildTime" : "20190529115119+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.5",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-1-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.5-rc-1-wrapper.jar.sha256"
}, {
  "version" : "5.4.1",
  "buildTime" : "20190426081442+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.4.1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.4.1-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.4.1-wrapper.jar.sha256"
}, {
  "version" : "5.4",
  "buildTime" : "20190416024416+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.4-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.4-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.4-wrapper.jar.sha256"
}, {
  "version" : "5.4-rc-1",
  "buildTime" : "20190410011532+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.4",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.4-rc-1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.4-rc-1-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.4-rc-1-wrapper.jar.sha256"
}, {
  "version" : "5.3.1",
  "buildTime" : "20190328090923+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.3.1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.3.1-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.3.1-wrapper.jar.sha256"
}, {
  "version" : "5.3",
  "buildTime" : "20190320110329+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.3-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-wrapper.jar.sha256"
}, {
  "version" : "5.3-rc-3",
  "buildTime" : "20190313202708+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.3",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-3-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-3-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-3-wrapper.jar.sha256"
}, {
  "version" : "5.3-rc-2",
  "buildTime" : "20190311210726+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.3",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-2-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-2-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-2-wrapper.jar.sha256"
}, {
  "version" : "5.3-rc-1",
  "buildTime" : "20190305205202+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "5.3",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-1-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.3-rc-1-wrapper.jar.sha256"
}, {
  "version" : "5.2.1",
  "buildTime" : "20190208190010+0000",
  "current" : false,
  "snapshot" : false,
  "nightly" : false,
  "releaseNightly" : false,
  "activeRc" : false,
  "rcFor" : "",
  "milestoneFor" : "",
  "broken" : false,
  "downloadUrl" : "https://services.gradle.org/distributions/gradle-5.2.1-bin.zip",
  "checksumUrl" : "https://services.gradle.org/distributions/gradle-5.2.1-bin.zip.sha256",
  "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-5.2.1-wrapper.jar.sha256"
}]"""

    val versions = parseGradleVersionsResponse(response.byteInputStream())
    Truth.assertThat(versions).containsExactly(
      "5.5",
      "5.5-rc-4",
      "5.5-rc-3",
      "5.5-rc-2",
      "5.5-rc-1",
      "5.4.1",
      "5.4",
      "5.4-rc-1",
      "5.3.1",
      "5.3",
      "5.3-rc-3",
      "5.3-rc-2",
      "5.3-rc-1",
      "5.2.1").inOrder()
  }
}