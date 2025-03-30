/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.layout

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.naveditor.model.effectiveDestination
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isStartDestination
import org.eclipse.elk.alg.layered.options.LayerConstraint
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.ElkNode
import org.eclipse.elk.graph.util.ElkGraphUtil

class ElkLayeredLayoutAlgorithm : NavSceneLayoutAlgorithm {
  override fun layout(destinations: List<SceneComponent>): List<SceneComponent> {
    val graph = ElkGraphUtil.createGraph()
    graph.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, true)
    graph.setProperty(CoreOptions.SPACING_COMPONENT_COMPONENT, 100.0)
    graph.setProperty(CoreOptions.SPACING_NODE_NODE, 100.0)
    graph.setProperty(LayeredMetaDataProvider.SPACING_NODE_NODE_BETWEEN_LAYERS, 100.0)
    // We don't use their line routing, so no need to leave space
    graph.setProperty(CoreOptions.SPACING_EDGE_EDGE, 0.0)
    // If there's an edge between two nodes the edge/node spacing is used instead of the node/node
    // spacing.
    graph.setProperty(CoreOptions.SPACING_EDGE_NODE, 50.0)
    graph.setProperty(CoreOptions.DIRECTION, Direction.RIGHT)

    val componentNodeMap = mutableMapOf<NlComponent, ElkNode>()
    var startDestinationNode: ElkNode? = null
    for (component in destinations.filter { it.nlComponent.isDestination }) {
      val node = ElkGraphUtil.createNode(graph)
      node.setDimensions(component.drawWidth.toDouble(), component.drawHeight.toDouble())
      componentNodeMap[component.nlComponent] = node
      if (component.nlComponent.isStartDestination) {
        startDestinationNode = node
      }
    }
    for (component in destinations.filter { it.nlComponent.isDestination }) {
      for (action in component.nlComponent.flatten().filter { it.isAction }) {
        val source = componentNodeMap[component.nlComponent]
        val destination = componentNodeMap[action.effectiveDestination]
        // Maybe one of them was laid out previously, or we simply have an invalid action.
        if (source != null && destination != null) {
          ElkGraphUtil.createSimpleEdge(source, destination)
          if (destination == startDestinationNode) {
            startDestinationNode = null
          }
        }
      }
    }
    startDestinationNode?.setProperty(
      LayeredMetaDataProvider.LAYERING_LAYER_CONSTRAINT,
      LayerConstraint.FIRST_SEPARATE,
    )

    RecursiveGraphLayoutEngine().layout(graph, BasicProgressMonitor())
    for (component in destinations.filter { it.nlComponent.isDestination }) {
      val node = componentNodeMap[component.nlComponent]!!
      component.setPosition(node.x.toInt(), node.y.toInt())
    }
    return listOf()
  }

  private companion object {
    init {
      LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(LayeredMetaDataProvider())
      System.setProperty("org.eclipse.emf.common.util.ReferenceClearingQueue", "false")
    }
  }
}
