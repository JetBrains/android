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
package com.android.tools.idea.imports

import com.android.ide.common.repository.NetworkCache
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Testing infrastructure.
 */
internal fun performWithoutSync(projectRule: AndroidGradleProjectRule, action: AndroidMavenImportIntentionAction, element: PsiElement) {
  action.perform(projectRule.project, projectRule.fixture.editor, element, false)
}

internal fun performAndWaitForSyncEnd(
  projectRule: AndroidGradleProjectRule,
  invoke: () -> Unit,
) {
  val publishedResult = SettableFuture.create<ProjectSystemSyncManager.SyncResult>()
  val project = projectRule.project
  project.messageBus
    .connect(project)
    .subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : ProjectSystemSyncManager.SyncResultListener {
      override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
        publishedResult.set(result)
      }
    })

  invoke()

  val results = publishedResult.get(10, TimeUnit.SECONDS)
  Truth.assertThat(results).named("Second sync result").isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
}

internal fun assertBuildGradle(projectRule: AndroidGradleProjectRule, check: (String) -> Unit) {
  val buildGradle = projectRule.project.guessProjectDir()!!.findFileByRelativePath("app/build.gradle")
  val buildGradlePsi = PsiManager.getInstance(projectRule.project).findFile(buildGradle!!)
  check(buildGradlePsi!!.text)
}

internal class FakeGMavenIndexRepository(
  val data: String
) : GMavenIndexRepository, NetworkCache("https://example.com/", GMAVEN_INDEX_CACHE_DIR_KEY, null) {
  override fun readUrlData(url: String, timeout: Int): ByteArray? = data.toByteArray()
  override fun error(throwable: Throwable, message: String?) = throw throwable
  override fun readDefaultData(relative: String) = data.byteInputStream()
  override fun fetchIndex(relative: String): InputStream? = findData(relative)
}