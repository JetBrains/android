#include "jniBleakHelper.h"
#include <cstring>
using namespace std;

jvmtiEnv *jvmti;

JNIEXPORT void JNICALL Java_com_android_tools_idea_tests_gui_framework_heapassertions_bleak_JniBleakHelper_pauseThreads0(JNIEnv *env) {
	jint nthreads;
	jthread *threads;
	jvmti->GetAllThreads(&nthreads, &threads);
	for (int i=0; i < nthreads; i++) {
		jvmtiThreadInfo info;
		jvmti->GetThreadInfo(threads[i], &info);
		if (strcmp("Time-limited test", info.name)) {	// if it's not the UI test thread, suspend it
//		  printf("Pausing thread %d (%s)\n", i, info.name);
			jvmti->SuspendThread(threads[i]);
		}
	}
}

JNIEXPORT void JNICALL Java_com_android_tools_idea_tests_gui_framework_heapassertions_bleak_JniBleakHelper_resumeThreads0(JNIEnv *env) {
	jint nthreads;
	jthread *threads;
	jvmti->GetAllThreads(&nthreads, &threads);
	for (int i=0; i < nthreads; i++) {
		jvmtiThreadInfo info;
		jvmti->GetThreadInfo(threads[i], &info);
		if (strcmp("Time-limited test", info.name)) {	// if it's not the UI test thread, resume it
//		  printf("Resuming thread %d\n", i);
			jvmti->ResumeThread(threads[i]);
		}
	}
}

const jlong GC_ROOT_TAG = 1;

jvmtiIterationControl JNICALL heapRootCallback(jvmtiHeapRootKind root_kind, jlong class_tag, jlong size, jlong *tag_ptr, void *user_data) {
  *tag_ptr = GC_ROOT_TAG;
  return JVMTI_ITERATION_IGNORE;
}

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_tests_gui_framework_heapassertions_bleak_JniBleakHelper_gcRoots(JNIEnv *env) {
  jvmti->IterateOverReachableObjects(heapRootCallback, NULL, NULL, NULL);
  jint nroots;
  jobject *roots;
  jvmti->GetObjectsWithTags(1, &GC_ROOT_TAG, &nroots, &roots, NULL);

  jclass objectClass = env->FindClass("java/lang/Object");
  jobjectArray arr = env->NewObjectArray(nroots, objectClass, NULL);
  for (int i=0; i < nroots; i++) {
    env->SetObjectArrayElement(arr, i, roots[i]);
  }
  return arr;
}

JNIEXPORT jobjectArray JNICALL Java_com_android_tools_idea_tests_gui_framework_heapassertions_bleak_JniBleakHelper_allLoadedClasses0(JNIEnv *env) {
  jint nclasses;
  jclass *classes;
  jvmti->GetLoadedClasses(&nclasses, &classes);

  jclass objectClass = env->FindClass("java/lang/Object");
  jobjectArray arr = env->NewObjectArray(nclasses, objectClass, NULL);
  for (int i=0; i < nclasses; i++) {
    env->SetObjectArrayElement(arr, i, classes[i]);
  }
  return arr;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
	vm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_0);
	jvmtiCapabilities capa;
	jvmti->GetCapabilities(&capa);
	capa.can_signal_thread = 1;
	capa.can_tag_objects = 1;
	jvmtiError err = jvmti->AddCapabilities(&capa);
	if (err != JVMTI_ERROR_NONE) {
	  printf("Jvmti error setting capabilities: %d\n", err);
	}
	return 0;
}
