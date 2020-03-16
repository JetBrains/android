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
package com.android.tools.idea.tests.gui.framework

import com.android.tools.idea.bleak.BleakOptions
import com.android.tools.idea.bleak.DisposerCheck
import com.android.tools.idea.bleak.LeakInfo
import com.android.tools.idea.bleak.MainBleakCheck
import com.android.tools.idea.bleak.Whitelist
import com.android.tools.idea.bleak.WhitelistEntry
import com.android.tools.idea.bleak.expander.SmartFMapExpander
import com.android.tools.idea.bleak.expander.SmartListExpander
import gnu.trove.TObjectHash
import java.util.function.Supplier

private val globalWhitelist = Whitelist<LeakInfo>(listOf(
  WhitelistEntry { it.leaktrace.size == 2 },
  WhitelistEntry { info -> info.leaktrace.elements.any { it.type.contains("com.intellij.testGuiFramework") } },
  WhitelistEntry { it.leaktrace.signatureAt(2) == "com.android.layoutlib.bridge.impl.DelegateManager#sJavaReferences" },
  WhitelistEntry { info -> info.leaktrace.elements.any { it.type.contains("org.fest.swing") } },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.ref.DebugReflectionUtil#allFields" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "java.util.concurrent.ForkJoinPool#workQueues" },
  WhitelistEntry { it.leaktrace.signatureAt(-4) == "java.io.DeleteOnExitHook#files" },

  // don't report growing weak or soft maps. Nodes whose weak or soft referents have been GC'd will be removed from the map during some
  // future map operation.
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentWeakHashMap#myMap" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentWeakKeyWeakValueHashMap#myMap" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.containers.WeakHashMap#myMap" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentSoftHashMap#myMap" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentSoftValueHashMap#myMap" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentSoftKeySoftValueHashMap#myMap" },

  WhitelistEntry { it.leaktrace.signatureAt(-4) == "com.android.tools.idea.configurations.ConfigurationManager#myCache" },
  WhitelistEntry { it.leaktrace.signatureAt(-4) == "com.maddyhome.idea.copyright.util.NewFileTracker#newFiles" }, // b/126417715
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.vfs.newvfs.impl.VfsData\$Segment#myObjectArray" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.vcs.impl.FileStatusManagerImpl#myCachedStatuses" },
  WhitelistEntry { it.leaktrace.signatureAt(-4) == "com.intellij.util.indexing.VfsAwareMapIndexStorage#myCache" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.indexing.IndexingStamp#myTimestampsCache" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.indexing.IndexingStamp#ourFinishedFiles" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.fileEditor.impl.EditorWindow#myRemovedTabs" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.notification.EventLog\$ProjectTracker#myInitial" },
  WhitelistEntry { it.leaktrace.signatureAt(2) == "sun.java2d.Disposer#records" },
  WhitelistEntry { it.leaktrace.signatureAt(2) == "sun.java2d.marlin.OffHeapArray#REF_LIST" },
  WhitelistEntry { it.leaktrace.signatureAt(2) == "sun.awt.X11.XInputMethod#lastXICFocussedComponent" }, // b/126447315
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "sun.font.XRGlyphCache#cacheMap" },
  // this accounts for both myObject2NodeMap and myRootObjects
  WhitelistEntry {
    it.leaktrace.element(-2)?.referenceLabel in listOf("_set", "_values") &&
    it.leaktrace.signatureAt(-4) == "com.intellij.openapi.util.Disposer#ourTree"
  },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.application.impl.ReadMostlyRWLock#readers" },
  WhitelistEntry { it.leaktrace.signatureAt(-3) == "org.jdom.JDOMInterner#myElements" },
  WhitelistEntry { it.leaktrace.signatureAt(-4) == "org.jdom.JDOMInterner#myStrings" },
  // coroutine scheduler thread pool: b/140457368
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "kotlinx.coroutines.scheduling.CoroutineScheduler#workers" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.ide.plugins.MainRunner$1#threads" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.openapi.command.impl.UndoRedoStacksHolder#myGlobalStack" },
  WhitelistEntry { it.leaktrace.signatureAt(-4) == "com.intellij.openapi.command.impl.UndoRedoStacksHolder#myDocumentStacks" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl#myBackPlaces" },
  WhitelistEntry { it.leaktrace.signatureAt(-2) == "com.intellij.openapi.editor.impl.RangeMarkerTree\$RMNode#intervals" }
))

/**
 * Known issues must have a corresponding tracking bug and should be removed as soon as they're fixed.
 */
private val knownIssues = Whitelist<LeakInfo>(listOf(
  WhitelistEntry { info ->
    // b/144418512: Compose Preview leaking ModuleClassLoader
    info.leaktrace.size == 1 // Only ROOT
     // 2 new instances of ModuleClassLoader are added
     && info.addedChildren.size == 2
     && info.addedChildren[0].type.name == "org.jetbrains.android.uipreview.ModuleClassLoader"
     && info.addedChildren[1].type.name == "org.jetbrains.android.uipreview.ModuleClassLoader"
  },
  WhitelistEntry {
    // b/151316853; upstream bug: IDEA-234673
    it.leaktrace.signatureAt(-2) == "com.intellij.ide.util.treeView.AbstractTreeUi#myElementToNodeMap"
  }
))

private val customExpanders = Supplier { listOf(SmartListExpander(), SmartFMapExpander()) }

object UiTestBleakOptions {
  // a fresh copy of the default options are provided with each access, to facilitate local modifications (e.g. test-specific whitelists)
  val defaults: BleakOptions
    get() = BleakOptions().withCheck(MainBleakCheck(globalWhitelist, knownIssues, customExpanders, listOf(TObjectHash.REMOVED)))
      .withCheck(DisposerCheck())
}
