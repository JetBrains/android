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
package com.android.tools.idea.whatsnew.assistant.v2.model

/** A condensed representation of WhatsNewBundle, with only the elements our Compose UI will care about */
data class WhatsNewData(
  val label: String? = null,
  val description: String? = null,
  val remoteLink: String? = null,
  val remoteLinkLabel: String? = null,
  val footerText: String? = null,
  val icon: String? = null,
  val cards: List<Card> = emptyList(),
) {
  class Builder {
    private var label: String = ""
    private var description: String = ""
    private var remoteLink: String? = null
    private var remoteLinkLabel: String? = null
    private var footerText: String? = null
    private var icon: String? = null
    private var cards: MutableList<Card> = mutableListOf()

    fun label(label: String) = apply { this.label = label }

    fun description(description: String) = apply { this.description = description }

    fun remoteLink(remoteLink: String) = apply { this.remoteLink = remoteLink }

    fun remoteLinkLabel(remoteLinkLabel: String) = apply { this.remoteLinkLabel = remoteLinkLabel }

    fun footerText(footerText: String) = apply { this.footerText = footerText }

    fun icon(icon: String) = apply { this.icon = icon }

    fun card(card: Card) = apply { this.cards.add(card) }

    fun card(block: Card.Builder.() -> Unit) = apply { this.cards.add(Card.Builder().apply(block).build()) }

    fun cards(cards: List<Card>) = apply { this.cards.addAll(cards) }

    fun build() = WhatsNewData(label, description, remoteLink, remoteLinkLabel, footerText, icon, cards.toList())
  }
}

data class Card(val title: String, val description: String, val image: Image?, val actions: List<Action> = emptyList()) {
  class Builder {
    private var title: String = ""
    private var description: String = ""
    private var image: Image? = null
    private var actions: MutableList<Action> = mutableListOf()

    fun title(title: String) = apply { this.title = title }

    fun description(description: String) = apply { this.description = description }

    fun image(image: Image) = apply { this.image = image }

    fun image(block: Image.Builder.() -> Unit) = apply { this.image = Image.Builder().apply(block).build() }

    fun action(action: Action) = apply { this.actions.add(action) }

    fun action(block: Action.Builder.() -> Unit) = apply { this.actions.add(Action.Builder().apply(block).build()) }

    fun actions(actions: List<Action>) = apply { this.actions.addAll(actions) }

    fun build() = Card(title, description, image, actions.toList())
  }
}

data class Image(val source: String? = null, val description: String? = null) {
  class Builder {
    private var source: String? = null
    private var description: String? = null

    fun source(source: String) = apply { this.source = source }

    fun description(description: String) = apply { this.description = description }

    fun build() = Image(source, description)
  }
}

data class Action(
  val label: String? = null,
  val key: String? = null,
  val actionArgument: String? = null,
  val successMessage: String? = null,
  val highlighted: Boolean = false,
) {
  class Builder {
    private var label: String? = null
    private var key: String? = null
    private var actionArgument: String? = null
    private var successMessage: String? = null
    private var highlighted: Boolean = false

    fun label(label: String) = apply { this.label = label }

    fun key(key: String) = apply { this.key = key }

    fun actionArgument(actionArgument: String) = apply { this.actionArgument = actionArgument }

    fun successMessage(successMessage: String) = apply { this.successMessage = successMessage }

    fun highlighted(highlighted: Boolean) = apply { this.highlighted = highlighted }

    fun build() = Action(label, key, actionArgument, successMessage, highlighted)
  }
}

fun WhatsNewData.getAllImageSources(): List<String> {
  val sources = mutableListOf<String>()
  cards.forEach { card -> card.image?.let { image -> image.source?.let { sources.add(it) } } }
  return sources
}
