#include "jniObjectTagger.h"
#include <vector>
#include <stack>
#include <unordered_map>

struct StackNode {
    jint depth;
    jweak obj_ref;
    jboolean references_processed;
    jlong tag;
};

struct ObjectMapNode {
    jweak obj_ref;
    jint ref_weight;
    jlong owned_by_component_mask;
    jlong retained_mask;
    jint retained_mask_for_categories;
    jlong tag;
};

jvmtiEnv *jvmti;

jmethodID stack_node_constructor;
jmethodID heap_snapshot_traverse_node_constructor;
std::stack<StackNode*> depth_first_search_stack;
std::unordered_map<int, ObjectMapNode*> object_id_to_traverse_node_map;

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
  jlong object_size = 0;
  jvmtiError error = jvmti->GetObjectSize(obj, &object_size);
  if (error != JVMTI_ERROR_NONE) {
    printf("JVMTI object size obtaining failed: %d\n", error);
  }
  return object_size;
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

  jclass object_class = env->FindClass("java/lang/Object");
  jobjectArray arr = env->NewObjectArray(initialized_classes.size(), object_class, NULL);
  for (size_t i=0; i < initialized_classes.size(); i++) {
    env->SetObjectArrayElement(arr, i, initialized_classes[i]);
  }
  return arr;
}

JNIEXPORT jboolean JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_isClassInitialized
  (JNIEnv *env, jclass klass, jclass class_to_check) {
  jint class_status;
  jvmti->GetClassStatus(class_to_check, &class_status);

  return class_status & JVMTI_CLASS_STATUS_INITIALIZED;
}

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapSnapshotTraverse_getClassStaticFieldsValues
  (JNIEnv *env, jclass klass, jclass class_to_check) {
  jvmtiError err;
  jint fcount;
  jfieldID *fields;
  char *signature_ptr;
  jint modifiers;

  err = jvmti->GetClassFields(class_to_check, &fcount, &fields);
  if (err != JVMTI_ERROR_NONE) {
    printf("Jvmti error while obtaining fields of the class: %d\n", err);
    return NULL;
  }

  std::vector<jfieldID> static_field_ids;

  for (int i=0; i < fcount; i++) {
    err = jvmti->GetFieldModifiers(class_to_check, fields[i], &modifiers);
    if (err != JVMTI_ERROR_NONE || !(modifiers & ACC_STATIC)) {
      continue;
    }
    err = jvmti->GetFieldName(class_to_check, fields[i], NULL, &signature_ptr, NULL);

    // Here we need to filter out all the non-reference typed fields. There is no need to return primitive type values
    // from this method. Besides for primitive typed fields GetStaticObjectField method will fail.
    // To filter primitive type fields out we check field signatures and process only ones starting with
    // 'L' - reference type objects and '[' - arrays
    // Read more about signatures: https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2-200
    if (err == JVMTI_ERROR_NONE && signature_ptr != NULL && (signature_ptr[0] == 'L' || signature_ptr[0] == '[')) {
      static_field_ids.push_back(fields[i]);
    }
    jvmti->Deallocate((unsigned char *)signature_ptr);
  }
  jvmti->Deallocate((unsigned char *)fields);

  jobjectArray arr = env->NewObjectArray(static_field_ids.size(), env->FindClass("java/lang/Object"), NULL);
  for (size_t i = 0; i < static_field_ids.size(); i++) {
      env->SetObjectArrayElement(arr, i, env->GetStaticObjectField(class_to_check, static_field_ids[i]));
  }

  return arr;
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_cacheStackNodeConstructorId
    (JNIEnv *env, jclass klass, jclass stack_node_class) {
    stack_node_constructor = env->GetMethodID(stack_node_class, "<init>", "(Ljava/lang/Object;IZJ)V");
}

JNIEXPORT jobject JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_peekAndMarkProcessedDepthFirstSearchStack
    (JNIEnv *env, jclass klass, jclass stack_node_class) {
    if (depth_first_search_stack.empty()) {
      return NULL;
    }
    jobject result = env->NewObject(stack_node_class, stack_node_constructor, depth_first_search_stack.top()->obj_ref,
                                                                depth_first_search_stack.top()->depth,
                                                                depth_first_search_stack.top()->references_processed,
                                                                depth_first_search_stack.top()->tag);
    depth_first_search_stack.top()->references_processed = JNI_TRUE;
    return result;
}

void depthFirstSearchStackPop(JNIEnv *env) {
    env->DeleteWeakGlobalRef(depth_first_search_stack.top()->obj_ref);
    delete depth_first_search_stack.top();
    depth_first_search_stack.pop();
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_popElementFromDepthFirstSearchStack
    (JNIEnv *env, jclass klass) {
    if (depth_first_search_stack.empty()) {
      env->ThrowNew(env->FindClass("java/util/NoSuchElementException"), "Attempt to pop element from empty Depth First Search stack");
      return;
    }
    depthFirstSearchStackPop(env);
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_pushElementToDepthFirstSearchStack
    (JNIEnv *env, jclass klass, jobject obj, jint depth, jlong tag) {
    StackNode* node = new StackNode();
    node->depth = depth;
    node->obj_ref = env->NewWeakGlobalRef(obj);
    node->references_processed = JNI_FALSE;
    node->tag = tag;
    depth_first_search_stack.push(node);
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_clearDepthFirstSearchStack(JNIEnv *env,
                                                                                                                    jclass klass) {
    while(!depth_first_search_stack.empty()) {
        depthFirstSearchStackPop(env);
    }
}

JNIEXPORT jint JNICALL Java_com_android_tools_idea_diagnostics_heap_StackNode_getDepthFirstSearchStackSize(JNIEnv *env,
                                                                                                                      jclass klass) {
    return depth_first_search_stack.size();
}

std::unordered_map<int, ObjectMapNode*>::iterator objectIdToTraverseNodeMapEraseIterator
    (JNIEnv *env, std::unordered_map<int, ObjectMapNode*>::iterator it) {
    if (it == object_id_to_traverse_node_map.end()) {
      return it;
    }
    env->DeleteWeakGlobalRef(it->second->obj_ref);
    delete it->second;
    return object_id_to_traverse_node_map.erase(it);
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_cacheHeapSnapshotTraverseNodeConstructorId
    (JNIEnv *env, jclass klass, jclass heapTraverseNodeClass) {
    heap_snapshot_traverse_node_constructor = env->GetMethodID(heapTraverseNodeClass, "<init>", "(Ljava/lang/Object;IJJIJ)V");
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_clearObjectIdToTraverseNodeMap
    (JNIEnv *env, jclass klass) {
    for (auto it = object_id_to_traverse_node_map.begin(); it != object_id_to_traverse_node_map.end(); ) {
        it = objectIdToTraverseNodeMapEraseIterator(env, it);
    }
}

JNIEXPORT jint JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_getObjectIdToTraverseNodeMapSize
    (JNIEnv *env, jclass klass) {
    return object_id_to_traverse_node_map.size();
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_removeElementFromObjectIdToTraverseNodeMap
    (JNIEnv *env, jclass klass, jint id) {
    objectIdToTraverseNodeMapEraseIterator(env, object_id_to_traverse_node_map.find(id));
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_putOrUpdateObjectIdToTraverseNodeMap
    (JNIEnv *env, jclass klass, jint id, jobject obj, jint ref_weight, jlong owned_by_component_mask, jlong retained_mask,
    jint retained_mask_for_categories, jlong tag) {
    ObjectMapNode* node;
    auto element_iterator = object_id_to_traverse_node_map.find(id);
    if (element_iterator != object_id_to_traverse_node_map.end()) {
      node = element_iterator->second;
    }
    else {
      node = new ObjectMapNode();
      node->obj_ref = env->NewWeakGlobalRef(obj);
      object_id_to_traverse_node_map.insert({id, node});
    }
    node->ref_weight = ref_weight;
    node->owned_by_component_mask = owned_by_component_mask;
    node->retained_mask = retained_mask;
    node->retained_mask_for_categories = retained_mask_for_categories;
    node->tag = tag;
}

JNIEXPORT jobject JNICALL Java_com_android_tools_idea_diagnostics_heap_HeapTraverseNode_getObjectIdToTraverseNodeMapElement
    (JNIEnv *env, jclass klass, jint id, jclass heapTraverseNodeClass) {
    auto element_iterator = object_id_to_traverse_node_map.find(id);
    if (element_iterator== object_id_to_traverse_node_map.end()) {
      return NULL;
    }
    ObjectMapNode* node = element_iterator->second;
    return env->NewObject(heapTraverseNodeClass, heap_snapshot_traverse_node_constructor, node->obj_ref,
                                                                                      node->ref_weight,
                                                                                      node->owned_by_component_mask,
                                                                                      node->retained_mask,
                                                                                      node->retained_mask_for_categories,
                                                                                      node->tag);
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
