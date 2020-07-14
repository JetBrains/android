/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

private val LOG = logger<RoomDependencyChecker>()

/**
 * Checks if project uses Room (any module depends on Room)
 */
class RoomDependencyChecker(val project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<RoomDependencyChecker>()
  }

  fun isRoomPresent(): Boolean {
    return CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result(calculateIsRoomPresent(), ProjectRootModificationTracker.getInstance(project))
    }
  }

  private fun calculateIsRoomPresent(): Boolean {
    LOG.debug("Recalculating project dependency on Room")

    return isRoomPresentInScope(GlobalSearchScope.allScope(project))
  }
}

internal fun isRoomPresentInScope(scope: GlobalSearchScope): Boolean {
  val psiFacade = JavaPsiFacade.getInstance(scope.project!!)
  return sequenceOf(RoomAnnotations.ENTITY.newName(), RoomAnnotations.ENTITY.oldName()).any { psiFacade.findClass(it, scope) != null }
}
