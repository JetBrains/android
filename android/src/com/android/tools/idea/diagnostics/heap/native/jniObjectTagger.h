#ifndef _Included_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse
#define _Included_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse
#include <jvmti.h>

extern "C" {
JNIEXPORT jlong JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getObjectTag
  (JNIEnv *, jclass, jobject);

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_setObjectTag
  (JNIEnv *, jclass, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_canTagObjects
  (JNIEnv *, jclass);

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getClasses(JNIEnv *, jclass);

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getClassStaticFieldsValues
(JNIEnv *, jclass, jclass);

JNIEXPORT jboolean JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_isClassInitialized
  (JNIEnv *, jclass, jclass);

JNIEXPORT jlong JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getObjectSize(JNIEnv *, jclass, jobject);

// depthFirstSearchStack methods

// This method caches the <a href="https://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jmethodID">MethodId</a> of the
// StackNode constructor for future use. This caching allows to avoid the repeated method resolution and JVM method table requests.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_cacheStackNodeConstructorId
    (JNIEnv *, jclass, jclass stackNodeClass);
// This method returns the top element of DFS native stack and marks the element as processed after returning.
// Marking node as processed means that it was already processed and all the child objects were added to the stack. We always mark the
// element after peeking it for the first time and joining this two methods allows to decrease the number of native calls and decrease
// the overhead.
// For the subsequent calls element is already marked as processed, so marking it again is not necessary, but won't break anything.
JNIEXPORT jobject JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_peekAndMarkProcessedDepthFirstSearchStack
    (JNIEnv *, jclass, jclass stackNodeClass);
// This method pops the top element of DFS native stack.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_popElementFromDepthFirstSearchStack
    (JNIEnv *, jclass);
// return the current size of the DFS native stack.
JNIEXPORT jint JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_getDepthFirstSearchStackSize(JNIEnv *, jclass);
// This method instantiate a new DFS node with passed Object, depth and tag and pushes it to the DFS native stack.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_pushElementToDepthFirstSearchStack
    (JNIEnv *, jclass, jobject obj, jint depth, jlong tag);
// Clears the DFS native stack.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_clearDepthFirstSearchStack(JNIEnv *, jclass);

// depthFirstSearchStack methods end

// objectIdToTraverseNodeMap methods

// This method caches the MethodId(https://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jmethodID) of the
// HeapTraverseNode constructor for future use. This caching allows to avoid the repeated method resolution and JVM method table
// requests.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_cacheHeapSnapshotTraverseNodeConstructorId
    (JNIEnv *, jclass, jclass heapTraverseNodeClass);
// Clears the object id to HeapTraverseNode native map.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_clearObjectIdToTraverseNodeMap
    (JNIEnv *, jclass);
// Return the size of the native id to HeapTraverseNode map.
JNIEXPORT jint JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_getObjectIdToTraverseNodeMapSize
    (JNIEnv *, jclass);
// Removes the element from the native object id to HeapTraverseNode map.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_removeElementFromObjectIdToTraverseNodeMap
    (JNIEnv *, jclass, jint id);
// Adds a new node to the native map initialized with the passed Object, reference weight, masks and tag if the passed id was not yet
// added to the native map. Otherwise, updates the existing element.
JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_putOrUpdateObjectIdToTraverseNodeMap
    (JNIEnv *, jclass, jint id, jobject obj, jint refWeight, jlong ownedByComponentMask, jlong retainedMask, jint retainedMaskForCategories,
    jlong tag, jboolean isMergePoint);
// Return element from the native HeapTraverseNode map.
JNIEXPORT jobject JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_getObjectIdToTraverseNodeMapElement
  (JNIEnv *, jclass, jint id, jclass heapTraverseNodeClass);
// objectIdToTraverseNodeMap methods end
}

#endif
