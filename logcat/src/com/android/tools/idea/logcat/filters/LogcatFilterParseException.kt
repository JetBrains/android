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
package com.android.tools.idea.logcat.filters

import com.intellij.psi.PsiErrorElement

/**
 * An exception thrown when converting a [com.intellij.psi.PsiElement] to a [LogcatFilter].
 *
 * TODO(aalbert): Maybe add more context that could allow us to highlight the error
 */
internal class LogcatFilterParseException(psiErrorElement: PsiErrorElement) :
  Exception(psiErrorElement.errorDescription)
