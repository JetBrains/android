#include "jniObjectTagger.h"
#include <vector>

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

#define ACC_SYNTHETIC	0x1000

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getClasses(JNIEnv *env, jclass klass) {
  jint nclasses;
  jclass *classes;
  jint class_status;
  jint modifiers_ptr;

  jvmti->GetLoadedClasses(&nclasses, &classes);

  std::vector<jclass> initialized_classes;
  for (int i=0; i < nclasses; i++) {
    jvmti->GetClassStatus(classes[i], &class_status);
    if (((class_status & JVMTI_CLASS_STATUS_VERIFIED) == 0) || ((class_status & JVMTI_CLASS_STATUS_PREPARED) == 0) ||
      ((class_status & JVMTI_CLASS_STATUS_INITIALIZED) == 0) || ((class_status & JVMTI_CLASS_STATUS_ERROR) != 0)) {
      continue;
    }
    jvmti->GetClassModifiers(classes[i], &modifiers_ptr);
    if (modifiers_ptr & ACC_SYNTHETIC) continue;
    initialized_classes.push_back(classes[i]);
  }
  jvmti->Deallocate((unsigned char *)classes);

  jclass objectClass = env->FindClass("java/lang/Object");
  jobjectArray arr = env->NewObjectArray(initialized_classes.size(), objectClass, NULL);
  for (int i=0; i < initialized_classes.size(); i++) {
    env->SetObjectArrayElement(arr, i, initialized_classes[i]);
  }
  return arr;
}

JNIEXPORT jboolean JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_isClassInitialized
  (JNIEnv *env, jclass klass, jclass classToCheck) {
  jint class_status;
  jvmti->GetClassStatus(classToCheck, &class_status);

  return class_status & JVMTI_CLASS_STATUS_INITIALIZED;
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
