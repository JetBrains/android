/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.repository.testframework.FakePackage.FakeRemotePackage
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class InstallSdkComponentsOperationTest {
  @Test
  fun testGetRetryMessageEmpty() {
    assertNull(InstallSdkComponentsOperation.getRetryMessage(emptyList()))
  }

  @Test
  fun testGetRetryMessageOnePackage() {
    val pkg = FakeRemotePackage("dummy").apply { setDisplayName("Dummy Package") }
    val message = InstallSdkComponentsOperation.getRetryMessage(listOf(pkg))
    assertEquals("The following SDK component was not installed: Dummy Package", message)
  }

  @Test
  fun testGetRetryMessageTwoPackages() {
    val pkg1 = FakeRemotePackage("dummy1").apply { setDisplayName("Dummy Package 1") }
    val pkg2 = FakeRemotePackage("dummy2").apply { setDisplayName("Dummy Package 2") }
    val message = InstallSdkComponentsOperation.getRetryMessage(listOf(pkg1, pkg2))
    assertEquals("The following SDK components were not installed: Dummy Package 1 and Dummy Package 2", message)
  }

  @Test
  fun testGetRetryMessageThreePackages() {
    val pkg1 = FakeRemotePackage("dummy1").apply { setDisplayName("Dummy Package 1") }
    val pkg2 = FakeRemotePackage("dummy2").apply { setDisplayName("Dummy Package 2") }
    val pkg3 = FakeRemotePackage("dummy3").apply { setDisplayName("Dummy Package 3") }
    val message = InstallSdkComponentsOperation.getRetryMessage(listOf(pkg1, pkg2, pkg3))
    assertEquals("The following SDK components were not installed: Dummy Package 1, Dummy Package 2 and Dummy Package 3", message)
  }

  @Test
  fun testGetRetryMessageFourPackages() {
    val pkg1 = FakeRemotePackage("dummy1").apply { setDisplayName("Dummy Package 1") }
    val pkg2 = FakeRemotePackage("dummy2").apply { setDisplayName("Dummy Package 2") }
    val pkg3 = FakeRemotePackage("dummy3").apply { setDisplayName("Dummy Package 3") }
    val pkg4 = FakeRemotePackage("dummy4").apply { setDisplayName("Dummy Package 4") }
    val message = InstallSdkComponentsOperation.getRetryMessage(listOf(pkg1, pkg2, pkg3, pkg4))
    assertEquals("The following SDK components were not installed: Dummy Package 1, Dummy Package 2 and 2 more", message)
  }

  @Test
  fun testGetRetryMessageEscapesHtml() {
    val pkg = FakeRemotePackage("dummy").apply { setDisplayName("<html>Dangerous & Hostile <script>alert(1)</script></html>") }
    val message = InstallSdkComponentsOperation.getRetryMessage(listOf(pkg))
    assertEquals(
      "The following SDK component was not installed: &lt;html&gt;Dangerous &amp; Hostile &lt;script&gt;alert(1)&lt;/script&gt;&lt;/html&gt;",
      message,
    )
  }
}
