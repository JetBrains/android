/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.aidl.tests;

@SuppressWarnings(value={"const-name"})
@Backing(type="int")
enum ConstantExpressionEnum {
    // Should be all true / ones.
    // dec literals are either int or long
    decInt32_1 = (~(-1)) == 0,
    decInt32_2 = ~~(1 << 31) == (1 << 31),
    decInt64_1 = (~(-1L)) == 0,
    decInt64_2 = (~4294967295L) != 0,
    decInt64_3 = (~4294967295) != 0,
    decInt64_4 = ~~(1L << 63) == (1L << 63),

    // hex literals could be int or long
    // 0x7fffffff is int, hence can be negated
    hexInt32_1 = -0x7fffffff < 0,

    // 0x80000000 is int32_t max + 1
    hexInt32_2 = 0x80000000 < 0,

    // 0xFFFFFFFF is int32_t, not long; if it were long then ~(long)0xFFFFFFFF != 0
    hexInt32_3 = ~0xFFFFFFFF == 0,

    // 0x7FFFFFFFFFFFFFFF is long, hence can be negated
    hexInt64_1 = -0x7FFFFFFFFFFFFFFF < 0
}
