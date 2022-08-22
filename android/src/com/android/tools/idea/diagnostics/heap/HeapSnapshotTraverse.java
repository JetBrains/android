/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.processMask;
import static com.android.tools.idea.util.StudioPathManager.isRunningFromSources;
import static com.google.common.math.IntMath.isPowerOfTwo;

import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HeapSnapshotTraverse {

  private static final int MAX_ALLOWED_OBJECT_MAP_SIZE = 1_000_000;
  private static final int INVALID_OBJECT_ID = -1;

  private static final long OBJECT_CREATION_TIMESTAMP_MASK = 0xFF;
  private static final long CURRENT_ITERATION_TIMESTAMP_MASK = 0xFF00;
  private static final long CURRENT_ITERATION_VISITED_MASK = 0x10000;
  private static final long CURRENT_ITERATION_OBJECT_ID_MASK = 0x1FFFFFFFE0000L;

  private static final int CURRENT_ITERATION_OBJECT_ID_OFFSET = 17;
  // 8(creation timestamp mask) + 8(current iteration timestamp mask) + 1(visited mask)
  private static final int CURRENT_ITERATION_TIMESTAMP_OFFSET = 8; // 8(creation timestamp mask)

  private static final String DIAGNOSTICS_HEAP_NATIVE_PATH =
    "tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native";
  private static final String JNI_OBJECT_TAGGER_LIB_NAME = "jni_object_tagger";
  private static final String RESOURCES_NATIVE_PATH = "plugins/android/resources/native";
  private static final Logger LOG = Logger.getInstance(HeapSnapshotTraverse.class);
  static boolean ourAgentWasSuccessfullyLoaded = false;
  private static short ourTraverseSessionId = 0;

  private volatile boolean myShouldAbortTraversal = false;

  static {
    try {
      loadObjectTaggingAgent();
      ourAgentWasSuccessfullyLoaded = true;
    }
    catch (HeapSnapshotTraverseException | IOException e) {
      LOG.warn("Native object tagging library is not available", e);
    }
  }

  @NotNull private final LowMemoryWatcher myWatcher;
  @NotNull private final HeapTraverseChildProcessor myHeapTraverseChildProcessor;
  private final short myTraverseSessionId;
  @NotNull private final HeapSnapshotStatistics myStatistics;
  private int myLastObjectId = 0;

  public HeapSnapshotTraverse(@NotNull final HeapSnapshotStatistics statistics) {
    this(new HeapTraverseChildProcessor(), statistics);
  }

  public HeapSnapshotTraverse(@NotNull final HeapTraverseChildProcessor childProcessor, @NotNull final HeapSnapshotStatistics statistics) {
    myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived);
    myHeapTraverseChildProcessor = childProcessor;
    myTraverseSessionId = getNextTraverseSessionId();
    myStatistics = statistics;
  }

  /**
   * The heap traversal algorithm is the following:
   * <p>
   * In the process of traversal, we associate a number of masks with each object. These masks are stored in {@link HeapTraverseNode} and
   * show which components own the corresponding object(myOwnedByComponentMask), which components retain the object(myRetainedMask) etc.
   * <p>
   * On the first pass along the heap we arrange objects in topological order (in terms of references). This is necessary so that during the
   * subsequent propagation of masks, we can be sure that all objects that refer to the object have already been processed and masks were
   * updated.
   * <p>
   * On the second pass, we directly update the masks and pass them to the referring objects.
   *
   * @param maxDepth   the maximum depth to which we will descend when traversing the object tree.
   * @param startRoots objects from which traversal is started.
   */
  public StatusCode walkObjects(int maxDepth, @NotNull final Collection<?> startRoots) {
    try {
      if (!canTagObjects()) {
        return StatusCode.CANT_TAG_OBJECTS;
      }
      final FieldCache fieldCache = new FieldCache();

      // enumerating heap objects in topological order
      for (Object root : startRoots) {
        if (root == null) continue;
        depthFirstTraverseHeapObjects(root, maxDepth, fieldCache);
      }
      // By this moment all the reachable heap objects are enumerated in topological order and marked as visited.
      // Order id, visited and the iteration identification timestamp are stored in objects tags.
      // We also use this enumeration to kind of "freeze" the state of the heap, and we will ignore all the newly allocated object
      // that were allocated after the enumeration pass.
      final Map<Integer, HeapTraverseNode> objectIdToTraverseNode = new Int2ObjectOpenHashMap<>();

      for (Object root : startRoots) {
        int objectId = getObjectId(root);
        if (objectId <= 0 || objectId > myLastObjectId) {
          return StatusCode.WRONG_ROOT_OBJECT_ID;
        }
        objectIdToTraverseNode.put(objectId, new HeapTraverseNode(root));
      }

      // iterate over objects and update masks
      for (int i = myLastObjectId; i > 0; i--) {
        abortTraversalIfRequested();
        if (objectIdToTraverseNode.size() > MAX_ALLOWED_OBJECT_MAP_SIZE) {
          return StatusCode.OBJECTS_MAP_IS_TOO_BIG;
        }
        HeapTraverseNode node = objectIdToTraverseNode.get(i);

        if (node == null) {
          continue;
        }
        objectIdToTraverseNode.remove(i);

        final Object currentObject = node.getObject();
        if (currentObject == null) {
          continue;
        }

        // Check whether the current object is a root of one of the components
        int componentId = myStatistics.getComponentsSet().getComponentId(currentObject);
        long currentObjectSize = getObjectSize(currentObject);
        myStatistics.addObjectToTotal(currentObjectSize);

        // if it's a root of a component
        if (componentId != HeapSnapshotStatistics.COMPONENT_NOT_FOUND) {
          node.myRetainedMask |= (1 << componentId);
          node.myOwnedByComponentMask = (1 << componentId);
          node.myOwnershipWeight = HeapTraverseNode.RefWeight.DEFAULT;
        }

        // If current object is retained by any components - propagate their stats.
        processMask(node.myRetainedMask, (index) -> myStatistics.addRetainedObjectSizeToComponent(index, currentObjectSize));
        if (node.myOwnedByComponentMask == 0) {
          myStatistics.addNonComponentObject(currentObjectSize);
        }
        else if (isPowerOfTwo(node.myOwnedByComponentMask)) {
          // if only owned by one component
          processMask(node.myOwnedByComponentMask, (index) -> myStatistics.addOwnedObjectSizeToComponent(index, currentObjectSize));
        }
        else {
          // if owned by multiple components -> add to shared
          myStatistics.addObjectSizeToSharedComponent(node.myOwnedByComponentMask, currentObjectSize);
        }

        // propagate to referred objects
        propagateComponentMask(currentObject, node, objectIdToTraverseNode, fieldCache);
      }
    }
    catch (HeapSnapshotTraverseException exception) {
      return exception.getStatusCode();
    }
    finally {
      myWatcher.stop();
    }
    return StatusCode.NO_ERROR;
  }

  private void abortTraversalIfRequested() throws HeapSnapshotTraverseException {
    if (myShouldAbortTraversal) {
      throw new HeapSnapshotTraverseException(StatusCode.LOW_MEMORY);
    }
  }

  private void onLowMemorySignalReceived() {
    myShouldAbortTraversal = true;
  }

  /**
   * Checks that the passed tag was set during the current traverse.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean isTagFromTheCurrentIteration(long tag) {
    return ((tag & CURRENT_ITERATION_TIMESTAMP_MASK) >> CURRENT_ITERATION_TIMESTAMP_OFFSET) == myTraverseSessionId;
  }

  private void checkObjectCreationTimestampAndSetIfNot(@NotNull final Object obj) {
    long tag = getObjectTag(obj);
    int creationTimestamp = (int)(tag & OBJECT_CREATION_TIMESTAMP_MASK);
    if (creationTimestamp == 0) {
      tag &= ~myTraverseSessionId;
      tag |= myTraverseSessionId;
      setObjectTag(obj, tag | myTraverseSessionId);
    }
  }

  private int getObjectId(@NotNull final Object obj) {
    long tag = getObjectTag(obj);

    if (!isTagFromTheCurrentIteration(tag)) {
      return INVALID_OBJECT_ID;
    }
    return (int)(tag >> CURRENT_ITERATION_OBJECT_ID_OFFSET);
  }

  private boolean wasVisited(@NotNull final Object obj) {
    long tag = getObjectTag(obj);
    if (!isTagFromTheCurrentIteration(tag)) {
      return false;
    }
    return (tag & CURRENT_ITERATION_VISITED_MASK) != 0;
  }

  private void setObjectId(@NotNull final Object obj, int newObjectId) {
    long tag = getObjectTag(obj);
    tag &= ~CURRENT_ITERATION_OBJECT_ID_MASK;
    tag |= (long)newObjectId << CURRENT_ITERATION_OBJECT_ID_OFFSET;
    tag &= ~CURRENT_ITERATION_TIMESTAMP_MASK;
    tag |= (long)myTraverseSessionId << CURRENT_ITERATION_TIMESTAMP_OFFSET;
    setObjectTag(obj, tag);
  }

  private void markVisited(@NotNull final Object obj) {
    long tag = getObjectTag(obj);
    tag &= ~CURRENT_ITERATION_VISITED_MASK;
    tag |= CURRENT_ITERATION_VISITED_MASK;
    tag &= ~CURRENT_ITERATION_TIMESTAMP_MASK;
    tag |= (long)myTraverseSessionId << CURRENT_ITERATION_TIMESTAMP_OFFSET;
    setObjectTag(obj, tag);
  }

  private void addToStack(@NotNull final Node node, int maxDepth, @Nullable final Object value, @NotNull final Deque<Node> stack) {
    if (value == null) {
      return;
    }
    if (node.getDepth() + 1 > maxDepth) {
      return;
    }
    if (HeapTraverseUtil.isPrimitive(value.getClass())) {
      return;
    }
    if (wasVisited(value)) {
      return;
    }

    markVisited(value);
    stack.push(new Node(value, node.getDepth() + 1));
  }

  private void addStronglyReferencedChildrenToStack(@NotNull final Node node,
                                                    int maxDepth,
                                                    @NotNull final Deque<Node> stack,
                                                    @NotNull final FieldCache fieldCache) {
    if (node.myDepth >= maxDepth) {
      return;
    }
    myHeapTraverseChildProcessor.processChildObjects(node.getObject(),
                                                     (Object value, HeapTraverseNode.RefWeight weight) -> addToStack(node, maxDepth, value,
                                                                                                                     stack), fieldCache);
  }

  private int getNextObjectId() {
    return ++myLastObjectId;
  }

    /*
    Object tags have the following structure (in right-most bit order):
    8bits - object creation timestamp
    8bits - current timestamp (used for validation of below fields)
    1bit - visited
    32bits - topological order id
   */

  private void depthFirstTraverseHeapObjects(@NotNull final Object root, int maxDepth, @NotNull final FieldCache fieldCache)
    throws HeapSnapshotTraverseException {
    if (wasVisited(root)) {
      return;
    }
    Deque<Node> stack = new ArrayDeque<>(1_000_000);
    Node rootNode = new Node(root, 0);
    markVisited(root);
    stack.push(rootNode);

    // DFS starting from the given root object.
    while (!stack.isEmpty()) {
      Node node = stack.peek();
      Object obj = node.getObject();
      if (obj == null) {
        stack.pop();
        continue;
      }
      // add to the topological order when ascending from the recursive subtree.
      if (node.myReferencesProcessed) {
        if (node.getObject() != null) {
          checkObjectCreationTimestampAndSetIfNot(obj);
          setObjectId(node.getObject(), getNextObjectId());
        }
        stack.pop();
        continue;
      }

      addStronglyReferencedChildrenToStack(node, maxDepth, stack, fieldCache);
      abortTraversalIfRequested();
      node.myReferencesProcessed = true;
    }
  }

  /**
   * Distributing object masks to referring objects.
   * <p>
   * Masks contain information about object ownership and retention.
   * <p>
   * By objects owned by a component CompA we mean objects that are reachable from one of the roots of the CompA and not directly
   * reachable from roots of other components (only through CompA root).
   * <p>
   * By component retained objects we mean objects that are only reachable through one of the component roots. Component retained objects
   * for the component also contains objects owned by other components but all of them will be unreachable from GC roots after removing the
   * component roots, so retained objects can be considered as an "additional weight" of the component.
   * <p>
   * We also added weights to object references in order to separate difference types of references and handle situations of shared
   * ownership. Reference types listed in {@link HeapTraverseNode.RefWeight}.
   *
   * @param parentObj              processing object
   * @param parentNode             contains object-specific information (masks)
   * @param objectIdToTraverseNode mapping from object id to corresponding {@link HeapTraverseNode}
   * @param fieldCache             cache that stores fields declared for the given class.
   */
  private void propagateComponentMask(@NotNull final Object parentObj,
                                      @NotNull final HeapTraverseNode parentNode,
                                      final Map<Integer, HeapTraverseNode> objectIdToTraverseNode,
                                      @NotNull final FieldCache fieldCache) {
    myHeapTraverseChildProcessor.processChildObjects(parentObj, (Object value, HeapTraverseNode.RefWeight ownershipWeight) -> {
      if (value == null) {
        return;
      }
      int objectId = getObjectId(value);
      // don't process non-enumerated objects.
      // This situation may occur if array/list element or field value changed after enumeration traversal. We don't process them
      // because they can break the topological ordering.
      if (objectId == INVALID_OBJECT_ID) {
        return;
      }
      if (parentObj.getClass().isSynthetic()) {
        ownershipWeight = HeapTraverseNode.RefWeight.SYNTHETIC;
      }
      if (parentNode.myOwnedByComponentMask == 0) {
        ownershipWeight = HeapTraverseNode.RefWeight.NON_COMPONENT;
      }

      HeapTraverseNode currentNode = objectIdToTraverseNode.get(objectId);
      if (currentNode == null) {
        currentNode = new HeapTraverseNode(value);

        currentNode.myOwnershipWeight = ownershipWeight;
        currentNode.myOwnedByComponentMask = parentNode.myOwnedByComponentMask;
        currentNode.myRetainedMask = parentNode.myRetainedMask;

        objectIdToTraverseNode.put(objectId, currentNode);
      }

      currentNode.myRetainedMask &= parentNode.myRetainedMask;

      if (ownershipWeight.compareTo(currentNode.myOwnershipWeight) > 0) {
        currentNode.myOwnershipWeight = ownershipWeight;
        currentNode.myOwnedByComponentMask = parentNode.myOwnedByComponentMask;
      }
      else if (ownershipWeight.compareTo(currentNode.myOwnershipWeight) == 0) {
        currentNode.myOwnedByComponentMask |= parentNode.myOwnedByComponentMask;
      }
    }, fieldCache);
  }

  private static @NotNull String getLibName() {
    return System.mapLibraryName(JNI_OBJECT_TAGGER_LIB_NAME);
  }

  private static @NotNull String getPlatformName() {
    if (SystemInfo.isWindows) {
      return "win";
    }
    if (SystemInfo.isMac) {
      return CpuArch.isArm64() ? "mac_arm" : "mac";
    }
    if (SystemInfo.isLinux) {
      return "linux";
    }
    return "";
  }

  private static @NotNull Path getLibLocation() throws HeapSnapshotTraverseException {
    String libName = getLibName();
    Path homePath = Paths.get(PathManager.getHomePath());
    // Installed Studio.
    Path libFile = homePath.resolve(RESOURCES_NATIVE_PATH).resolve(libName);
    if (Files.exists(libFile)) {
      return libFile;
    }

    if (isRunningFromSources()) {
      // Dev environment.
      libFile = StudioPathManager.resolvePathFromSourcesRoot(DIAGNOSTICS_HEAP_NATIVE_PATH).resolve(getPlatformName()).resolve(libName);
      if (Files.exists(libFile)) {
        return libFile;
      }
    }
    throw new HeapSnapshotTraverseException(StatusCode.AGENT_LOAD_FAILED);
  }

  private static short getNextTraverseSessionId() {
    return ++ourTraverseSessionId;
  }

  private static void loadObjectTaggingAgent() throws HeapSnapshotTraverseException, IOException {
    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.substring(0, vmName.indexOf('@'));
    VirtualMachine vm = null;
    try {
      vm = VirtualMachine.attach(pid);
      vm.loadAgentPath(getLibLocation().toString());
    }
    catch (AttachNotSupportedException | AgentInitializationException | AgentLoadException e) {
      throw new HeapSnapshotTraverseException(StatusCode.AGENT_LOAD_FAILED);
    }
    finally {
      if (vm != null) {
        vm.detach();
      }
    }
  }

  private static native long getObjectTag(@NotNull final Object obj);

  private static native void setObjectTag(@NotNull final Object obj, long newTag);

  private static native boolean canTagObjects();

  private static native long getObjectSize(@NotNull final Object obj);

  private static final class Node {
    private final int myDepth;
    @NotNull private final WeakReference<Object> myObjReference;
    private boolean myReferencesProcessed = false;

    private Node(@NotNull final Object obj, int depth) {
      myObjReference = new WeakReference<>(obj);
      myDepth = depth;
    }

    @Nullable
    private Object getObject() {
      return myObjReference.get();
    }

    private int getDepth() {
      return myDepth;
    }
  }
}
