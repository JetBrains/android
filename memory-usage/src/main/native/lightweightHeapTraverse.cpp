#include "lightweightHeapTraverse.h"
#include <iostream>
#include <jvmti.h>
#include <cstring>

jvmtiEnv *jvmti;

struct TraverseResult {
  jint total_objects_number;
  jlong total_objects_size;

  jint total_reachable_objects_number;
  jlong total_reachable_objects_size;

  jint total_strong_referenced_objects_number;
  jlong total_strong_referenced_objects_size;
};

#define VISITED_TAG (-1)
#define IGNORE_CLASS_TAG (-2)

static jint JNICALL traverseCallback(jvmtiHeapReferenceKind reference_kind,
                             const jvmtiHeapReferenceInfo* reference_info,
                             jlong class_tag,
                             jlong referrer_class_tag,
                             jlong size,
                             jlong* tag_ptr,
                             jlong* referrer_tag_ptr,
                             jint length,
                             void* user_data) {
  auto *info = reinterpret_cast<TraverseResult *>(user_data);

  if (reference_kind == JVMTI_HEAP_REFERENCE_JNI_LOCAL || reference_kind == JVMTI_HEAP_REFERENCE_JNI_GLOBAL) {
      return 0;
  } else if (*tag_ptr == 0) {
      *tag_ptr = VISITED_TAG;
  } else if (*tag_ptr == VISITED_TAG) {
      return 0;
  }

  info->total_reachable_objects_size += size;
  info->total_reachable_objects_number++;
  return JVMTI_VISIT_OBJECTS;
}

static jint JNICALL objectsIterationCallback(jlong class_tag,
                                   jlong size,
                                   jlong* tag_ptr,
                                   jint length,
                                   void* user_data) {
  auto *info = reinterpret_cast<TraverseResult *>(user_data);
  info->total_objects_size += size;
  info->total_objects_number++;

  return JVMTI_VISIT_OBJECTS;
}

static jint JNICALL clearTags(jlong class_tag,
                                   jlong size,
                                   jlong* tag_ptr,
                                   jint length,
                                   void* user_data) {
  *tag_ptr = 0;
  return JVMTI_VISIT_OBJECTS;
}

static jint JNICALL traverseStrongReferencesCallback(jvmtiHeapReferenceKind reference_kind,
                                                     const jvmtiHeapReferenceInfo* reference_info,
                                                     jlong class_tag,
                                                     jlong referrer_class_tag,
                                                     jlong size,
                                                     jlong* tag_ptr,
                                                     jlong* referrer_tag_ptr,
                                                     jint length,
                                                     void* user_data) {
  auto *info = reinterpret_cast<TraverseResult *>(user_data);

  if (class_tag == IGNORE_CLASS_TAG) {
    return 0;
  }

  if (reference_kind == JVMTI_HEAP_REFERENCE_JNI_LOCAL || reference_kind == JVMTI_HEAP_REFERENCE_JNI_GLOBAL) {
      return 0;
  } else if (*tag_ptr == 0) {
      *tag_ptr = VISITED_TAG;
  } else if (*tag_ptr == VISITED_TAG) {
      return 0;
  }

  info->total_strong_referenced_objects_size += size;
  info->total_strong_referenced_objects_number++;
  return JVMTI_VISIT_OBJECTS;
}

JNIEXPORT jobject JNICALL Java_com_android_tools_memory_usage_LightweightHeapTraverse_collectReport
  (JNIEnv *env, jclass klass, jboolean collectHeapObjectInfo,
                              jboolean collectReachableObjectsInfo,
                              jboolean collectStrongReferencedObjectsInfo) {
  jvmtiHeapCallbacks cb;
  std::memset(&cb, 0, sizeof(jvmtiHeapCallbacks));

  TraverseResult result;
  result.total_objects_size = 0;
  result.total_objects_number = 0;
  result.total_reachable_objects_size = 0;
  result.total_reachable_objects_number = 0;
  result.total_strong_referenced_objects_size = 0;
  result.total_strong_referenced_objects_number = 0;

  jvmtiError err;

  if (collectHeapObjectInfo == JNI_TRUE) {
    cb.heap_iteration_callback = reinterpret_cast<jvmtiHeapIterationCallback>(&objectsIterationCallback);
    err = jvmti->IterateThroughHeap(0, NULL, &cb, &result);
    if (err != JVMTI_ERROR_NONE) {
      printf("Jvmti error during the iteration over the heap: %d\n", err);
    }
  }

  cb.heap_iteration_callback = reinterpret_cast<jvmtiHeapIterationCallback>(&clearTags);
  if (collectReachableObjectsInfo == JNI_TRUE) {
    cb.heap_reference_callback = reinterpret_cast<jvmtiHeapReferenceCallback>(&traverseCallback);
    err = jvmti->FollowReferences(0, NULL, NULL, &cb, &result);
    if (err != JVMTI_ERROR_NONE) {
      printf("Jvmti error during the iteration over the heap: %d\n", err);
    }
    err = jvmti->IterateThroughHeap(JVMTI_HEAP_FILTER_UNTAGGED, NULL, &cb, &result);
    if (err != JVMTI_ERROR_NONE) {
      printf("Jvmti error during tags cleanup: %d\n", err);
    }
  }

  if (collectStrongReferencedObjectsInfo == JNI_TRUE) {
    jint nclasses;
    jclass *classes;
    jint class_status;
    jclass soft_reference_class = env->FindClass("java/lang/ref/SoftReference");
    jclass weak_reference_class = env->FindClass("java/lang/ref/WeakReference");

    jvmti->GetLoadedClasses(&nclasses, &classes);

    for (int i=0; i < nclasses; i++) {
      jvmti->GetClassStatus(classes[i], &class_status);
      if (((class_status & JVMTI_CLASS_STATUS_VERIFIED) == 0) || ((class_status & JVMTI_CLASS_STATUS_PREPARED) == 0) ||
        ((class_status & JVMTI_CLASS_STATUS_INITIALIZED) == 0) || ((class_status & JVMTI_CLASS_STATUS_ERROR) != 0)) {
        continue;
      }
      if (env->IsAssignableFrom(classes[i], soft_reference_class) == JNI_TRUE ||
          env->IsAssignableFrom(classes[i], weak_reference_class) == JNI_TRUE) {
        jvmti->SetTag(classes[i], IGNORE_CLASS_TAG);
      }
      env->DeleteLocalRef(classes[i]);
    }
    jvmti->Deallocate((unsigned char *)classes);

    cb.heap_reference_callback = reinterpret_cast<jvmtiHeapReferenceCallback>(&traverseStrongReferencesCallback);
    err = jvmti->FollowReferences(0, NULL, NULL, &cb, &result);
    if (err != JVMTI_ERROR_NONE) {
      printf("Jvmti error during the iteration over references: %d\n", err);
    }
    err = jvmti->IterateThroughHeap(JVMTI_HEAP_FILTER_UNTAGGED, NULL, &cb, NULL);
    if (err != JVMTI_ERROR_NONE) {
      printf("Jvmti error during tags cleanup: %d\n", err);
    }
  }
  jclass result_class = env->FindClass("com/android/tools/memory/usage/LightweightTraverseResult");
  jmethodID result_constructor = env->GetMethodID(result_class, "<init>", "(IJIJIJ)V");
  return env->NewObject(result_class, result_constructor,
                        result.total_objects_number,
                        result.total_objects_size,
                        result.total_reachable_objects_number,
                        result.total_reachable_objects_size,
                        result.total_strong_referenced_objects_number,
                        result.total_strong_referenced_objects_size);
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

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
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
