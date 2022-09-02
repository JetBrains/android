package com.android.tools.idea.compose.preview.uibuilder.handler

import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.handlers.ViewHandlerProvider
import com.android.tools.idea.uibuilder.surface.ScreenView

/**
 * [ViewHandlerProvider] for the `ComposeViewAdapter`. It only serves the
 * [ComposeViewAdapterHandler].
 */
class ComposeViewHandlerProvider : ViewHandlerProvider {
  override fun findHandler(viewTag: String): ViewHandler? =
    if (COMPOSE_VIEW_ADAPTER_FQN == viewTag) {
      ComposeViewAdapterHandler()
    } else {
      null
    }
}

/**
 * [ViewGroupHandler] for the `ComposeViewAdapter`. It disables all interactions with the component
 * since Compose elements can not be interacted with.
 */
class ComposeViewAdapterHandler : ViewGroupHandler() {
  override fun acceptsChild(layout: NlComponent, newChild: NlComponent) = false

  override fun createInteraction(
    screenView: ScreenView,
    x: Int,
    y: Int,
    component: NlComponent
  ): Interaction? = SceneInteraction(screenView)
}
