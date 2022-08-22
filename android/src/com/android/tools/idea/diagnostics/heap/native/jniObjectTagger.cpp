#include "jniObjectTagger.h"

jvmtiEnv *jvmti;

JNIEXPORT jlong JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getObjectTag
  (JNIEnv *env, jclass klass, jobject obj) {
  jlong tag = 0;
  jvmtiError error = jvmti->GetTag(obj, &tag);
  if (error != JVMTI_ERROR_NONE) {
    printf("JVMTI tag getting failed: %d\n", error);
  }
  return tag;
}

JNIEXPORT jlong JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getObjectSize
  (JNIEnv *env, jclass klass, jobject obj) {
  jlong objectSize = 0;
  jvmtiError error = jvmti->GetObjectSize(obj, &objectSize);
  if (error != JVMTI_ERROR_NONE) {
    printf("JVMTI object size obtaining failed: %d\n", error);
  }
  return objectSize;
}

JNIEXPORT jboolean JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_canTagObjects
  (JNIEnv *env, jclass klass) {
  jvmtiCapabilities capa;
  jvmti->GetCapabilities(&capa);
  return capa.can_tag_objects == 1;
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_setObjectTag
  (JNIEnv *env, jclass klass, jobject obj, jlong newTag) {
  jvmtiError error = jvmti->SetTag(obj, newTag);
  if (error != JVMTI_ERROR_NONE) {
    printf("JVMTI tag setting failed: %d\n", error);
  }
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
	vm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_0);
	jvmtiCapabilities capa;
	jvmti->GetCapabilities(&capa);
	capa.can_tag_objects = 1;
	jvmtiError err = jvmti->AddCapabilities(&capa);
	if (err != JVMTI_ERROR_NONE) {
	  printf("Jvmti error setting capabilities: %d\n", err);
	}
	return err;
}
