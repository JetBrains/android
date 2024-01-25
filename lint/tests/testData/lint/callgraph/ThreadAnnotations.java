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
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

@FunctionalInterface
public interface Runnable {
  public abstract void run();
}

class Test {

  // Make sure the old annotation names still work.
  @android.support.annotation.UiThread static void oldAnnotationA() { oldAnnotationB() }
  static void oldAnnotationB() { oldAnnotationC() }
  @android.support.annotation.WorkerThread static void oldAnnotationC() {}

  @UiThread static void uiThreadStatic() { unannotatedStatic(); }
  static void unannotatedStatic() { workerThreadStatic(); }
  @WorkerThread static void workerThreadStatic() {}

  @UiThread void uiThread() { unannotated(); }
  void unannotated() { workerThread(); }
  @WorkerThread void workerThread() {}

  @UiThread void runUi() {}
  void runIt(Runnable r) { r.run(); }
  @WorkerThread void callRunIt() {
    runIt(() -> runUi());
  }

  public static void main(String[] args) {
    Test instance = new Test();
    instance.uiThread();
  }


  interface It {
    void run(Runnable r);
  }

  class A implements It {
    @UiThread
    public void run(Runnable r) { r.run(); }
  }

  class B implements It {
    @WorkerThread
    public void run(Runnable r) { r.run(); }
  }

  @UiThread
  void a() {}

  @WorkerThread
  void b() {}

  void runWithIt(It it, Runnable r) { it.run(r); }

  void f() {
    runWithIt(new A(), this::b);
    runWithIt(new B(), this::a);
  }

  public static void invokeLater(@UiThread Runnable runnable) { /* place on queue to invoke on UiThread */ }

  public static void invokeInBackground(@WorkerThread Runnable runnable) { /* place on queue to invoke on background thread */ }

  @WorkerThread
  void c() {}

  @UiThread
  void d() {}

  void callInvokeLater() {
    invokeLater(() -> c());
    invokeLater(() -> d()); // Ok.
  }

  void callInvokeInBackground() {
    invokeInBackground(() -> d());
    invokeInBackground(() -> c()); // Ok.
  }
}