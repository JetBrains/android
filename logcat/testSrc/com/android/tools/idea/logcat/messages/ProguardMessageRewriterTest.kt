/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.tools.idea.logcat.messages

import com.android.testutils.TestResources
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val MESSAGES =
  """
      09-10 17:57:52.303  9066  9066 D Foobar  : 	at S0.a.e(SourceFile:30)
      09-10 17:57:52.303  9066  9066 D Foobar  : 	at H.a.k(SourceFile:160)
      09-10 17:57:52.303  9066  9066 D Foobar  : 	at m.n.m(SourceFile:132)
      09-10 17:57:52.303  9066  9066 D Foobar  : 	at Z0.a.d(SourceFile:9)
    """
    .trimIndent()

private val CLEAR_MESSAGES =
  """
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at com.example.myapplication.Foo.foo(Foo.kt:7)
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at com.example.myapplication.MainActivity.onClick(MainActivity.kt:38)
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at com.example.myapplication.MainActivity.Greeting${'$'}lambda${'$'}1${'$'}lambda${'$'}0(MainActivity.kt:43)
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at androidx.compose.foundation.ClickablePointerInputNode${'$'}pointerInput${'$'}3.invoke-k-4lQ0M(ClickablePointerInputNode.java:987)
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at androidx.compose.foundation.ClickablePointerInputNode${'$'}pointerInput${'$'}3.invoke(ClickablePointerInputNode.java:981)
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at androidx.compose.foundation.gestures.TapGestureDetectorKt${'$'}detectTapAndPress${'$'}2${'$'}1.invokeSuspend(TapGestureDetector.kt:255)
    09-10 17:57:52.303  9066  9066 D Foobar  : 	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
    """
    .trimIndent()

/** Tests for [ProguardMessageRewriter] */
@RunWith(JUnit4::class)
class ProguardMessageRewriterTest {
  @Test
  fun rewrite_noMapping() {
    val rewriter = ProguardMessageRewriter()

    val text = rewriter.rewrite(MESSAGES)
    assertThat(text).isEqualTo(MESSAGES)
  }

  @Test
  fun rewrite_withMapping() {
    val rewriter = ProguardMessageRewriter()
    rewriter.loadProguardMap(TestResources.getFile("/proguard/mapping.txt").toPath())

    val text = rewriter.rewrite(MESSAGES)
    assertThat(text).isEqualTo(CLEAR_MESSAGES)
  }
}
