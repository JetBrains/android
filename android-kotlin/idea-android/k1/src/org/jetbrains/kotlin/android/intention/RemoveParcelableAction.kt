/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.intention

import com.android.tools.idea.kotlin.insideBody
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.android.canRemoveParcelable
import org.jetbrains.kotlin.android.isParcelize
import org.jetbrains.kotlin.android.removeParcelableImplementation
import org.jetbrains.kotlin.idea.base.plugin.suppressAndroidPlugin
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtClass


class RemoveParcelableAction :
        SelfTargetingIntention<KtClass>(KtClass::class.java, { AndroidBundle.message("remove.parcelable.intention.text") }),
        HighPriorityAction {
    override fun isApplicableTo(element: KtClass, caretOffset: Int): Boolean =
            !suppressAndroidPlugin() &&
            AndroidFacet.getInstance(element) != null &&
            !element.insideBody(caretOffset) &&
            !element.isParcelize() &&
            element.canRemoveParcelable()

    override fun applyTo(element: KtClass, editor: Editor?) {
        element.removeParcelableImplementation()
    }

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        // Disable intention preview because ParcelableUtil.kt does not support running outside a write action.
        return IntentionPreviewInfo.EMPTY
    }
}