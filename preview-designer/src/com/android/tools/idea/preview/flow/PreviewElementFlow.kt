/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.flow

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.preview.PreviewElement
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex

/**
 * Flow of events, happening when some data for languages gets changed.
 *
 * We are using it here as a workaround to track [KotlinAnnotationsIndex] changes, otherwise we are
 * failing to collect annotated methods right after a [Project] is created and is in Smart mode but
 * indexes are not yet prepared.
 */
private fun languageModificationFlow(project: Project, languages: Set<Language>) =
  disposableCallbackFlow("${languages.joinToString(", ") { it.displayName }} modification flow") {
    val connection = project.messageBus.connect(this.disposable)
    val modificationTracker =
      PsiModificationTracker.getInstance(project).forLanguages { it in languages }
    connection.subscribe<PsiModificationTracker.Listener>(
      PsiModificationTracker.TOPIC,
      object : PsiModificationTracker.Listener {
        private var lastTimeSeen = modificationTracker.modificationCount

        init {
          // After the flow is connected, emit a forced notification to ensure the listener
          // gets the latest change.
          trySend(lastTimeSeen)
        }

        override fun modificationCountChanged() {
          val now = modificationTracker.modificationCount
          if (lastTimeSeen != now) {
            lastTimeSeen = now
            trySend(now)
          }
        }
      },
    )
  }

/**
 * Creates a new [Flow] returning all the [PreviewElement]s from a given [PreviewElementProvider]
 * any time a file changes. This flow takes into account any changes in any Kotlin files since
 * Multi-Preview can cause previews to be altered in this file.
 */
@OptIn(FlowPreview::class)
fun <T : PreviewElement<SmartPsiElementPointer<PsiElement>>> previewElementsOnFileChangesFlow(
  project: Project,
  previewElementProvider: () -> PreviewElementProvider<T>,
): Flow<FlowableCollection<T>> {
  return channelFlow {
      coroutineScope {
        // We are tracking the language change flow instead of file change flow because
        // filePreviewElementProvider relies not only on files themselves but also on indexes
        // [KotlinAnnotationsIndex] in particular. If we only track file changes we lose the time
        // when the indexes are updated (they are not immediately ready after e.g. a project is
        // created) and we do not receive the required preview elements.
        val languageChangeFlow =
          languageModificationFlow(project, setOf(KotlinLanguage.INSTANCE, JavaLanguage.INSTANCE))
            // debounce to avoid many equality comparisons of the set
            .debounce(250)
        languageChangeFlow.collectLatest {
          val previews = previewElementProvider().previewElements().toSet()
          send(FlowableCollection.Present(previews))
        }
      }
    }
    .distinctUntilChanged()
}
