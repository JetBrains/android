/*
 * Copyright (C) 2017 The Android Open Source Project
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
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread

class Test {

  // Make sure the old annotation names still work.
  @android.support.annotation.UiThread fun oldAnnotationA() { oldAnnotationB() }
  fun oldAnnotationB() { oldAnnotationC() }
  @android.support.annotation.WorkerThread fun oldAnnotationC() {}

  @UiThread fun uiThread() { unannotated() }
  fun unannotated() { workerThread() }
  @WorkerThread fun workerThread() {}

  @UiThread fun runUi() {}
  fun runIt(r: () -> Unit) { r() }
  @WorkerThread fun callRunIt() { runIt({ runUi() }) }


  interface It { fun run(r: () -> Unit) }

  inner class A : It {
    @UiThread
    override fun run(r: () -> Unit) { r() }
  }

  inner class B : It {
    @WorkerThread
    override fun run(r: () -> Unit) { r() }
  }

  @UiThread
  fun a() {}

  @WorkerThread
  fun b() {}

  fun runWithIt(it: It, r: () -> Unit) { it.run(r) }

  fun f() {
    runWithIt(A(), this::b)
    runWithIt(B(), this::a)
  }

  @WorkerThread
  fun c() {}

  @UiThread
  fun d() {}

  fun callInvokeLater() {
    invokeLater({ c() })
    invokeLater({ d() }) // Ok.
  }

  fun callInvokeInBackground() {
    invokeInBackground({ d() })
    invokeInBackground({ c() }) // Ok.
  }

  companion object {
    @UiThread fun uiThreadStatic() { unannotatedStatic() }

    fun unannotatedStatic() { workerThreadStatic() }

    @WorkerThread fun workerThreadStatic() {}

    @JvmStatic fun main(args: Array<String>) {
      val instance = Test()
      instance.uiThread()
    }

    fun invokeLater(@UiThread runnable: () -> Unit) { /* place on queue to invoke on UiThread */ }

    fun invokeInBackground(@WorkerThread runnable: () -> Unit) { /* place on queue to invoke on background thread */ }
  }
}