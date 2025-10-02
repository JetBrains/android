/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.RecompositionStateReadResponse
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.RecompositionStateReadResponseBuilder
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.FakeComposeLayoutInspector
import com.google.common.truth.Truth.assertThat
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Parameter.Type
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Response

private const val COMPOSE1_ANCHOR_HASH = COMPOSE1.toInt()
const val STATE_VALUE_INSTANCE1 = 1
const val STATE_VALUE_INSTANCE2 = 1

private const val STACK_TRACE2 =
  """
  at androidx.compose.runtime.CompositionImpl.recordReadOf(Composition.kt:1015)
  at androidx.compose.runtime.Recomposer.readObserverOf${'$'}lambda$87(Recomposer.kt:1519)
  at androidx.compose.runtime.Recomposer.${'$'}r8${'$'}lambda${'$'}RNpGrwMSqIkMvRQQdpqhpRw2OuI(Unknown Source:0)
  at androidx.compose.runtime.Recomposer$${'$'}ExternalSyntheticLambda0.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.snapshots.SnapshotKt.readable(Snapshot.kt:2081)
  at androidx.compose.runtime.SnapshotMutableStateImpl.getValue(SnapshotState.kt:142)
  at androidx.compose.runtime.DynamicValueHolder.readValue(ValueHolders.kt:71)
  at androidx.compose.runtime.CompositionLocalMapKt.read(CompositionLocalMap.kt:88)
  at androidx.compose.runtime.ComposerImpl.consume(Composer.kt:2473)
  at androidx.compose.material3.TextKt.Text--4IGK_g(Text.kt:352)
  at com.example.recompositiontest.MainActivityKt.HighChangeRate${'$'}lambda$3$1(MainActivity.kt:79)
  at com.example.recompositiontest.MainActivityKt.${'$'}r8${'$'}lambda$2pcReAwK6qZS9-dAl7MMmxDijSE(Unknown Source:0)
  at com.example.recompositiontest.MainActivityKt$${'$'}ExternalSyntheticLambda1.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:130)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:51)
  at androidx.compose.material3.ButtonKt${'$'}Button$2$1.invoke(Button.kt:1140)
  at androidx.compose.material3.ButtonKt${'$'}Button$2$1.invoke(Button.kt:139)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:121)
  at androidx.compose.runtime.internal.ComposableLambdaImpl${'$'}invoke$1.invoke(ComposableLambda.kt:122)
  at androidx.compose.runtime.internal.ComposableLambdaImpl${'$'}invoke$1.invoke(ComposableLambda.kt:122)
  at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:198)
  at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2926)
  at androidx.compose.runtime.ComposerImpl.skipToGroupEnd(Composer.kt:3320)
  at androidx.compose.material3.internal.ProvideContentColorTextStyleKt.ProvideContentColorTextStyle-3J-VO9M(ProvideContentColorTextStyle.kt:46)
  at androidx.compose.material3.ButtonKt${'$'}Button$2.invoke(Button.kt:136)
  at androidx.compose.material3.ButtonKt${'$'}Button$2.invoke(Button.kt:135)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:121)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:51)
  at androidx.compose.material3.SurfaceKt${'$'}Surface$2.invoke(Surface.kt:229)
  at androidx.compose.material3.SurfaceKt${'$'}Surface$2.invoke(Surface.kt:209)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:121)
  at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.kt:51)
  at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:370)
  at androidx.compose.material3.SurfaceKt.Surface-o_FOJdg(Surface.kt:206)
  at androidx.compose.material3.ButtonKt.Button(Button.kt:125)
  at com.example.recompositiontest.MainActivityKt.HighChangeRate(MainActivity.kt:78)
  at com.example.recompositiontest.MainActivityKt.HighChangeRate${'$'}lambda$4(Unknown Source:6)
  at com.example.recompositiontest.MainActivityKt.${'$'}r8${'$'}lambda${'$'}iYCFMbms6xafzvskm0wq_4MwERU(Unknown Source:0)
  at com.example.recompositiontest.MainActivityKt$${'$'}ExternalSyntheticLambda3.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:198)
  at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2926)
  at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(Composer.kt:3262)
  at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(Composer.kt:3893)
  at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg${'$'}runtime(Composer.kt:3817)
  at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1076)
  at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1400)
  at androidx.compose.runtime.Recomposer.access${'$'}performRecompose(Recomposer.kt:156)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2.invokeSuspend${'$'}lambda$22(Recomposer.kt:635)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2.${'$'}r8${'$'}lambda${'$'}OqADLCDYmRw1RgNUvn1CR0kX32M(Unknown Source:0)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2$${'$'}ExternalSyntheticLambda0.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.ui.platform.AndroidUiFrameClock${'$'}withFrameNanos$2${'$'}callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
  at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
  at androidx.compose.ui.platform.AndroidUiDispatcher.access${'$'}performFrameDispatch(AndroidUiDispatcher.android.kt:41)
  at androidx.compose.ui.platform.AndroidUiDispatcher${'$'}dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1337)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1348)
  at android.view.Choreographer.doCallbacks(Choreographer.java:952)
  at android.view.Choreographer.doFrame(Choreographer.java:878)
  at android.view.Choreographer${'$'}FrameDisplayEventReceiver.run(Choreographer.java:1322)
  at android.os.Handler.handleCallback(Handler.java:958)
  at android.os.Handler.dispatchMessage(Handler.java:99)
  at android.os.Looper.loopOnce(Looper.java:205)
  at android.os.Looper.loop(Looper.java:294)
  at android.app.ActivityThread.main(ActivityThread.java:8177)
  at java.lang.reflect.Method.invoke(Method.java:-2)
  at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:552)
  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:971)
"""

private const val STACK_TRACE3 =
  """
  at androidx.compose.runtime.CompositionImpl.recordReadOf(Composition.kt:1015)
  at androidx.compose.runtime.Recomposer.readObserverOf${'$'}lambda$87(Recomposer.kt:1519)
  at androidx.compose.runtime.Recomposer.${'$'}r8${'$'}lambda${'$'}RNpGrwMSqIkMvRQQdpqhpRw2OuI(Unknown Source:0)
  at androidx.compose.runtime.Recomposer$${'$'}ExternalSyntheticLambda0.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.snapshots.SnapshotKt.readable(Snapshot.kt:2081)
  at androidx.compose.runtime.snapshots.SnapshotStateListKt.getReadable(SnapshotStateList.kt:215)
  at kotlin.collections.CollectionsKt___CollectionsKt.joinTo(_Collections.kt:3598)
  at kotlin.collections.CollectionsKt___CollectionsKt.joinToString(_Collections.kt:3618)
  at kotlin.collections.CollectionsKt___CollectionsKt.joinToString${'$'}default(_Collections.kt:3617)
  at com.example.recompositiontest.MainActivityKt.Item(MainActivity.kt:60)
  at com.example.recompositiontest.MainActivityKt.Item${'$'}lambda$3(Unknown Source:6)
  at com.example.recompositiontest.MainActivityKt.${'$'}r8${'$'}lambda$-WQ9lcw2L62jBkR0JG7MNrSlbR4(Unknown Source:0)
  at com.example.recompositiontest.MainActivityKt$${'$'}ExternalSyntheticLambda6.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:198)
  at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2926)
  at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(Composer.kt:3262)
  at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(Composer.kt:3893)
  at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg${'$'}runtime(Composer.kt:3817)
  at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1076)
  at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1400)
  at androidx.compose.runtime.Recomposer.access${'$'}performRecompose(Recomposer.kt:156)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2.invokeSuspend${'$'}lambda$22(Recomposer.kt:635)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2.${'$'}r8${'$'}lambda${'$'}OqADLCDYmRw1RgNUvn1CR0kX32M(Unknown Source:0)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2$${'$'}ExternalSyntheticLambda0.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.ui.platform.AndroidUiFrameClock${'$'}withFrameNanos$2${'$'}callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
  at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
  at androidx.compose.ui.platform.AndroidUiDispatcher.access${'$'}performFrameDispatch(AndroidUiDispatcher.android.kt:41)
  at androidx.compose.ui.platform.AndroidUiDispatcher${'$'}dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1337)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1348)
  at android.view.Choreographer.doCallbacks(Choreographer.java:952)
  at android.view.Choreographer.doFrame(Choreographer.java:878)
  at android.view.Choreographer${'$'}FrameDisplayEventReceiver.run(Choreographer.java:1322)
  at android.os.Handler.handleCallback(Handler.java:958)
  at android.os.Handler.dispatchMessage(Handler.java:99)
  at android.os.Looper.loopOnce(Looper.java:205)
  at android.os.Looper.loop(Looper.java:294)
  at android.app.ActivityThread.main(ActivityThread.java:8177)
  at java.lang.reflect.Method.invoke(Method.java:-2)
  at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:552)
  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:971)
"""

private const val STACK_TRACE4 =
  """
  at androidx.compose.runtime.CompositionImpl.recordReadOf(Composition.kt:1015)
  at androidx.compose.runtime.Recomposer.readObserverOf${'$'}lambda$87(Recomposer.kt:1519)
  at androidx.compose.runtime.Recomposer.${'$'}r8${'$'}lambda${'$'}RNpGrwMSqIkMvRQQdpqhpRw2OuI(Unknown Source:0)
  at androidx.compose.runtime.Recomposer$${'$'}ExternalSyntheticLambda0.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.snapshots.SnapshotKt.readable(Snapshot.kt:2081)
  at androidx.compose.runtime.snapshots.SnapshotStateListKt.getReadable(SnapshotStateList.kt:215)
  at kotlin.collections.CollectionsKt___CollectionsKt.joinTo(_Collections.kt:3598)
  at kotlin.collections.CollectionsKt___CollectionsKt.joinToString(_Collections.kt:3618)
  at kotlin.collections.CollectionsKt___CollectionsKt.joinToString${'$'}default(_Collections.kt:3617)
  at com.example.recompositiontest.MainActivityKt.Item(MainActivity.kt:60)
  at com.example.recompositiontest.MainActivityKt.Item${'$'}lambda$3(Unknown Source:6)
  at com.example.recompositiontest.MainActivityKt.${'$'}r8${'$'}lambda$-WQ9lcw2L62jBkR0JG7MNrSlbR4(Unknown Source:0)
  at com.example.recompositiontest.MainActivityKt$${'$'}ExternalSyntheticLambda6.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:198)
  at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2926)
  at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(Composer.kt:3262)
  at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(Composer.kt:3893)
  at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg${'$'}runtime(Composer.kt:3817)
  at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1076)
  at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1400)
  at androidx.compose.runtime.Recomposer.access${'$'}performRecompose(Recomposer.kt:156)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2.invokeSuspend${'$'}lambda$22(Recomposer.kt:635)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2.${'$'}r8${'$'}lambda${'$'}OqADLCDYmRw1RgNUvn1CR0kX32M(Unknown Source:0)
  at androidx.compose.runtime.Recomposer${'$'}runRecomposeAndApplyChanges$2$${'$'}ExternalSyntheticLambda0.invoke(D8$${'$'}SyntheticClass:0)
  at androidx.compose.ui.platform.AndroidUiFrameClock${'$'}withFrameNanos$2${'$'}callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
  at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
  at androidx.compose.ui.platform.AndroidUiDispatcher.access${'$'}performFrameDispatch(AndroidUiDispatcher.android.kt:41)
  at androidx.compose.ui.platform.AndroidUiDispatcher${'$'}dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1337)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1348)
  at android.view.Choreographer.doCallbacks(Choreographer.java:952)
  at android.view.Choreographer.doFrame(Choreographer.java:878)
  at android.view.Choreographer${'$'}FrameDisplayEventReceiver.run(Choreographer.java:1322)
  at android.os.Handler.handleCallback(Handler.java:958)
  at android.os.Handler.dispatchMessage(Handler.java:99)
  at android.os.Looper.loopOnce(Looper.java:205)
  at android.os.Looper.loop(Looper.java:294)
  at android.app.ActivityThread.main(ActivityThread.java:8177)
  at java.lang.reflect.Method.invoke(Method.java:-2)
  at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:552)
  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:971)
"""

class FakeInspectorStateReads(private val composeInspector: FakeComposeLayoutInspector) {
  var lateStateReadsKnown = false

  fun createFakeStateReads() {
    composeInspector.interceptWhen({ it.hasGetRecompositionStateReadCommand() }) { command ->
      val readCommand = command.getRecompositionStateReadCommand
      val anchorHash = readCommand.anchorHash
      val start = readCommand.recompositionNumberStart
      val end = readCommand.recompositionNumberEnd
      val includeExtra = readCommand.includeExtra
      assertThat(anchorHash).isEqualTo(COMPOSE1_ANCHOR_HASH)
      val response = RecompositionStateReadResponseBuilder()
      var added = 0
      for (recomposition in start..end) {
        if (response.addStateReadGroup(recomposition)) {
          added++
        }
      }
      val size = end - start + 1
      if (includeExtra && lateStateReadsKnown && added < size) {
        var recomposition = 102
        while (added < size) {
          response.addStateReadGroup(recomposition)
          recomposition++
          added++
        }
      }
      Response.newBuilder().setGetRecompositionStateReadResponse(response.build()).build()
    }
  }

  private fun RecompositionStateReadResponseBuilder.addStateReadGroup(recomposition: Int): Boolean {
    when (recomposition) {
      2 -> addStateReadGroup2()
      3 -> addStateReadGroup3()
      102 -> addStateReadGroup102()
      103 -> addStateReadGroup103()
      else -> return false
    }
    return true
  }

  private val anchor1Empty = RecompositionStateReadResponse { AnchorHash(COMPOSE1_ANCHOR_HASH) }

  private fun RecompositionStateReadResponseBuilder.addStateReadGroup2() {
    StateReadGroup {
      Recomposition(2)
      StateRead {
        Parameter("value", Type.STRING, "TextStyle") {
          Element("color", Type.STRING, "Unspecified")
          Element("fontSize", Type.DIMENSION_SP, 14f)
          Element("fontWeight", Type.STRING, "W500")
          Element("fontFamily", Type.STRING, "SansSerif")
          Element("letterSpacing", Type.DIMENSION_SP, 0.1f)
          Element("background", Type.STRING, "Unspecified")
          Element("textDirection", Type.STRING, "Unspecified")
          Element("lineHeight", Type.DIMENSION_SP, 20f)
        }
        ValueInstance(STATE_VALUE_INSTANCE1)
        ParseStackTraceLines(STACK_TRACE2.trimStackTrace())
      }
      StateRead {
        Parameter("value", Type.ITERABLE, "List[6]") {
          Element("[0]", Type.STRING, "a")
          Element("[1]", Type.STRING, "b")
          Element("[2]", Type.STRING, "c")
          Element("[3]", Type.STRING, "d")
          Element("[4]", Type.STRING, "e")
          Reference(COMPOSE1_ANCHOR_HASH)
        }
        ValueInstance(STATE_VALUE_INSTANCE2)
        ParseStackTraceLines(STACK_TRACE3.trimStackTrace())
      }
    }
  }

  private fun RecompositionStateReadResponseBuilder.addStateReadGroup3() {
    StateReadGroup {
      Recomposition(3)
      StateRead {
        Parameter("value", Type.ITERABLE, "List[2]") {
          Element("[0]", Type.STRING, "b")
          Element("[1]", Type.STRING, "c")
        }
        Invalidated(true)
        ValueInstance(STATE_VALUE_INSTANCE2)
        ParseStackTraceLines(STACK_TRACE4.trimStackTrace())
      }
    }
  }

  private fun RecompositionStateReadResponseBuilder.addStateReadGroup102() {
    StateReadGroup {
      Recomposition(102)
      StateRead {
        Parameter("value", Type.STRING, "TextStyle") {
          Element("color", Type.STRING, "Unspecified")
          Element("fontSize", Type.DIMENSION_SP, 14f)
          Element("fontWeight", Type.STRING, "W500")
          Element("fontFamily", Type.STRING, "SansSerif")
          Element("letterSpacing", Type.DIMENSION_SP, 0.1f)
          Element("background", Type.STRING, "Unspecified")
          Element("textDirection", Type.STRING, "Unspecified")
          Element("lineHeight", Type.DIMENSION_SP, 20f)
        }
        ValueInstance(STATE_VALUE_INSTANCE1)
        ParseStackTraceLines(STACK_TRACE2.trimStackTrace())
      }
    }
  }

  private fun RecompositionStateReadResponseBuilder.addStateReadGroup103() {
    StateReadGroup {
      Recomposition(103)
      StateRead {
        Parameter("value", Type.ITERABLE, "List[6]") {
          Element("[0]", Type.STRING, "a")
          Element("[1]", Type.STRING, "b")
          Element("[2]", Type.STRING, "c")
          Element("[3]", Type.STRING, "d")
          Element("[4]", Type.STRING, "e")
          Reference(COMPOSE1_ANCHOR_HASH)
        }
        ValueInstance(STATE_VALUE_INSTANCE2)
        ParseStackTraceLines(STACK_TRACE3.trimStackTrace())
      }
    }
  }

  private fun RecompositionStateReadResponseBuilder.addStateReadGroup104() {
    StateReadGroup {
      Recomposition(104)
      StateRead {
        Parameter("value", Type.ITERABLE, "List[2]") {
          Element("[0]", Type.STRING, "b")
          Element("[1]", Type.STRING, "c")
        }
        Invalidated(true)
        ValueInstance(STATE_VALUE_INSTANCE2)
        ParseStackTraceLines(STACK_TRACE4.trimStackTrace())
      }
    }
  }

  private fun String.trimStackTrace(): String = substring(1).trimIndent()
}
