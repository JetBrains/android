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
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessModuleDir
import com.intellij.testFramework.RuleChain
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val MESSAGE =
  """
    at c1.e.d(r8-map-id-MAP_ID:5)
    at m.j.k(r8-map-id-MAP_ID:139)
    at k1.a.r(r8-map-id-MAP_ID:9)
    at A1.x.n(r8-map-id-MAP_ID:83)
    at A1.f.p(r8-map-id-MAP_ID:114)
  """
    .trimIndent()

private val CLEAR_MESSAGE =
  """
    at com.example.proguardedapp.MainActivityKt.logStackTrace(MainActivity.kt:43) (Show original)
    at com.example.proguardedapp.MainActivityKt.access${'$'}logStackTrace(MainActivity.kt:1)
    at com.example.proguardedapp.MainActivityKt${'$'}App$1$1$1$1.invoke(MainActivity.kt:38)
    at com.example.proguardedapp.MainActivityKt${'$'}App$1$1$1$1.invoke(MainActivity.kt:38)
    at androidx.compose.foundation.ClickableNode${'$'}clickPointerInput$3.invoke-k-4lQ0M(ClickableNode.java:639)
    at androidx.compose.foundation.ClickableNode${'$'}clickPointerInput$3.invoke(ClickableNode.java:633)
    at androidx.compose.foundation.gestures.TapGestureDetectorKt${'$'}detectTapAndPress$2$1.invokeSuspend(TapGestureDetector.kt:255)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
    at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:179)
    at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:168)
    at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:474)
  """
    .trimIndent()

/** Tests for [AutoProguardMessageRewriter] */
@RunWith(JUnit4::class)
class AutoProguardMessageRewriterTest {
  private val projectRule =
    AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        ":app1",
        "release",
        AndroidProjectBuilder(applicationIdFor = { "app1" }),
      ),
      AndroidModuleModelBuilder(
        ":app2",
        "release",
        AndroidProjectBuilder(applicationIdFor = { "app2" }),
      ),
      AndroidModuleModelBuilder(
        ":app3",
        "release",
        AndroidProjectBuilder(applicationIdFor = { "app2" }),
      ),
    )
  @get:Rule val rule = RuleChain(projectRule)

  @Test
  fun rewrite_autoMapping() {
    val rewriter = AutoProguardMessageRewriter(projectRule.project)
    val mapId = "map-id-12345"
    copyMapToModule(findModules("app1").first(), "release", mapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  @Test
  fun rewrite_autoMapping_cachesValue() {
    val rewriter = AutoProguardMessageRewriter(projectRule.project)
    val mapId = "map-id-12345"
    val mappingFile = copyMapToModule(findModules("app1").first(), "release", mapId)
    val message = MESSAGE.withMapId(mapId)
    rewriter.rewrite(message, "app1")
    mappingFile.delete()

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  @Test
  fun rewrite_autoMapping_multipleVariants() {
    val rewriter = AutoProguardMessageRewriter(projectRule.project)
    val incorrectMapId = "incorrect-map-id"
    val correctMapId = "correct-map-id"
    val module = findModules("app1").first()
    copyMapToModule(module, "release1", incorrectMapId)
    copyMapToModule(module, "release2", correctMapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(correctMapId), "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  @Test
  fun rewrite_autoMapping_multipleModules() {
    val rewriter = AutoProguardMessageRewriter(projectRule.project)
    val mapId = "map-id"
    val module = findModules("app2").first()
    copyMapToModule(module, "release", mapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app2")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  @Test
  fun rewrite_autoMapping_multipleModulesOfSameAppId() {
    val rewriter = AutoProguardMessageRewriter(projectRule.project)
    val incorrectMapId = "incorrect-map-id"
    val correctMapId = "correct-map-id"
    val module1 = findModules("app2")[0]
    val module2 = findModules("app2")[1]
    copyMapToModule(module1, "release", incorrectMapId)
    copyMapToModule(module2, "release", correctMapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(correctMapId), "app2")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  private fun findModules(applicationId: String): List<Module> {
    return projectRule.project.getProjectSystem().findModulesWithApplicationId(applicationId).map {
      it.getModuleSystem().getHolderModule()
    }
  }
}

private fun copyMapToModule(module: Module?, variant: String, mapId: String): Path {
  val moduleDir = module?.guessModuleDir()?.toNioPath() ?: fail("Failed to prepare module dir")
  val mappingFile = moduleDir.resolve("build/outputs/mapping/$variant/mapping.txt")
  mappingFile.createParentDirectories()
  val resourceFile = TestResources.getFile("/proguard/mapping-with-id.txt").toPath()
  val text = resourceFile.readText().replace("MAP_ID", mapId)
  mappingFile.writeText(text)
  return mappingFile
}

private fun String.withMapId(mapId: String) = replace("MAP_ID", mapId)
