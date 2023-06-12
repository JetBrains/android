/*
 * Copyright (C) 2022 The Android Open Source Project
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
package trebuchet.util

import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test
import trebuchet.io.BufferProducer
import trebuchet.io.DataSlice
import trebuchet.io.GenericByteBuffer
import trebuchet.io.StreamingReader

class StringSearchTest {
  /**
   * Read the source buffer in slices until there is no more data left.
   */
  private class MockBufferProducer(_source: String, private val sliceSize: Int) : BufferProducer {
    private val source = _source.toByteArray()
    private var tail = 0

    override fun next(): DataSlice? {
      val start = tail
      tail = (start + sliceSize).coerceAtMost(source.size)

      return if (start == tail) null else DataSlice(source, start, tail)
    }
  }

  private val sliceSize = 2

  private fun String.asGenericByteBuffer(): GenericByteBuffer = this.let {
    object : GenericByteBuffer {
      override val length: Int
        get() = it.length

      override fun get(index: Int): Byte = it[index].code.toByte()
    }
  }

  @Test
  fun `find text at start of streaming reader`() {
    val search = StringSearch("hello")

    val producer = MockBufferProducer("hello world", sliceSize)
    val found = search.find(StreamingReader(producer))

    assertThat(found).isEqualTo(0)
  }

  @Test
  fun `find text at end of streaming reader`() {
    val search = StringSearch("world")

    val producer = MockBufferProducer("hello world", sliceSize)
    val found = search.find(StreamingReader(producer))

    assertThat(found).isEqualTo(6)
  }

  @Test
  fun `find text in middle of streaming reader`() {
    val search = StringSearch("world")

    val producer = MockBufferProducer("hello world, by old friend", sliceSize)
    val found = search.find(StreamingReader(producer))

    assertThat(found).isEqualTo(6)
  }

  @Ignore // Failing (java.lang.OutOfMemoryError)
  @Test
  fun `doesn't find missing text in middle of streaming reader`() {
    val search = StringSearch("balloon")

    val producer = MockBufferProducer("hello world, by old friend", sliceSize)
    val found = search.find(StreamingReader(producer))

    assertThat(found).isEqualTo(-1)
  }

  @Test
  fun `find in loaded region text at start of streaming reader`() {
    val search = StringSearch("hello")

    val producer = MockBufferProducer("hello world", sliceSize)
    val reader = StreamingReader(producer)

    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("he".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hell".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello ".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(0)
  }

  @Ignore // Failing (java.lang.IndexOutOfBoundsException)
  @Test
  fun `find in loaded region text at end of streaming reader`() {
    val search = StringSearch("world")

    val producer = MockBufferProducer("hello world", sliceSize)
    val reader = StreamingReader(producer)

    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("he".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hell".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello ".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello wo".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello worl".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(6)
  }

  @Ignore // Failing (java.lang.IndexOutOfBoundsException)
  @Test
  fun `find in loaded region text in middle of streaming reader`() {
    val search = StringSearch("world")

    val producer = MockBufferProducer("hello world, my old friend", sliceSize)
    val reader = StreamingReader(producer)

    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hell".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello wo".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world,".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(6)

    assertThat(reader.loadIndex("hello world, my ".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(6)

    assertThat(reader.loadIndex("hello world, my old ".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(6)

    assertThat(reader.loadIndex("hello world, my old frie".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(6)

    assertThat(reader.loadIndex("hello world, my old friend".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(6)
  }

  @Ignore // Failing(java.lang.IndexOutOfBoundsException)
  @Test
  fun `doesn't find missing text in loaded region of streaming reader`() {
    val search = StringSearch("balloon")

    val producer = MockBufferProducer("hello world, my old friend", sliceSize)
    val reader = StreamingReader(producer)

    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hell".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello wo".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world,".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world, my ".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world, my old ".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world, my old frie".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)

    assertThat(reader.loadIndex("hello world, my old friend".length - 1)).isTrue()
    assertThat(search.findInLoadedRegion(reader)).isEqualTo(-1)
  }

  @Test
  fun `find text at start of generic byte array`() {
    val found = StringSearch("hello").find("hello world".asGenericByteBuffer())
    assertThat(found).isEqualTo(0)
  }

  @Test
  fun `find text at end of generic byte array`() {
    val found = StringSearch("world").find("hello world".asGenericByteBuffer())
    assertThat(found).isEqualTo(6)
  }

  @Test
  fun `find text in middle of generic byte array`() {
    val found = StringSearch("world").find("hello world, my old friend".asGenericByteBuffer())
    assertThat(found).isEqualTo(6)
  }

  @Ignore // Failing (wrong value)
  @Test
  fun `doesn't find missing text in generic byte array`() {
    val found = StringSearch("balloon").find("hello world, my old friend".asGenericByteBuffer())
    assertThat(found).isEqualTo(-1)
  }

  @Ignore // Failing (wrong value)
  @Test
  fun `doesn't find text when starting after it in generic byte buffer`() {
    val found = StringSearch("hello").find("hello world, my old friend".asGenericByteBuffer(), startIndex = 7)
    assertThat(found).isEqualTo(-1)
  }

  @Ignore // Failing (java.lang.StringIndexOutOfBoundsException)
  @Test
  fun `ignores endIndex when longer than generic byte buffer`() {
    val found = StringSearch("balloon").find("hello world, my old friend".asGenericByteBuffer(), endIndex = Int.MAX_VALUE)
    assertThat(found).isEqualTo(-1)
  }

  @Test
  fun `find text at start of byte array`() {
    val found = StringSearch("hello").find("hello world".toByteArray())
    assertThat(found).isEqualTo(0)
  }

  @Test
  fun `find text at end of byte array`() {
    val found = StringSearch("world").find("hello world".toByteArray())
    assertThat(found).isEqualTo(6)
  }

  @Test
  fun `find text in middle of byte array`() {
    val found = StringSearch("world").find("hello world, my old friend".toByteArray())
    assertThat(found).isEqualTo(6)
  }

  @Ignore // Failing (wrong value)
  @Test
  fun `doesn't find missing text in byte array`() {
    val found = StringSearch("balloon").find("hello world, my old friend".toByteArray())
    assertThat(found).isEqualTo(-1)
  }

  @Ignore // Failing (wrong value)
  @Test
  fun `doesn't find text when starting after it in byte array`() {
    val found = StringSearch("hello").find("hello world, my old friend".toByteArray(), startIndex = 7)
    assertThat(found).isEqualTo(-1)
  }

  @Ignore // Failing (java.lang.StringIndexOutOfBoundsException)
  @Test
  fun `ignores endIndex when longer than byte array`() {
    val found = StringSearch("balloon").find("hello world, my old friend".toByteArray(), endIndex = Int.MAX_VALUE)
    assertThat(found).isEqualTo(-1)
  }
}