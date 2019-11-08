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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.HeapGraph.Companion.jniHelper
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.ArrayObjectIdentityExpander
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.ClassLoaderExpander
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.ClassStaticsExpander
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.DefaultObjectExpander
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.ElidingExpander
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.Expander
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.ExpanderChooser
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander.RootExpander
import java.util.function.Supplier

class BleakOptions private constructor(var whitelist: Whitelist,
                                       var knownIssues: Whitelist,
                                       var useIncrementalPropagation: Boolean,
                                       var iterations: Int,
                                       var customExpanderSupplier: () -> List<Expander>) {

  constructor() : this(Whitelist(listOf()), Whitelist(listOf()), false, 3, { ElidingExpander.getExpanders() })


  fun withCustomExpanders(supplier: Supplier<List<Expander>>): BleakOptions {
    customExpanderSupplier = { customExpanderSupplier() + supplier.get() }
    return this
  }

  fun withIncrementalPropagation(): BleakOptions {
    useIncrementalPropagation = true
    return this
  }

  fun whitelist(w: Whitelist): BleakOptions {
    whitelist += w
    return this
  }

  fun whitelist(e: WhitelistEntry): BleakOptions {
    whitelist += e
    return this
  }

  fun withKnownIssues(w: Whitelist): BleakOptions {
    knownIssues += w
    return this
  }

  fun withKnownIssue(e: WhitelistEntry): BleakOptions {
    knownIssues += e
    return this
  }

  fun withIterations(i: Int): BleakOptions {
    iterations = i
    return this
  }

  // get a new ExpanderChooser instance each time, since some Expanders may hold references to Nodes from
  // the graphs (notably the label to node maps in ArrayObjectIdentityExpander). Using a single instance
  // of ArrayObjectIdentityExpander across all iterations would keep all of the graphs in memory at once.
  fun getExpanderChooser() = ExpanderChooser(listOf(
    RootExpander(),
    ArrayObjectIdentityExpander(),
    ClassLoaderExpander(jniHelper),
    ClassStaticsExpander()) +
    customExpanderSupplier() +
    listOf(DefaultObjectExpander()))

}