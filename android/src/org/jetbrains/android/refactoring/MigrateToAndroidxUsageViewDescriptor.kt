/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageViewBundle

class MigrateToAndroidxUsageViewDescriptor(private val myElements: Array<PsiElement>) : UsageViewDescriptorAdapter() {

  override fun getElements() = myElements

  override fun getProcessedElementsHeader() = "Items to be migrated"

//  override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
//      RefactoringBundle.message("occurences.to.be.migrated", UsageViewBundle.getReferencesString(usagesCount, filesCount))

}

