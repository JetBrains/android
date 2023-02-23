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
  IgnoreListEntry { it.leaktrace.size == 1 },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.util.ObjectNode", "myChildren") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.util.ObjectTree", "myObject2ParentNode") },
  IgnoreListEntry { info -> info.leaktrace.elements.any { it.type.contains("com.intellij.testGuiFramework") } },
  IgnoreListEntry { it.leaktrace.referenceMatches(1, "com.android.layoutlib.bridge.impl.DelegateManager", "sJavaReferences") },
  IgnoreListEntry { info -> info.leaktrace.elements.any { it.type.contains("org.fest.swing") } },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.util.ref.DebugReflectionUtil", "allFields") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "java.util.concurrent.ForkJoinPool", "workQueues") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-3, "java.io.DeleteOnExitHook", "files") },

  // don't report growing weak or soft maps. Nodes whose weak or soft referents have been GC'd will be removed from the map during some
  // future map operation.
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.containers.ConcurrentWeakHashMap", "myMap") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.containers.ConcurrentWeakValueHashMap", "myMap") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.containers.ConcurrentWeakKeyWeakValueHashMap", "myMap") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.util.containers.WeakHashMap", "myMap") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.containers.ConcurrentSoftHashMap", "myMap") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.containers.ConcurrentSoftValueHashMap", "myMap") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.containers.ConcurrentSoftKeySoftValueHashMap", "myMap") },

  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl", "myDocumentCache") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.android.tools.idea.configurations.ConfigurationManager", "myCache") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-3, "com.maddyhome.idea.copyright.util.NewFileTracker", "newFiles") }, // b/126417715
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.vfs.newvfs.impl.VfsData\$Segment", "myObjectArray") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.vcs.impl.FileStatusManagerImpl", "myCachedStatuses") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-3, "com.intellij.util.indexing.VfsAwareMapIndexStorage", "myCache") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.util.indexing.IndexingStamp", "myTimestampsCache") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.util.indexing.IndexingStamp", "ourFinishedFiles") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.fileEditor.impl.EditorWindow", "myRemovedTabs") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.notification.EventLog\$ProjectTracker", "myInitial") },
  IgnoreListEntry { it.leaktrace.referenceMatches(1, "sun.java2d.Disposer", "records") },
  IgnoreListEntry { it.leaktrace.referenceMatches(1, "sun.java2d.marlin.OffHeapArray", "REF_LIST") },
  IgnoreListEntry { it.leaktrace.referenceMatches(1, "sun.awt.X11.XInputMethod", "lastXICFocussedComponent") }, // b/126447315
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "sun.font.XRGlyphCache", "cacheMap") },

  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.openapi.application.impl.ReadMostlyRWLock", "readers") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-3, "org.jdom.JDOMInterner", "myElements") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-3, "org.jdom.JDOMInterner", "myStrings") },
  // coroutine scheduler thread pool: b/140457368
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "kotlinx.coroutines.scheduling.CoroutineScheduler", "workers") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.ide.plugins.MainRunner$1", "threads") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.openapi.command.impl.UndoRedoStacksHolder", "myGlobalStack") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-3, "com.intellij.openapi.command.impl.UndoRedoStacksHolder", "myDocumentStacks") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl", "myBackPlaces") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.openapi.editor.impl.RangeMarkerTree\$RMNode", "intervals") },
  IgnoreListEntry { it.leaktrace.referenceMatches(1, "com.android.tools.idea.io.netty.buffer.ByteBufAllocator", "DEFAULT") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer", "incomingChanges") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectSerializersImpl", "internalSourceToExternal") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.util.concurrency.AppDelayQueue", "q") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-2, "com.intellij.execution.process.ProcessIOExecutorService", "workers") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-4, "com.intellij.util.containers.RecentStringInterner", "myInterns") },
  IgnoreListEntry { it.leaktrace.referenceMatches(-1, "com.intellij.util.xml.EvaluatedXmlNameImpl", "ourInterned") },
  IgnoreListEntry {
    // b/151316853; upstream bug: IDEA-234673
    it.leaktrace.referenceMatches(-1, "com.intellij.ide.util.treeView.AbstractTreeUi", "myElementToNodeMap")
  }
))

private val customExpanders = Supplier { listOf(SmartListExpander(), SmartFMapExpander()) }

object UiTestBleakOptions {
  // a fresh copy of the default options are provided with each access, to facilitate local modifications (e.g. test-specific ignore lists)
  val defaults: BleakOptions
    get() = BleakOptions().withCheck(MainBleakCheck(globalIgnoreList, customExpanders, listOf(TObjectHash.REMOVED)))
                          .withCheck(DisposerCheck())


  fun defaultsWithAdditionalIgnoreList(additionalIgnoreList: IgnoreList<LeakInfo>): BleakOptions {
    return BleakOptions().withCheck(
      MainBleakCheck(globalIgnoreList + additionalIgnoreList, customExpanders, listOf(TObjectHash.REMOVED))).withCheck(
      DisposerCheck())
  }
}
