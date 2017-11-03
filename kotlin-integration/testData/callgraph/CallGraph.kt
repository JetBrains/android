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
open class Trivial {

  fun empty() {}

  fun static1() { static2() }
  fun static2() { static3() }
  fun static3() {}

  private fun private1() { private2() }
  private fun private2() { private3() }
  private fun private3() {}

  open fun public1() { public2() }
  open fun public2() { public3() }
  open fun public3() {}
}


interface It {
  fun f()
  fun implUnique()
}

open class Impl : It {
  override fun f() { implUnique() }
  override fun implUnique() {}
}

class SubImpl : Impl() {
  override fun f() { subImplUnique() }
  fun subImplUnique() {}
}


/** Test simple call chains using call hierarchy analysis and type estimates. */
class SimpleLocal {
  fun notUnique() {
    val it: It? = null
    it?.f()
  }

  fun unique() {
    val it: It? = null
    it?.implUnique()
  }

  fun typeEvidencedSubImpl() {
    val it = SubImpl()
    it.f()
  }

  fun typeEvidencedImpl() {
    val it = Impl()
    it.f()
  }

  fun typeEvidencedBoth() {
    var it: It = Impl()
    it = SubImpl()
    it.f()
  }
}


/** Test calls through fields. */
class SimpleField {
  var itNotUnique: It? = null
  var itUnique: It? = null
  var itTypeEvidencedSubImpl: It
  var itTypeEvidencedImpl: It
  var itTypeEvidencedBoth: It

  init {
    itTypeEvidencedSubImpl = SubImpl()
    itTypeEvidencedImpl = Impl()
    itTypeEvidencedBoth = Impl()
    itTypeEvidencedBoth = SubImpl()
  }

  fun notUnique() { itNotUnique!!.f() }
  fun unique() { itUnique!!.implUnique() }
  fun typeEvidencedSubImpl() { itTypeEvidencedSubImpl.f() }
  fun typeEvidencedImpl() { itTypeEvidencedImpl.f() }
  fun typeEvidencedBoth() { itTypeEvidencedBoth.f() }
}


/** Test calls through array elements.  */
class SimpleArray {
  var itNotUnique = arrayOfNulls<It>(1)
  var itUnique = arrayOfNulls<It>(1)
  var itTypeEvidencedSubImpl = arrayOfNulls<Array<It>>(1)
  var itTypeEvidencedImpl = arrayOfNulls<It>(1)
  var itTypeEvidencedBoth = arrayOfNulls<Array<It>>(1)

  init {
    itTypeEvidencedSubImpl[0]!![0] = SubImpl()
    itTypeEvidencedImpl[0] = Impl()
    itTypeEvidencedBoth[0]!![0] = Impl()
    itTypeEvidencedBoth[0]!![0] = SubImpl()
  }

  fun notUnique() { itNotUnique[0]!!.f() }
  fun unique() { itUnique[0]!!.implUnique() }
  fun typeEvidencedSubImpl() { itTypeEvidencedSubImpl[0]!![0].f() }
  fun typeEvidencedImpl() { itTypeEvidencedImpl[0]!!.f() }
  fun typeEvidencedBoth() { itTypeEvidencedBoth[0]!![0].f() }
}


/** Test special calls through this and super. */
open class Special {
  init {
    f()
    h()
  }

  open class SubSpecial : Special() {
    override fun f() { super.f() }
  }

  open class SubSubSpecial : SubSpecial() {
    override fun f() {}
    override fun g() { super.g() }
  }

  class SubSubSubSpecial : SubSubSpecial()

  open fun f() {}
  open fun g() {}
  fun h() {}
}


/** Test class and field initializers. */
class Initializers {
  private var n = Nested.f()
  private val empty = Empty()
  private val inner = Inner()

  init {
    Nested.g()
  }

  class Nested {
    init { h() }

    companion object {
      fun f(): Int { return 0 }
      fun g() {}
      fun h() {}
    }
  }

  class Empty

  inner class Inner {
    init { ++n }
  }

  companion object {
    private val nested = Nested()
    init { Nested.h() }
  }
}


/** Test return values. */
class Return {

  interface RetUniqueIt {
    fun f()
  }

  class RetUnique : RetUniqueIt {
    override fun f() {}
  }

  fun createRetUniqueIt(): RetUniqueIt {
    return RetUnique()
  }

  fun unique() {
    val it = createRetUniqueIt()
    it.f()
  }

  interface RetAmbigIt {
    fun f()
  }

  inner class RetAmbigA : RetAmbigIt {
    override fun f() {}
  }

  inner class RetAmbigB : RetAmbigIt {
    override fun f() {}
  }

  fun createRetAmbigNull(): RetAmbigIt? { return null }

  fun ambig() {
    val it = createRetAmbigNull()
    it?.f()
  }

  fun evidenced3(): RetAmbigIt { return RetAmbigA() }
  fun evidenced2(): RetAmbigIt { return evidenced3() }
  fun evidenced1(): RetAmbigIt { return evidenced2() }
  fun evidenced() {
    val it = evidenced1()
    it.f()
  }
}


/** Test lambdas. */
class Lambdas {
  fun f() {}
  fun g() {
    val r = this::f
    r()
  }

  fun h() {
    val r = { f(); g() }
    r()
  }

  fun i() {
    val r = object : Runnable {
      override fun run() { f() }
    }
    r.run()
  }
}


/** Test contextual call paths, found by tracking lambdas and concrete types. */
class Contextual {
  // Test paths relying only on a single argument.
  fun f() {}
  fun g() {}
  fun a() { run({ f() }) }
  fun b() { run({ g() }) }
  fun run(r: () -> Unit, vararg ignoredVarArg: Any) { r() }

  // Test paths relying on multiple arguments at once.
  interface MultiArg {
    fun run(r: () -> Unit)
  }

  inner class MultiArgA : MultiArg {
    override fun run(r: () -> Unit) { r() }
  }

  inner class MultiArgB : MultiArg {
    override fun run(r: () -> Unit) { r() }
  }

  fun runMultiArg(it: MultiArg, r: () -> Unit) { it.run(r) }
  fun multiArgA() { runMultiArg(MultiArgA(), { f() }) }
  fun multiArgB() { runMultiArg(MultiArgB(), { g() }) }

  // Test paths also relying on the implicit `this` argument.
  abstract inner class ImplicitThis {
    fun run(r: () -> Unit) { myRun(r) }
    protected abstract fun myRun(r: () -> Unit)
  }
  inner class ImplicitThisA : ImplicitThis() {
    override fun myRun(r: () -> Unit) { r() }
  }
  inner class ImplicitThisB : ImplicitThis() {
    override fun myRun(r: () -> Unit) { r() }
  }

  fun runImplicitThis(it: ImplicitThis, r: () -> Unit) { it.run(r) }
  fun implicitThisA() { runImplicitThis(ImplicitThisA(), { f() }) }
  fun implicitThisB() { runImplicitThis(ImplicitThisB(), { g() }) }

  // Test long contextual paths.
  fun run1(r: () -> Unit) { run(r) }
  fun run2(r: () -> Unit) { run1(r) }
  fun run3(r: () -> Unit) { run2(r) }
  fun run4(r: () -> Unit) { run3(r) }
  fun run5(r: () -> Unit) { run4(r) }
  fun c() {
    run5({ f() })
  }
}

fun topLevelA() { topLevelB() }
fun topLevelB() { topLevelC() }
fun topLevelC() { topLevelB() }
