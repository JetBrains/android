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

package trebuchet.collections

@Suppress("UNCHECKED_CAST", "unused")
class SparseArray<E> constructor(initialCapacity: Int = 10) {
    private var mGarbage = false

    private var mKeys: IntArray
    private var mValues: Array<Any?>
    private var mSize: Int = 0

    init {
        if (initialCapacity == 0) {
            mKeys = EMPTY_INTS
            mValues = EMPTY_OBJECTS
        } else {
            val idealSize = idealIntArraySize(initialCapacity)
            mKeys = IntArray(idealSize)
            mValues = arrayOfNulls<Any>(idealSize)
        }
    }

    /**
     * Gets the Object mapped from the specified key, or `null`
     * if no such mapping has been made.
     */
    operator fun get(key: Int): E? {
        return get(key, null)
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object
     * if no such mapping has been made.
     */
    operator fun get(key: Int, valueIfKeyNotFound: E?): E? {
        val i = binarySearch(mKeys, mSize, key)

        if (i < 0 || mValues[i] === DELETED) {
            return valueIfKeyNotFound
        } else {
            return mValues[i] as E
        }
    }

    /**
     * Removes the mapping from the specified key, if there was any.
     */
    private fun delete(key: Int) {
        val i = binarySearch(mKeys, mSize, key)

        if (i >= 0) {
            if (mValues[i] !== DELETED) {
                mValues[i] = DELETED
                mGarbage = true
            }
        }
    }

    /**
     * Alias for [.delete].
     */
    fun remove(key: Int) {
        delete(key)
    }

    /**
     * Removes the mapping at the specified index.
     */
    private fun removeAt(index: Int) {
        if (mValues[index] !== DELETED) {
            mValues[index] = DELETED
            mGarbage = true
        }
    }

    /**
     * Remove a range of mappings as a batch.

     * @param index Index to begin at
     * *
     * @param size Number of mappings to remove
     */
    fun removeAtRange(index: Int, size: Int) {
        val end = minOf(mSize, index + size)
        for (i in index..end - 1) {
            removeAt(i)
        }
    }

    private fun gc() {
        val n = mSize
        var o = 0
        val keys = mKeys
        val values = mValues

        for (i in 0..n - 1) {
            val `val` = values[i]

            if (`val` !== DELETED) {
                if (i != o) {
                    keys[o] = keys[i]
                    values[o] = `val`
                    values[i] = null
                }

                o++
            }
        }

        mGarbage = false
        mSize = o
    }

    /**
     * Adds a mapping from the specified key to the specified value,
     * replacing the previous mapping from the specified key if there
     * was one.
     */
    fun put(key: Int, value: E) {
        var i = binarySearch(mKeys, mSize, key)

        if (i >= 0) {
            mValues[i] = value
        } else {
            i = i.inv()

            if (i < mSize && mValues[i] === DELETED) {
                mKeys[i] = key
                mValues[i] = value
                return
            }

            if (mGarbage && mSize >= mKeys.size) {
                gc()

                // Search again because indices may have changed.
                i = binarySearch(mKeys, mSize, key).inv()
            }

            if (mSize >= mKeys.size) {
                val n = idealIntArraySize(mSize + 1)

                val nkeys = IntArray(n)
                val nvalues = arrayOfNulls<Any>(n)

                System.arraycopy(mKeys, 0, nkeys, 0, mKeys.size)
                System.arraycopy(mValues, 0, nvalues, 0, mValues.size)

                mKeys = nkeys
                mValues = nvalues
            }

            if (mSize - i != 0) {
                System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i)
                System.arraycopy(mValues, i, mValues, i + 1, mSize - i)
            }

            mKeys[i] = key
            mValues[i] = value
            mSize++
        }
    }

    /**
     * Returns the number of key-value mappings that this SparseArray
     * currently stores.
     */
    fun size(): Int {
        if (mGarbage) {
            gc()
        }

        return mSize
    }

    /**
     * Given an index in the range `0...size()-1`, returns
     * the key from the `index`th key-value mapping that this
     * SparseArray stores.
     */
    private fun keyAt(index: Int): Int {
        if (mGarbage) {
            gc()
        }

        return mKeys[index]
    }

    /**
     * Given an index in the range `0...size()-1`, returns
     * the value from the `index`th key-value mapping that this
     * SparseArray stores.
     */
    private fun valueAt(index: Int): E {
        if (mGarbage) {
            gc()
        }

        return mValues[index] as E
    }

    /**
     * Given an index in the range `0...size()-1`, sets a new
     * value for the `index`th key-value mapping that this
     * SparseArray stores.
     */
    fun setValueAt(index: Int, value: E) {
        if (mGarbage) {
            gc()
        }

        mValues[index] = value
    }

    /**
     * Returns the index for which [.keyAt] would return the
     * specified key, or a negative number if the specified
     * key is not mapped.
     */
    fun indexOfKey(key: Int): Int {
        if (mGarbage) {
            gc()
        }

        return binarySearch(mKeys, mSize, key)
    }

    /**
     * Returns an index for which [.valueAt] would return the
     * specified key, or a negative number if no keys map to the
     * specified value.
     *
     * Beware that this is a linear search, unlike lookups by key,
     * and that multiple keys can map to the same value and this will
     * find only one of them.
     *
     * Note also that unlike most collections' `indexOf` methods,
     * this method compares values using `==` rather than `equals`.
     */
    fun indexOfValue(value: E): Int {
        if (mGarbage) {
            gc()
        }

        for (i in 0..mSize - 1)
            if (mValues[i] === value)
                return i

        return -1
    }

    /**
     * Removes all key-value mappings from this SparseArray.
     */
    fun clear() {
        val n = mSize
        val values = mValues

        for (i in 0..n - 1) {
            values[i] = null
        }

        mSize = 0
        mGarbage = false
    }

    /**
     * Puts a key/value pair into the array, optimizing for the case where
     * the key is greater than all existing keys in the array.
     */
    fun append(key: Int, value: E) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value)
            return
        }

        if (mGarbage && mSize >= mKeys.size) {
            gc()
        }

        val pos = mSize
        if (pos >= mKeys.size) {
            val n = idealIntArraySize(pos + 1)

            val nkeys = IntArray(n)
            val nvalues = arrayOfNulls<Any>(n)

            System.arraycopy(mKeys, 0, nkeys, 0, mKeys.size)
            System.arraycopy(mValues, 0, nvalues, 0, mValues.size)

            mKeys = nkeys
            mValues = nvalues
        }

        mKeys[pos] = key
        mValues[pos] = value
        mSize = pos + 1
    }

    /**
     * {@inheritDoc}

     *
     * This implementation composes a string by iterating over its mappings. If
     * this map contains itself as a value, the string "(this Map)"
     * will appear in its place.
     */
    override fun toString(): String {
        if (size() <= 0) {
            return "{}"
        }

        val buffer = StringBuilder(mSize * 28)
        buffer.append('{')
        for (i in 0..mSize - 1) {
            if (i > 0) {
                buffer.append(", ")
            }
            val key = keyAt(i)
            buffer.append(key)
            buffer.append('=')
            val value = valueAt(i)
            if (value !== this) {
                buffer.append(value)
            } else {
                buffer.append("(this Map)")
            }
        }
        buffer.append('}')
        return buffer.toString()
    }

    companion object {
        private val DELETED = Any()

        val EMPTY_INTS = IntArray(0)
        val EMPTY_OBJECTS = arrayOfNulls<Any>(0)

        fun idealIntArraySize(need: Int): Int {
            return idealByteArraySize(need * 4) / 4
        }

        private fun idealByteArraySize(need: Int): Int {
            for (i in 4..31)
                if (need <= (1 shl i) - 12)
                    return (1 shl i) - 12

            return need
        }

        // This is Arrays.binarySearch(), but doesn't do any argument validation.
        fun binarySearch(array: IntArray, size: Int, value: Int): Int {
            var lo = 0
            var hi = size - 1

            while (lo <= hi) {
                val mid = (lo + hi).ushr(1)
                val midVal = array[mid]

                if (midVal < value) {
                    lo = mid + 1
                } else if (midVal > value) {
                    hi = mid - 1
                } else {
                    return mid  // value found
                }
            }
            return lo.inv()  // value not present
        }
    }
}