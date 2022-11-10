/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.asdriver.inject;

import com.android.tools.idea.bleak.BleakOptions;
import com.android.tools.idea.bleak.IgnoreList;
import com.android.tools.idea.bleak.IgnoreListEntry;
import com.android.tools.idea.bleak.LeakInfo;
import com.android.tools.idea.bleak.MainBleakCheck;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

public class AsdriverBleakOptions {
  /**
   * Specifies a pattern to ignore should it appear in a leaktrace (shortest path from GC roots to a suspected
   * leaky object). The pattern matches if the reference at position {@code index} is from an object of class
   * {@code className}, through field {@code fieldName}. Negative indices count backwards from the end of the
   * leaktrace.
   */
  private static class IgnoredRef {
    int index;
    String className;
    String fieldName;

    IgnoredRef(int index, String className, String fieldName) {
      this.index = index;
      this.className = className;
      this.fieldName = fieldName;
    }

    private IgnoreListEntry<LeakInfo> toIgnorelistEntry() {
      return (LeakInfo info) -> info.getLeaktrace().referenceMatches(index, className, fieldName);
    }
  }

  private static final IgnoredRef[] ignoredRefs = {
    new IgnoredRef(-2, "com.intellij.openapi.util.ObjectNode", "myChildren"),
    new IgnoredRef(-2, "com.intellij.openapi.util.ObjectTree", "myObject2ParentNode"),
    new IgnoredRef(1, "com.android.layoutlib.bridge.impl.DelegateManager", "sJavaReferences"),
    new IgnoredRef(-2, "com.intellij.util.ref.DebugReflectionUtil", "allFields"),
    new IgnoredRef(-1, "java.util.concurrent.ForkJoinPool", "workQueues"),
    new IgnoredRef(-3, "java.io.DeleteOnExitHook", "files"),

    // don't report growing weak or soft maps. Nodes whose weak or soft referents have been GC'd will be removed from the map during some
    // future map operation.
    new IgnoredRef(-1, "com.intellij.util.containers.ConcurrentWeakHashMap", "myMap"),
    new IgnoredRef(-1, "com.intellij.util.containers.ConcurrentWeakValueHashMap", "myMap"),
    new IgnoredRef(-1, "com.intellij.util.containers.ConcurrentWeakKeyWeakValueHashMap", "myMap"),
    new IgnoredRef(-2, "com.intellij.util.containers.WeakHashMap", "myMap"),
    new IgnoredRef(-1, "com.intellij.util.containers.ConcurrentSoftHashMap", "myMap"),
    new IgnoredRef(-1, "com.intellij.util.containers.ConcurrentSoftValueHashMap", "myMap"),
    new IgnoredRef(-1, "com.intellij.util.containers.ConcurrentSoftKeySoftValueHashMap", "myMap"),

    new IgnoredRef(-2, "com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl", "myDocumentCache"),
    new IgnoredRef(-2, "com.android.tools.idea.configurations.ConfigurationManager", "myCache"),
    new IgnoredRef(-3, "com.maddyhome.idea.copyright.util.NewFileTracker", "newFiles"), // b/126417715
    new IgnoredRef(-2, "com.intellij.openapi.vfs.newvfs.impl.VfsData$Segment", "myObjectArray"),
    new IgnoredRef(-2, "com.intellij.openapi.vcs.impl.FileStatusManagerImpl", "myCachedStatuses"),
    new IgnoredRef(-3, "com.intellij.util.indexing.VfsAwareMapIndexStorage", "myCache"),
    new IgnoredRef(-2, "com.intellij.util.indexing.IndexingStamp", "myTimestampsCache"),
    new IgnoredRef(-2, "com.intellij.util.indexing.IndexingStamp", "ourFinishedFiles"),
    new IgnoredRef(-2, "com.intellij.openapi.fileEditor.impl.EditorWindow", "myRemovedTabs"),
    new IgnoredRef(-2, "com.intellij.notification.EventLog$ProjectTracker", "myInitial"),
    new IgnoredRef(1, "sun.java2d.Disposer", "records"),
    new IgnoredRef(1, "sun.java2d.marlin.OffHeapArray", "REF_LIST"),
    new IgnoredRef(1, "sun.awt.X11.XInputMethod", "lastXICFocussedComponent"), // b/150879705
    new IgnoredRef(-1, "sun.font.XRGlyphCache", "cacheMap"),

    new IgnoredRef(-2, "com.intellij.openapi.application.impl.ReadMostlyRWLock", "readers"),
    new IgnoredRef(-3, "org.jdom.JDOMInterner", "myElements"),
    new IgnoredRef(-3, "org.jdom.JDOMInterner", "myStrings"),
    // coroutine scheduler thread pool: b/140457368
    new IgnoredRef(-1, "kotlinx.coroutines.scheduling.CoroutineScheduler", "workers"),
    new IgnoredRef(-1, "com.intellij.ide.plugins.MainRunner$1", "threads"),
    new IgnoredRef(-1, "com.intellij.openapi.command.impl.UndoRedoStacksHolder", "myGlobalStack"),
    new IgnoredRef(-3, "com.intellij.openapi.command.impl.UndoRedoStacksHolder", "myDocumentStacks"),
    new IgnoredRef(-1, "com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl", "myBackPlaces"),
    new IgnoredRef(-1, "com.intellij.openapi.editor.impl.RangeMarkerTree$RMNode", "intervals"),
    new IgnoredRef(1, "com.android.tools.idea.io.netty.buffer.ByteBufAllocator", "DEFAULT"),
    new IgnoredRef(-1, "com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer", "incomingChanges"),
    new IgnoredRef(-1, "com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectSerializersImpl", "internalSourceToExternal"),
    new IgnoredRef(-2, "com.intellij.util.concurrency.AppDelayQueue", "q"),
    new IgnoredRef(-2, "com.intellij.execution.process.ProcessIOExecutorService", "workers"),
    new IgnoredRef(-4, "com.intellij.util.containers.RecentStringInterner", "myInterns"),
    new IgnoredRef(-1, "com.intellij.util.xml.EvaluatedXmlNameImpl", "ourInterned"),
    // IDEA-234673
    new IgnoredRef(-1, "com.intellij.ide.util.treeView.AbstractTreeUi", "myElementToNodeMap"),

    // as-driver-specific:
    new IgnoredRef(2, "com.android.tools.idea.io.grpc.InternalChannelz", "perServerSockets"),
    new IgnoredRef(-1, "com.android.tools.idea.io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscChunkedArrayQueue", "producerBuffer"),
    new IgnoredRef(-1, "com.android.tools.idea.io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue", "buffer"),
    new IgnoredRef(-1, "com.android.tools.idea.io.grpc.netty.shaded.io.netty.buffer.PoolChunk", "subpages"),
  };

  private static final IgnoreList<LeakInfo> globalIgnoreList = new IgnoreList<>(
    Arrays.stream(ignoredRefs).map((ref) -> ref.toIgnorelistEntry()).toList());

  public static BleakOptions getDefaults() {
    return new BleakOptions().withCheck(
      new MainBleakCheck(globalIgnoreList, () -> new ArrayList<>(), new ArrayList<>(), Duration.ofSeconds(60)));
  }
}