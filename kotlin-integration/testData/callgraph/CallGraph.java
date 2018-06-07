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
package com.android.tools.idea.callgraph

import java.util.function.Consumer;


/** Test trivial call chains. */
class Trivial {

  void empty() {}

  private static void static1() { static2(); }
  private static void static2() { static3(); }
  private static void static3() {}

  private void private1() { private2(); }
  private void private2() { private3(); }
  private void private3() {}

  public void public1() { public2(); }
  public void public2() { public3(); }
  public void public3() {}
}


interface It {
  void f();
  void implUnique();
}

class Impl implements It {
  @Override public void f() { implUnique(); }
  @Override public void implUnique() {}
}

class SubImpl extends Impl {
  @Override public void f() { subImplUnique(); }
  public void subImplUnique() {}
}


/** Test simple call chains using call hierarchy analysis and type estimates. */
class SimpleLocal {
  void notUnique() { It it = null; it.f(); }
  void unique() { It it = null; it.implUnique(); }
  void typeEvidencedSubImpl() { It it = new SubImpl(); it.f(); }
  void typeEvidencedImpl() { It it = new Impl(); it.f(); }
  void typeEvidencedBoth() { It it = new Impl(); it = new SubImpl(); it.f(); }
}


/** Test calls through fields. */
class SimpleField {
  It itNotUnique;
  It itUnique;
  It itTypeEvidencedSubImpl;
  It itTypeEvidencedImpl;
  It itTypeEvidencedBoth;

  SimpleField() {
    itTypeEvidencedSubImpl = new SubImpl();
    itTypeEvidencedImpl = new Impl();
    itTypeEvidencedBoth = new Impl();
    itTypeEvidencedBoth = new SubImpl();
  }

  void notUnique() { itNotUnique.f(); }
  void unique() { itUnique.implUnique(); }
  void typeEvidencedSubImpl() { itTypeEvidencedSubImpl.f(); }
  void typeEvidencedImpl() { itTypeEvidencedImpl.f(); }
  void typeEvidencedBoth() { itTypeEvidencedBoth.f(); }
}


/** Test calls through array elements. */
class SimpleArray {
  It[] itNotUnique = new It[1];
  It[] itUnique = new It[1];
  It[][] itTypeEvidencedSubImpl = new It[1][1];
  It[] itTypeEvidencedImpl = new It[1];
  It[][] itTypeEvidencedBoth = new It[1][1];

  SimpleArray() {
    itTypeEvidencedSubImpl[0][0] = new SubImpl();
    itTypeEvidencedImpl[0] = new Impl();
    itTypeEvidencedBoth[0][0] = new Impl();
    itTypeEvidencedBoth[0][0] = new SubImpl();
  }

  void notUnique() { itNotUnique[0].f(); }
  void unique() { itUnique[0].implUnique(); }
  void typeEvidencedSubImpl() { itTypeEvidencedSubImpl[0][0].f(); }
  void typeEvidencedImpl() { itTypeEvidencedImpl[0].f(); }
  void typeEvidencedBoth() { itTypeEvidencedBoth[0][0].f(); }
}


/** Test special calls through this and super. */
class Special {

  Special() { f(); h(); }

  static class SubSpecial extends Special {
    @Override void f() { super.f(); }
  }

  static class SubSubSpecial extends SubSpecial {
    SubSubSpecial() { /* Implicit call to super constructor. */ }
    @Override void f() {}
    @Override void g() { super.g(); }
  }

  static class SubSubSubSpecial extends SubSubSpecial {
    SubSubSubSpecial() { super(); }
  }

  void f() {}
  void g() {}
  void h() {}
}


/** Test class and field initializers. */
class Initializers {
  private static Nested nested = new Nested();
  private int n = Nested.f();
  private Empty empty = new Empty();
  private Inner inner = new Inner();
  { Nested.g(); }
  static { Nested.h(); }

  static class Nested {
    Nested() { h(); }
    static int f() { return 0; }
    static void g() {}
    static void h() {}
  }

  static class Empty {}

  class Inner {
    Inner() { ++n; }
  }
}


/** Test return values. */
class Return {

  interface RetUniqueIt { void f(); }

  static class RetUnique implements RetUniqueIt {
    @Override
    public void f() {}
  }

  RetUniqueIt createRetUniqueIt() { return new RetUnique(); }

  void unique() {
    RetUniqueIt it = createRetUniqueIt();
    it.f();
  }

  interface RetAmbigIt { void f(); }

  class RetAmbigA implements RetAmbigIt {
    @Override
    public void f() {}
  }

  class RetAmbigB implements RetAmbigIt {
    @Override
    public void f() {}
  }

  RetAmbigIt createRetAmbigNull() { return null; }

  void ambig() {
    RetAmbigIt it = createRetAmbigNull();
    it.f();
  }

  RetAmbigIt evidenced3() { return new RetAmbigA(); }
  RetAmbigIt evidenced2() { return evidenced3(); }
  RetAmbigIt evidenced1() { return evidenced2(); }
  void evidenced()  {
    RetAmbigIt it = evidenced1();
    it.f();
  }
}


/** Test lambdas. */
class Lambdas {
  void f(Object o) {}

  void g() {
    Consumer<Object> r = this::f;
    r.accept(null);
  }

  void h() {
    Runnable r = () -> { f(); g(); };
    r.run();
  }

  void i() {
    Runnable r = new Runnable() {
      @Override
      public void run() { f(); }
    };
    r.run();
  }

  void j() {
    Runnable captured = () -> f(null);
    Runnable r = () -> captured.run();
    r.run();
  }
}


/** Test contextual call paths, found by tracking lambdas and concrete types. */
class Contextual {
  // Test paths relying only on a single argument.
  void f() {}
  void g() {}
  void a() { run(() -> f()); }
  void b() { run(() -> g()); }
  void run(Runnable r, Object... ignoredVarArg) { r.run(); }

  // Test paths relying on multiple arguments at once.
  interface MultiArg {
    void run(Runnable r);
  }
  class MultiArgA implements MultiArg {
    @Override
    public void run(Runnable r) { r.run(); }
  }
  class MultiArgB implements MultiArg {
    @Override
    public void run(Runnable r) { r.run(); }
  }
  void runMultiArg(MultiArg it, Runnable r) { it.run(r); }
  void multiArgA() { runMultiArg(new MultiArgA(), () -> f()); }
  void multiArgB() { runMultiArg(new MultiArgB(), () -> g()); }

  // Test paths also relying on the implicit `this` argument.
  abstract class ImplicitThis {
    void run(Runnable r) { myRun(r); }
    protected abstract void myRun(Runnable r);
  }
  class ImplicitThisA extends ImplicitThis {
    @Override
    protected void myRun(Runnable r) { r.run(); }
  }
  class ImplicitThisB extends ImplicitThis {
    @Override
    protected void myRun(Runnable r) { r.run(); }
  }
  void runWithConsumer(Consumer<Runnable> c, Runnable r) { c.accept(r); }
  void runImplicitThis(ImplicitThis it, Runnable r) { runWithConsumer(it::myRun, r); }
  void implicitThisA() { runImplicitThis(new ImplicitThisA(), this::f); }
  void implicitThisB() { runImplicitThis(new ImplicitThisB(), () -> g()); }

  // Test long contextual paths.
  void run1(Runnable r) { run(r); }
  void run2(Runnable r) { run1(r); }
  void run3(Runnable r) { run2(r); }
  void run4(Runnable r) { run3(r); }
  void run5(Runnable r) { run4(r); }
  void c() {
    run5(new Runnable() {
      @Override
      public void run() { f(); }
    });
  }

  // Test lambda captures.
  void runWrapped(Runnable captured) { run(() -> captured.run()); }
  void d() { runWrapped(() -> f()); }
  void runWrappedMulti(MultiArg it, Runnable r) { run(() -> it.run(r)); }
  void e() { runWrappedMulti(new MultiArgA(), this::f); }
  void h() { runWrappedMulti(new MultiArgB(), this::g); }

  // Test that recursive lambda specialization is properly limited. (If not, the analysis will never finish.)
  void recursiveCapture(Runnable r) { recursiveCapture(() -> r.run()); }
  void runRecursiveCapture() { recursiveCapture(() -> f()); }
}