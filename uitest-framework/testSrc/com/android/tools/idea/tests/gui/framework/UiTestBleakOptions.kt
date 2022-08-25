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
import com.android.tools.idea.bleak.IgnoreList
import com.android.tools.idea.bleak.IgnoreListEntry
import com.android.tools.idea.bleak.expander.SmartFMapExpander
import com.android.tools.idea.bleak.expander.SmartListExpander
import gnu.trove.TObjectHash
import java.util.function.Supplier

private val globalIgnoreList = IgnoreList<LeakInfo>(listOf(
  IgnoreListEntry { it.leaktrace.size == 2 },
  IgnoreListEntry { info -> info.leaktrace.elements.any { it.type.contains("com.intellij.testGuiFramework") } },
  IgnoreListEntry { it.leaktrace.signatureAt(2) == "com.android.layoutlib.bridge.impl.DelegateManager#sJavaReferences" },
  IgnoreListEntry { info -> info.leaktrace.elements.any { it.type.contains("org.fest.swing") } },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.ref.DebugReflectionUtil#allFields" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "java.util.concurrent.ForkJoinPool#workQueues" },
  IgnoreListEntry { it.leaktrace.signatureAt(-4) == "java.io.DeleteOnExitHook#files" },

  // don't report growing weak or soft maps. Nodes whose weak or soft referents have been GC'd will be removed from the map during some
  // future map operation.
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentWeakHashMap#myMap" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentWeakValueHashMap#myMap" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentWeakKeyWeakValueHashMap#myMap" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.containers.WeakHashMap#myMap" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentSoftHashMap#myMap" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentSoftValueHashMap#myMap" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.containers.ConcurrentSoftKeySoftValueHashMap#myMap" },

  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl#myDocumentCache" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.android.tools.idea.configurations.ConfigurationManager#myCache" },
  IgnoreListEntry { it.leaktrace.signatureAt(-4) == "com.maddyhome.idea.copyright.util.NewFileTracker#newFiles" }, // b/126417715
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.vfs.newvfs.impl.VfsData\$Segment#myObjectArray" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.vcs.impl.FileStatusManagerImpl#myCachedStatuses" },
  IgnoreListEntry { it.leaktrace.signatureAt(-4) == "com.intellij.util.indexing.VfsAwareMapIndexStorage#myCache" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.indexing.IndexingStamp#myTimestampsCache" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.indexing.IndexingStamp#ourFinishedFiles" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.fileEditor.impl.EditorWindow#myRemovedTabs" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.notification.EventLog\$ProjectTracker#myInitial" },
  IgnoreListEntry { it.leaktrace.signatureAt(2) == "sun.java2d.Disposer#records" },
  IgnoreListEntry { it.leaktrace.signatureAt(2) == "sun.java2d.marlin.OffHeapArray#REF_LIST" },
  IgnoreListEntry { it.leaktrace.signatureAt(2) == "sun.awt.X11.XInputMethod#lastXICFocussedComponent" }, // b/126447315
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "sun.font.XRGlyphCache#cacheMap" },
  // this accounts for both myObject2NodeMap and myRootObjects
  IgnoreListEntry {
    it.leaktrace.element(-2)?.referenceLabel in listOf("key", "value") &&
    it.leaktrace.signatureAt(-4) == "com.intellij.openapi.util.Disposer#ourTree"
  },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.openapi.application.impl.ReadMostlyRWLock#readers" },
  IgnoreListEntry { it.leaktrace.signatureAt(-4) == "org.jdom.JDOMInterner#myElements" },
  IgnoreListEntry { it.leaktrace.signatureAt(-4) == "org.jdom.JDOMInterner#myStrings" },
  // coroutine scheduler thread pool: b/140457368
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "kotlinx.coroutines.scheduling.CoroutineScheduler#workers" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.ide.plugins.MainRunner$1#threads" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.openapi.command.impl.UndoRedoStacksHolder#myGlobalStack" },
  IgnoreListEntry { it.leaktrace.signatureAt(-4) == "com.intellij.openapi.command.impl.UndoRedoStacksHolder#myDocumentStacks" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl#myBackPlaces" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.openapi.editor.impl.RangeMarkerTree\$RMNode#intervals" },
  IgnoreListEntry { it.leaktrace.signatureAt(2) == "com.android.tools.idea.io.netty.buffer.ByteBufAllocator#DEFAULT" },
  IgnoreListEntry { it.leaktrace.signatureAt(-1) == "com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer#incomingChanges" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectSerializersImpl#internalSourceToExternal" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.util.concurrency.AppDqlayQueue#q" },
  IgnoreListEntry { it.leaktrace.signatureAt(-3) == "com.intellij.execution.process.ProcessIOExecutorService#workers" },
  IgnoreListEntry { it.leaktrace.signatureAt(-5) == "com.intellij.util.containers.RecentStringInterner#myInterns" },
  IgnoreListEntry { it.leaktrace.signatureAt(-2) == "com.intellij.util.xml.EvaluatedXmlNameImpl#ourInterned" },

))

/**
 * Known issues must have a corresponding tracking bug and should be removed as soon as they're fixed.
 */
private val knownIssues = IgnoreList<LeakInfo>(listOf(
  IgnoreListEntry {
    // b/151316853; upstream bug: IDEA-234673
    it.leaktrace.signatureAt(-2) == "com.intellij.ide.util.treeView.AbstractTreeUi#myElementToNodeMap"
  }
))

private val customExpanders = Supplier { listOf(SmartListExpander(), SmartFMapExpander()) }

object UiTestBleakOptions {
  // a fresh copy of the default options are provided with each access, to facilitate local modifications (e.g. test-specific ignore lists)
  val defaults: BleakOptions
    get() = BleakOptions().withCheck(MainBleakCheck(globalIgnoreList, knownIssues, customExpanders, listOf(TObjectHash.REMOVED)))
}
