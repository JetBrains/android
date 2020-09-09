/*
 * Copyright (C) 2016 The Android Open Source Project
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

@file:JvmName("AvdNameVerifier")

package com.android.tools.idea.deviceManager.avdmanager

private const val ALLOWED_CHARS = "0-9a-zA-Z-_. ()"
private const val ALLOWED_CHARS_READABLE = "a-z A-Z 0-9 . _ - ( )"

/**
 * The name is valid if it has one or more allowed characters and only allowed characters
 */
fun isValid(candidateName: String): Boolean = candidateName.matches("^[$ALLOWED_CHARS]+$".toRegex())

/**
 * Remove any invalid characters.
 */
fun stripBadCharacters(candidateName: String): String = candidateName.replace("[^$ALLOWED_CHARS]".toRegex(), " ")

/**
 * Remove any invalid characters. Remove leading and trailing spaces.
 * Replace consecutive spaces, parentheses, and underscores by a single underscore.
 */
fun stripBadCharactersAndCollapse(candidateName: String): String = candidateName
  .replace("[^$ALLOWED_CHARS]".toRegex(), " ")
  .replace("[ ()_]+".toRegex(), "_")

fun humanReadableAllowedCharacters(): String = ALLOWED_CHARS_READABLE
