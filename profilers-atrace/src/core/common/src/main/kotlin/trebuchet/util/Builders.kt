/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package trebuchet.util

import kotlin.reflect.full.createInstance

private fun <T> noReset(t: T) = t

class StartEndBuilder<R, out P>(
        private val new: () -> P,
        private val reset: (P) -> P = ::noReset) {

    private val stack = mutableListOf<P>()
    private val garbage = mutableListOf<P>()

    fun start(cb: P.() -> Unit) {
        val p = if (garbage.isNotEmpty())
                garbage.removeAt(garbage.lastIndex)
            else
                new()
        stack.add(p)
        cb.invoke(p)
    }

    fun end(cb: P.() -> R): R? {
        if (stack.isEmpty()) return null
        val p = stack.removeAt(stack.lastIndex)
        val ret = cb.invoke(p)
        garbage.add(reset(p))
        return ret
    }

    companion object {
        inline fun <R, reified P : Any> make() : StartEndBuilder<R, P> {
            return StartEndBuilder(P::class::createInstance)
        }
    }
}
