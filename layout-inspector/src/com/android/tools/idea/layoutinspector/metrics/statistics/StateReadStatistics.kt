/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorStateReads

class StateReadStatistics {
  /** The number of times the user selected "Observe All" composable */
  private var observingAllSelected = 0
  /** The number of times the user selected "Start/Stop Observing Node" */
  private var observingNodeByIdSelected = 0
  /** The state read pages shown while observing all */
  private var pagesShownObservingAll = 0
  /** The state read pages shown while observing by id */
  private var pagesShownObservingById = 0
  /** The number of times the user selected next recomposition */
  private var nextRecompositionChosen = 0
  /** The number of times the user selected previous recomposition */
  private var prevRecompositionChosen = 0
  /** The number of times the user clicked on a stack trace link */
  private var stackTraceLinksClicked = 0
  /** The number of times the user clicked on AI link */
  private var aiLinksClicked = 0

  private var observed = Observed.None

  private enum class Observed {
    None,
    All,
    ById,
  }

  fun start() {
    observingAllSelected = 0
    observingNodeByIdSelected = 0
    pagesShownObservingAll = 0
    pagesShownObservingById = 0
    nextRecompositionChosen = 0
    prevRecompositionChosen = 0
    stackTraceLinksClicked = 0
    aiLinksClicked = 0
  }

  fun observingNoneSelected() {
    observed = Observed.None
  }

  fun observingAllSelected() {
    observed = Observed.All
    observingAllSelected++
  }

  fun observingSingleNodeSelected() {
    observed = Observed.ById
    observingNodeByIdSelected++
  }

  fun stateReadsShown() {
    when (observed) {
      Observed.All -> pagesShownObservingAll++
      Observed.ById -> pagesShownObservingById++
      Observed.None -> {}
    }
  }

  fun nextRecompositionChosen() {
    nextRecompositionChosen++
  }

  fun prevRecompositionChosen() {
    prevRecompositionChosen++
  }

  fun gotoSourceFromStackTrace() {
    stackTraceLinksClicked++
  }

  fun explainWithAiClicked() {
    aiLinksClicked++
  }

  fun save(dataSupplier: () -> DynamicLayoutInspectorStateReads.Builder) {
    if (
      observingAllSelected == 0 &&
        observingNodeByIdSelected == 0 &&
        pagesShownObservingAll == 0 &&
        pagesShownObservingById == 0 &&
        nextRecompositionChosen == 0 &&
        prevRecompositionChosen == 0 &&
        stackTraceLinksClicked == 0 &&
        aiLinksClicked == 0
    ) {
      return
    }
    dataSupplier().let {
      it.observingAllSelected = observingAllSelected
      it.observingNodeByIdSelected = observingNodeByIdSelected
      it.pagesShownObservingAll = pagesShownObservingAll
      it.pagesShownObservingById = pagesShownObservingById
      it.nextRecompositionChosen = nextRecompositionChosen
      it.prevRecompositionChosen = prevRecompositionChosen
      it.stackTraceLinksClicked = stackTraceLinksClicked
      it.aiLinksClicked = aiLinksClicked
    }
  }
}
