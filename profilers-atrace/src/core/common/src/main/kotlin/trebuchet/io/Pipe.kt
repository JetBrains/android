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

package trebuchet.io

import java.util.concurrent.ArrayBlockingQueue

interface Producer<in T> {
    fun add(t: T)
    fun close()
}

interface Consumer<out T> {
    fun next(): T?
}

class Pipe<T>(capacity: Int = 4) : Producer<T>, Consumer<T> {
    private class Packet<out T>(val data: T?)

    private val queue = ArrayBlockingQueue<Packet<T>>(capacity)
    private var producerClosed = false
    private var consumerClosed = false

    override fun add(data: T) {
        if (data == null) throw IllegalStateException("Unable to send null")

        if (producerClosed) throw IllegalStateException("Already closed")
        queue.put(Packet(data))
    }

    override fun close() {
        if (!producerClosed) {
            producerClosed = true
            queue.put(Packet(null))
        }
    }

    override fun next(): T? {
        if (consumerClosed) return null
        val packet = queue.take()
        if (packet.data == null) {
            consumerClosed = true
        }
        return packet.data
    }
}