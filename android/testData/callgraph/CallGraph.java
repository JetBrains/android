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


/** Test lambdas. */
class Lambdas {
  void f() {}
  void g() {
    Runnable r = this::f;
    r.run();
  }
  void h() {
    Runnable r = () -> { f(); g(); };
    r.run();
  }
}