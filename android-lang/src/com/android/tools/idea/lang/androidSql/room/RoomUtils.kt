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
package com.android.tools.idea.lang.androidSql.room

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private val PsiClass.definesRoomTable: Boolean
  get() {
    return hasAnnotation(RoomAnnotations.ENTITY.oldName()) ||
           hasAnnotation(RoomAnnotations.ENTITY.newName()) ||
           hasAnnotation(RoomAnnotations.DATABASE_VIEW.oldName()) ||
           hasAnnotation(RoomAnnotations.DATABASE_VIEW.newName())
  }

/**
 * True if element is PsiElementForFakeColumn, @ENTITY/DATABASE_VIEW-annotated class or field inside such class.
 */
val PsiElement.definesRoomSchema: Boolean
  get() = when (this) {
    is PsiElementForFakeColumn -> true
    is PsiClass -> definesRoomTable
    is PsiField, is KtProperty -> {
      val psiClass: PsiClass? = if (this is PsiField) {
        this.containingClass
      }
      else {
        (this as KtProperty).containingClass()?.toLightClass()
      }
      // Doesn't work if there is subclass annotated with `@Entity`, making fields into SQL column definitions.
      psiClass?.definesRoomTable == true
    }
    else -> false
  }
