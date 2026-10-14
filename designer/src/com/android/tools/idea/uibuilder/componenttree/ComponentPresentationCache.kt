// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.uibuilder.componenttree

import com.android.tools.idea.common.model.NlComponent

/** XML reads can take a read lock, which the IDE prohibits during paint. */
internal class ComponentPresentationCache<T>(private val read: (NlComponent) -> T) {
  private val values = HashMap<NlComponent, T>()

  fun refresh(components: List<NlComponent>) {
    values.clear()
    components.associateWithTo(values, read)
  }

  operator fun get(component: NlComponent): T? = values[component]
}
