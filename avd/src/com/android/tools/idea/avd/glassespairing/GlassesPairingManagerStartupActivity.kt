/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.avd.glassespairing

import com.android.tools.idea.flags.StudioFlags
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerListener
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.android.facet.AndroidFacet

class GlassesPairingManagerStartupActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    val logger = logger<GlassesPairingManagerStartupActivity>()
    logger.debug("GlassesPairingManagerStartupActivity triggered")

    if (!StudioFlags.AI_GLASSES_PAIRING_RECONCILIATION_ENABLED.get()) {
      logger.debug("Glasses pairing reconciliation disabled by flag")
      return
    }

    val stateManager = project.service<GlassesPairingStateManager>()

    if (ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      logger.debug("Android facet already present; initializing GlassesPairingStateManager")
      stateManager.initialize()
      return
    }

    logger.debug("Android facet not found yet; listening for FacetManager events")

    project.messageBus
      .connect(project)
      .subscribe(
        FacetManager.FACETS_TOPIC,
        object : FacetManagerListener {
          override fun facetAdded(facet: Facet<*>) {
            if (facet.typeId == AndroidFacet.ID) {
              logger.debug("Android facet added; initializing GlassesPairingStateManager")
              stateManager.initialize()
            }
          }
        },
      )
  }
}
