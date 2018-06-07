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
@file:JvmName("LiteralTokenTypes")

package com.android.tools.idea.lang.roomSql.psi

@JvmField val UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL = RoomTokenType("unterminated single quote string literal")
@JvmField val UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL = RoomTokenType("unterminated double quote string literal")
@JvmField val UNTERMINATED_BRACKET_LITERAL = RoomTokenType("unterminated bracket literal")
@JvmField val UNTERMINATED_BACKTICK_LITERAL = RoomTokenType("unterminated backtick literal")
