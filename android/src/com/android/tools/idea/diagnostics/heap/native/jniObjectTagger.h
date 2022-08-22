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

JNIEXPORT jlong JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getObjectSize(JNIEnv *, jclass, jobject);

}

#endif
