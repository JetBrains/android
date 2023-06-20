/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.GradleFiles
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.groovy.GroovyLanguage

/**
 * Recognizes Groovy elements in files that [GradleFiles] considers to be build scripts.
 */
class GradleUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement): UsageType? {
    if (element?.language != GroovyLanguage && element?.language != KotlinLanguage.INSTANCE) return null
    return if (GradleFiles.getInstance(element.project).isGradleFile(element.containingFile)) GRADLE_USAGE_TYPE else null
  }

  companion object {
    private val GRADLE_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("android.usageType.gradle.build.script"))
  }
}

/**
 * Recognizes gradle.properties
 */
class AndroidPropertiesUsageType : UsageTypeProvider {
  companion object {
    private val ANDROID_PROPERTIES_FILE = UsageType(AndroidBundle.messagePointer("android.usageType.gradle.properties.file"))
  }

  override fun getUsageType(element: PsiElement): UsageType? {
    return if (element is PsiFile && element.name == SdkConstants.FN_GRADLE_PROPERTIES) ANDROID_PROPERTIES_FILE else null
  }
}