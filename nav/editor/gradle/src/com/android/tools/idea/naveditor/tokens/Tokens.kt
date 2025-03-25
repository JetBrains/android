/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.naveditor.tokens

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.editor.AddDestinationMenuToken
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavDesignSurfaceToken
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.util.addDependenciesWithUiConfirmation
import com.android.tools.idea.util.dependsOn
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicBoolean

class GradleAddDestinationMenuToken : AddDestinationMenuToken<GradleProjectSystem>, GradleToken {
  override fun modifyProject(
    projectSystem: GradleProjectSystem,
    data: AddDestinationMenuToken.Data,
  ) {
    val module = data.surface.model?.module ?: return
    if (module.dependsOn(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT)) {
      return
    }

    module.addDependenciesWithUiConfirmation(
      coordinates =
        listOf(
          GoogleMavenArtifactId.ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT.getCoordinate("+")
        ),
      promptUserBeforeAdding = true,
      requestSync = false,
    )
  }
}

class GradleNavDesignSurfaceToken : NavDesignSurfaceToken<GradleProjectSystem>, GradleToken {
  override fun modifyProject(projectSystem: GradleProjectSystem, model: NlModel): Boolean {
    val didAdd = AtomicBoolean(false)
    val module = model.module
    val coordinates = NavDesignSurface.getDependencies(module).map { it.getCoordinate("+") }
    ApplicationManager.getApplication()
      .invokeAndWait(
        {
          try {
            didAdd.set(
              module
                .addDependenciesWithUiConfirmation(
                  coordinates,
                  promptUserBeforeAdding = true,
                  requestSync = false,
                )
                .isEmpty()
            )
          } catch (t: Throwable) {
            Logger.getInstance(NavDesignSurface::class.java).warn("Failed to add dependencies", t)
            didAdd.set(false)
          }
        },
        ModalityState.nonModal(),
      )
    return didAdd.get()
  }
}
