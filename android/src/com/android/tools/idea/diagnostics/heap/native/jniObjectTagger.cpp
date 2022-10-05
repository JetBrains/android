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

#define ACC_STATIC    0x0008

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getClasses(JNIEnv *env, jclass klass) {
  jint nclasses;
  jclass *classes;
  jint class_status;

  jvmti->GetLoadedClasses(&nclasses, &classes);

  std::vector<jclass> initialized_classes;
  for (int i=0; i < nclasses; i++) {
    jvmti->GetClassStatus(classes[i], &class_status);
    if (((class_status & JVMTI_CLASS_STATUS_VERIFIED) == 0) || ((class_status & JVMTI_CLASS_STATUS_PREPARED) == 0) ||
      ((class_status & JVMTI_CLASS_STATUS_INITIALIZED) == 0) || ((class_status & JVMTI_CLASS_STATUS_ERROR) != 0)) {
      continue;
    }
    initialized_classes.push_back(classes[i]);
  }
  jvmti->Deallocate((unsigned char *)classes);

  jclass objectClass = env->FindClass("java/lang/Object");
  jobjectArray arr = env->NewObjectArray(initialized_classes.size(), objectClass, NULL);
  for (size_t i=0; i < initialized_classes.size(); i++) {
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

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getClassStaticFieldsValues
  (JNIEnv *env, jclass klass, jclass classToCheck) {
  jvmtiError err;
  jint fcount;
  jfieldID *fields;
  char *signature_ptr;
  jint modifiers;

  err = jvmti->GetClassFields(classToCheck, &fcount, &fields);
  if (err != JVMTI_ERROR_NONE) {
    printf("Jvmti error while obtaining fields of the class: %d\n", err);
    return NULL;
  }

  std::vector<jfieldID> staticFieldIds;

  for (int i=0; i < fcount; i++) {
    err = jvmti->GetFieldModifiers(classToCheck, fields[i], &modifiers);
    if (err != JVMTI_ERROR_NONE || !(modifiers & ACC_STATIC)) {
      continue;
    }
    err = jvmti->GetFieldName(classToCheck, fields[i], NULL, &signature_ptr, NULL);

    // Here we need to filter out all the non-reference typed fields. There is no need to return primitive type values
    // from this method. Besides for primitive typed fields GetStaticObjectField method will fail.
    // To filter primitive type fields out we check field signatures and process only ones starting with
    // 'L' - reference type objects and '[' - arrays
    // Read more about signatures: https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2-200
    if (err == JVMTI_ERROR_NONE && signature_ptr != NULL && (signature_ptr[0] == 'L' || signature_ptr[0] == '[')) {
      staticFieldIds.push_back(fields[i]);
    }
    jvmti->Deallocate((unsigned char *)signature_ptr);
  }
  jvmti->Deallocate((unsigned char *)fields);

  jobjectArray arr = env->NewObjectArray(staticFieldIds.size(), env->FindClass("java/lang/Object"), NULL);
  for (size_t i = 0; i < staticFieldIds.size(); i++) {
      env->SetObjectArrayElement(arr, i, env->GetStaticObjectField(classToCheck, staticFieldIds[i]));
  }

  return arr;
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
