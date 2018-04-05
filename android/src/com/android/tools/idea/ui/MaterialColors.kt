/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.google.common.collect.ArrayTable
import com.google.common.collect.Table
import java.awt.Color

/**
 * Class containing all material colors
 */
@Suppress("HasPlatformType")
object MaterialColors {

  enum class Color {
    RED,
    PINK,
    PURPLE,
    DEEP_PURPLE,
    INDIGO,
    BLUE,
    LIGHT_BLUE,
    CYAN,
    TEAL,
    GREEN,
    LIGHT_GREEN,
    LIME,
    YELLOW,
    AMBER,
    ORANGE,
    DEEP_ORANGE,
    BROWN,
    GREY,
    BLUE_GREY,
  }

  enum class Category(val displayName: String) {
    MATERIAL_50("Material 50"),
    MATERIAL_100("Material 100"),
    MATERIAL_200("Material 200"),
    MATERIAL_300("Material 300"),
    MATERIAL_400("Material 400"),
    MATERIAL_500("Material 500"),
    MATERIAL_600("Material 600"),
    MATERIAL_700("Material 700"),
    MATERIAL_800("Material 800"),
    MATERIAL_900("Material 900"),
    MATERIAL_ACCENT_100("Material A100"),
    MATERIAL_ACCENT_200("Material A200"),
    MATERIAL_ACCENT_400("Material A400"),
    MATERIAL_ACCENT_700("Material A700");

    override fun toString() = displayName
  }

  private val table: Table<Color, Category, java.awt.Color>
    = ArrayTable.create(Color.values().asIterable(), Category.values().asIterable())

  // Helper extension to allow using assignment to put value to the table
  operator fun <R, C, V> Table<R, C, V>.set(r: R, c: C, v: V) = put(r, c, v)

  init {
    table[Color.RED, Category.MATERIAL_50] = Color(255, 235, 238)
    table[Color.RED, Category.MATERIAL_100] = Color(255, 205, 210)
    table[Color.RED, Category.MATERIAL_200] = Color(239, 154, 154)
    table[Color.RED, Category.MATERIAL_300] = Color(229, 115, 115)
    table[Color.RED, Category.MATERIAL_400] = Color(239, 83, 80)
    table[Color.RED, Category.MATERIAL_500] = Color(244, 67, 54)
    table[Color.RED, Category.MATERIAL_600] = Color(229, 57, 53)
    table[Color.RED, Category.MATERIAL_700] = Color(211, 47, 47)
    table[Color.RED, Category.MATERIAL_800] = Color(198, 40, 40)
    table[Color.RED, Category.MATERIAL_900] = Color(183, 28, 28)
    table[Color.RED, Category.MATERIAL_ACCENT_100] = Color(255, 138, 128)
    table[Color.RED, Category.MATERIAL_ACCENT_200] = Color(255, 82, 82)
    table[Color.RED, Category.MATERIAL_ACCENT_400] = Color(255, 23, 68)
    table[Color.RED, Category.MATERIAL_ACCENT_700] = Color(213, 0, 0)

    table[Color.PINK, Category.MATERIAL_50] = Color(252, 228, 236)
    table[Color.PINK, Category.MATERIAL_100] = Color(248, 187, 208)
    table[Color.PINK, Category.MATERIAL_200] = Color(244, 143, 177)
    table[Color.PINK, Category.MATERIAL_300] = Color(240, 98, 146)
    table[Color.PINK, Category.MATERIAL_400] = Color(236, 64, 122)
    table[Color.PINK, Category.MATERIAL_500] = Color(233, 30, 99)
    table[Color.PINK, Category.MATERIAL_600] = Color(216, 27, 96)
    table[Color.PINK, Category.MATERIAL_700] = Color(194, 24, 91)
    table[Color.PINK, Category.MATERIAL_800] = Color(173, 20, 87)
    table[Color.PINK, Category.MATERIAL_900] = Color(136, 14, 79)
    table[Color.PINK, Category.MATERIAL_ACCENT_100] = Color(255, 128, 171)
    table[Color.PINK, Category.MATERIAL_ACCENT_200] = Color(255, 64, 129)
    table[Color.PINK, Category.MATERIAL_ACCENT_400] = Color(245, 0, 87)
    table[Color.PINK, Category.MATERIAL_ACCENT_700] = Color(197, 17, 98)

    table[Color.PURPLE, Category.MATERIAL_50] = Color(243, 229, 245)
    table[Color.PURPLE, Category.MATERIAL_100] = Color(225, 190, 231)
    table[Color.PURPLE, Category.MATERIAL_200] = Color(206, 147, 216)
    table[Color.PURPLE, Category.MATERIAL_300] = Color(186, 104, 200)
    table[Color.PURPLE, Category.MATERIAL_400] = Color(171, 71, 188)
    table[Color.PURPLE, Category.MATERIAL_500] = Color(156, 39, 176)
    table[Color.PURPLE, Category.MATERIAL_600] = Color(142, 36, 170)
    table[Color.PURPLE, Category.MATERIAL_700] = Color(123, 31, 162)
    table[Color.PURPLE, Category.MATERIAL_800] = Color(106, 27, 154)
    table[Color.PURPLE, Category.MATERIAL_900] = Color(74, 20, 140)
    table[Color.PURPLE, Category.MATERIAL_ACCENT_100] = Color(234, 128, 252)
    table[Color.PURPLE, Category.MATERIAL_ACCENT_200] = Color(224, 64, 251)
    table[Color.PURPLE, Category.MATERIAL_ACCENT_400] = Color(213, 0, 249)
    table[Color.PURPLE, Category.MATERIAL_ACCENT_700] = Color(170, 0, 255)

    table[Color.DEEP_PURPLE, Category.MATERIAL_50] = Color(237, 231, 246)
    table[Color.DEEP_PURPLE, Category.MATERIAL_100] = Color(209, 196, 233)
    table[Color.DEEP_PURPLE, Category.MATERIAL_200] = Color(179, 157, 219)
    table[Color.DEEP_PURPLE, Category.MATERIAL_300] = Color(149, 117, 205)
    table[Color.DEEP_PURPLE, Category.MATERIAL_400] = Color(126, 87, 194)
    table[Color.DEEP_PURPLE, Category.MATERIAL_500] = Color(103, 58, 183)
    table[Color.DEEP_PURPLE, Category.MATERIAL_600] = Color(94, 53, 177)
    table[Color.DEEP_PURPLE, Category.MATERIAL_700] = Color(81, 45, 168)
    table[Color.DEEP_PURPLE, Category.MATERIAL_800] = Color(69, 39, 160)
    table[Color.DEEP_PURPLE, Category.MATERIAL_900] = Color(49, 27, 146)
    table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_100] = Color(179, 136, 255)
    table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_200] = Color(124, 77, 255)
    table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_400] = Color(101, 31, 255)
    table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_700] = Color(98, 0, 234)

    table[Color.INDIGO, Category.MATERIAL_50] = Color(232, 234, 246)
    table[Color.INDIGO, Category.MATERIAL_100] = Color(197, 202, 233)
    table[Color.INDIGO, Category.MATERIAL_200] = Color(159, 168, 218)
    table[Color.INDIGO, Category.MATERIAL_300] = Color(121, 134, 203)
    table[Color.INDIGO, Category.MATERIAL_400] = Color(92, 107, 192)
    table[Color.INDIGO, Category.MATERIAL_500] = Color(63, 81, 181)
    table[Color.INDIGO, Category.MATERIAL_600] = Color(57, 73, 171)
    table[Color.INDIGO, Category.MATERIAL_700] = Color(48, 63, 159)
    table[Color.INDIGO, Category.MATERIAL_800] = Color(40, 53, 147)
    table[Color.INDIGO, Category.MATERIAL_900] = Color(26, 35, 126)
    table[Color.INDIGO, Category.MATERIAL_ACCENT_100] = Color(140, 158, 255)
    table[Color.INDIGO, Category.MATERIAL_ACCENT_200] = Color(83, 109, 254)
    table[Color.INDIGO, Category.MATERIAL_ACCENT_400] = Color(61, 90, 254)
    table[Color.INDIGO, Category.MATERIAL_ACCENT_700] = Color(48, 79, 254)

    table[Color.BLUE, Category.MATERIAL_50] = Color(227, 242, 253)
    table[Color.BLUE, Category.MATERIAL_100] = Color(187, 222, 251)
    table[Color.BLUE, Category.MATERIAL_200] = Color(144, 202, 249)
    table[Color.BLUE, Category.MATERIAL_300] = Color(100, 181, 246)
    table[Color.BLUE, Category.MATERIAL_400] = Color(66, 165, 245)
    table[Color.BLUE, Category.MATERIAL_500] = Color(33, 150, 243)
    table[Color.BLUE, Category.MATERIAL_600] = Color(30, 136, 229)
    table[Color.BLUE, Category.MATERIAL_700] = Color(25, 118, 210)
    table[Color.BLUE, Category.MATERIAL_800] = Color(21, 101, 192)
    table[Color.BLUE, Category.MATERIAL_900] = Color(13, 71, 161)
    table[Color.BLUE, Category.MATERIAL_ACCENT_100] = Color(130, 177, 255)
    table[Color.BLUE, Category.MATERIAL_ACCENT_200] = Color(68, 138, 255)
    table[Color.BLUE, Category.MATERIAL_ACCENT_400] = Color(41, 121, 255)
    table[Color.BLUE, Category.MATERIAL_ACCENT_700] = Color(41, 98, 255)

    table[Color.LIGHT_BLUE, Category.MATERIAL_50] = Color(225, 245, 254)
    table[Color.LIGHT_BLUE, Category.MATERIAL_100] = Color(179, 229, 252)
    table[Color.LIGHT_BLUE, Category.MATERIAL_200] = Color(129, 212, 250)
    table[Color.LIGHT_BLUE, Category.MATERIAL_300] = Color(79, 195, 247)
    table[Color.LIGHT_BLUE, Category.MATERIAL_400] = Color(41, 182, 246)
    table[Color.LIGHT_BLUE, Category.MATERIAL_500] = Color(3, 169, 244)
    table[Color.LIGHT_BLUE, Category.MATERIAL_600] = Color(3, 155, 229)
    table[Color.LIGHT_BLUE, Category.MATERIAL_700] = Color(2, 136, 209)
    table[Color.LIGHT_BLUE, Category.MATERIAL_800] = Color(2, 119, 189)
    table[Color.LIGHT_BLUE, Category.MATERIAL_900] = Color(1, 87, 155)
    table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_100] = Color(128, 216, 255)
    table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_200] = Color(64, 196, 255)
    table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_400] = Color(0, 176, 255)
    table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_700] = Color(0, 145, 234)

    table[Color.CYAN, Category.MATERIAL_50] = Color(224, 247, 250)
    table[Color.CYAN, Category.MATERIAL_100] = Color(178, 235, 242)
    table[Color.CYAN, Category.MATERIAL_200] = Color(128, 222, 234)
    table[Color.CYAN, Category.MATERIAL_300] = Color(77, 208, 225)
    table[Color.CYAN, Category.MATERIAL_400] = Color(38, 198, 218)
    table[Color.CYAN, Category.MATERIAL_500] = Color(0, 188, 212)
    table[Color.CYAN, Category.MATERIAL_600] = Color(0, 172, 193)
    table[Color.CYAN, Category.MATERIAL_700] = Color(0, 151, 167)
    table[Color.CYAN, Category.MATERIAL_800] = Color(0, 131, 143)
    table[Color.CYAN, Category.MATERIAL_900] = Color(0, 96, 100)
    table[Color.CYAN, Category.MATERIAL_ACCENT_100] = Color(132, 255, 255)
    table[Color.CYAN, Category.MATERIAL_ACCENT_200] = Color(24, 255, 255)
    table[Color.CYAN, Category.MATERIAL_ACCENT_400] = Color(0, 229, 255)
    table[Color.CYAN, Category.MATERIAL_ACCENT_700] = Color(0, 184, 212)

    table[Color.TEAL, Category.MATERIAL_50] = Color(224, 242, 241)
    table[Color.TEAL, Category.MATERIAL_100] = Color(178, 223, 219)
    table[Color.TEAL, Category.MATERIAL_200] = Color(128, 203, 196)
    table[Color.TEAL, Category.MATERIAL_300] = Color(77, 182, 172)
    table[Color.TEAL, Category.MATERIAL_400] = Color(38, 166, 154)
    table[Color.TEAL, Category.MATERIAL_500] = Color(0, 150, 136)
    table[Color.TEAL, Category.MATERIAL_600] = Color(0, 137, 123)
    table[Color.TEAL, Category.MATERIAL_700] = Color(0, 121, 107)
    table[Color.TEAL, Category.MATERIAL_800] = Color(0, 105, 92)
    table[Color.TEAL, Category.MATERIAL_900] = Color(0, 77, 64)
    table[Color.TEAL, Category.MATERIAL_ACCENT_100] = Color(167, 255, 235)
    table[Color.TEAL, Category.MATERIAL_ACCENT_200] = Color(100, 255, 218)
    table[Color.TEAL, Category.MATERIAL_ACCENT_400] = Color(29, 233, 182)
    table[Color.TEAL, Category.MATERIAL_ACCENT_700] = Color(0, 191, 165)

    table[Color.GREEN, Category.MATERIAL_50] = Color(232, 245, 233)
    table[Color.GREEN, Category.MATERIAL_100] = Color(200, 230, 201)
    table[Color.GREEN, Category.MATERIAL_200] = Color(165, 214, 167)
    table[Color.GREEN, Category.MATERIAL_300] = Color(129, 199, 132)
    table[Color.GREEN, Category.MATERIAL_400] = Color(102, 187, 106)
    table[Color.GREEN, Category.MATERIAL_500] = Color(76, 175, 80)
    table[Color.GREEN, Category.MATERIAL_600] = Color(67, 160, 71)
    table[Color.GREEN, Category.MATERIAL_700] = Color(56, 142, 60)
    table[Color.GREEN, Category.MATERIAL_800] = Color(46, 125, 50)
    table[Color.GREEN, Category.MATERIAL_900] = Color(27, 94, 32)
    table[Color.GREEN, Category.MATERIAL_ACCENT_100] = Color(185, 246, 202)
    table[Color.GREEN, Category.MATERIAL_ACCENT_200] = Color(105, 240, 174)
    table[Color.GREEN, Category.MATERIAL_ACCENT_400] = Color(0, 230, 118)
    table[Color.GREEN, Category.MATERIAL_ACCENT_700] = Color(0, 200, 83)

    table[Color.LIGHT_GREEN, Category.MATERIAL_50] = Color(241, 248, 233)
    table[Color.LIGHT_GREEN, Category.MATERIAL_100] = Color(220, 237, 200)
    table[Color.LIGHT_GREEN, Category.MATERIAL_200] = Color(197, 225, 165)
    table[Color.LIGHT_GREEN, Category.MATERIAL_300] = Color(174, 213, 129)
    table[Color.LIGHT_GREEN, Category.MATERIAL_400] = Color(156, 204, 101)
    table[Color.LIGHT_GREEN, Category.MATERIAL_500] = Color(139, 195, 74)
    table[Color.LIGHT_GREEN, Category.MATERIAL_600] = Color(124, 179, 66)
    table[Color.LIGHT_GREEN, Category.MATERIAL_700] = Color(104, 159, 56)
    table[Color.LIGHT_GREEN, Category.MATERIAL_800] = Color(85, 139, 47)
    table[Color.LIGHT_GREEN, Category.MATERIAL_900] = Color(51, 105, 30)
    table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_100] = Color(204, 255, 144)
    table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_200] = Color(178, 255, 89)
    table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_400] = Color(118, 255, 3)
    table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_700] = Color(100, 221, 23)

    table[Color.LIME, Category.MATERIAL_50] = Color(249, 251, 231)
    table[Color.LIME, Category.MATERIAL_100] = Color(240, 244, 195)
    table[Color.LIME, Category.MATERIAL_200] = Color(230, 238, 156)
    table[Color.LIME, Category.MATERIAL_300] = Color(220, 231, 117)
    table[Color.LIME, Category.MATERIAL_400] = Color(212, 225, 87)
    table[Color.LIME, Category.MATERIAL_500] = Color(205, 220, 57)
    table[Color.LIME, Category.MATERIAL_600] = Color(192, 202, 51)
    table[Color.LIME, Category.MATERIAL_700] = Color(175, 180, 43)
    table[Color.LIME, Category.MATERIAL_800] = Color(158, 157, 36)
    table[Color.LIME, Category.MATERIAL_900] = Color(130, 119, 23)
    table[Color.LIME, Category.MATERIAL_ACCENT_100] = Color(244, 255, 129)
    table[Color.LIME, Category.MATERIAL_ACCENT_200] = Color(238, 255, 65)
    table[Color.LIME, Category.MATERIAL_ACCENT_400] = Color(198, 255, 0)
    table[Color.LIME, Category.MATERIAL_ACCENT_700] = Color(174, 234, 0)

    table[Color.YELLOW, Category.MATERIAL_50] = Color(255, 253, 231)
    table[Color.YELLOW, Category.MATERIAL_100] = Color(255, 249, 196)
    table[Color.YELLOW, Category.MATERIAL_200] = Color(255, 245, 157)
    table[Color.YELLOW, Category.MATERIAL_300] = Color(255, 241, 118)
    table[Color.YELLOW, Category.MATERIAL_400] = Color(255, 238, 88)
    table[Color.YELLOW, Category.MATERIAL_500] = Color(255, 235, 59)
    table[Color.YELLOW, Category.MATERIAL_600] = Color(253, 216, 53)
    table[Color.YELLOW, Category.MATERIAL_700] = Color(251, 192, 45)
    table[Color.YELLOW, Category.MATERIAL_800] = Color(249, 168, 37)
    table[Color.YELLOW, Category.MATERIAL_900] = Color(245, 127, 23)
    table[Color.YELLOW, Category.MATERIAL_ACCENT_100] = Color(255, 255, 141)
    table[Color.YELLOW, Category.MATERIAL_ACCENT_200] = Color(255, 225, 0)
    table[Color.YELLOW, Category.MATERIAL_ACCENT_400] = Color(255, 234, 0)
    table[Color.YELLOW, Category.MATERIAL_ACCENT_700] = Color(255, 214, 0)

    table[Color.AMBER, Category.MATERIAL_50] = Color(255, 248, 225)
    table[Color.AMBER, Category.MATERIAL_100] = Color(255, 236, 179)
    table[Color.AMBER, Category.MATERIAL_200] = Color(255, 224, 130)
    table[Color.AMBER, Category.MATERIAL_300] = Color(255, 213, 79)
    table[Color.AMBER, Category.MATERIAL_400] = Color(255, 202, 40)
    table[Color.AMBER, Category.MATERIAL_500] = Color(255, 193, 7)
    table[Color.AMBER, Category.MATERIAL_600] = Color(255, 179, 0)
    table[Color.AMBER, Category.MATERIAL_700] = Color(255, 160, 0)
    table[Color.AMBER, Category.MATERIAL_800] = Color(255, 143, 0)
    table[Color.AMBER, Category.MATERIAL_900] = Color(255, 111, 0)
    table[Color.AMBER, Category.MATERIAL_ACCENT_100] = Color(255, 229, 127)
    table[Color.AMBER, Category.MATERIAL_ACCENT_200] = Color(255, 215, 64)
    table[Color.AMBER, Category.MATERIAL_ACCENT_400] = Color(255, 196, 0)
    table[Color.AMBER, Category.MATERIAL_ACCENT_700] = Color(255, 171, 0)

    table[Color.ORANGE, Category.MATERIAL_50] = Color(255, 243, 224)
    table[Color.ORANGE, Category.MATERIAL_100] = Color(255, 224, 178)
    table[Color.ORANGE, Category.MATERIAL_200] = Color(255, 204, 128)
    table[Color.ORANGE, Category.MATERIAL_300] = Color(255, 183, 77)
    table[Color.ORANGE, Category.MATERIAL_400] = Color(255, 167, 38)
    table[Color.ORANGE, Category.MATERIAL_500] = Color(255, 152, 0)
    table[Color.ORANGE, Category.MATERIAL_600] = Color(251, 140, 0)
    table[Color.ORANGE, Category.MATERIAL_700] = Color(245, 124, 0)
    table[Color.ORANGE, Category.MATERIAL_800] = Color(239, 108, 0)
    table[Color.ORANGE, Category.MATERIAL_900] = Color(230, 81, 0)
    table[Color.ORANGE, Category.MATERIAL_ACCENT_100] = Color(255, 209, 128)
    table[Color.ORANGE, Category.MATERIAL_ACCENT_200] = Color(255, 171, 64)
    table[Color.ORANGE, Category.MATERIAL_ACCENT_400] = Color(255, 145, 0)
    table[Color.ORANGE, Category.MATERIAL_ACCENT_700] = Color(255, 109, 0)

    table[Color.DEEP_ORANGE, Category.MATERIAL_50] = Color(251, 233, 231)
    table[Color.DEEP_ORANGE, Category.MATERIAL_100] = Color(255, 204, 188)
    table[Color.DEEP_ORANGE, Category.MATERIAL_200] = Color(255, 171, 145)
    table[Color.DEEP_ORANGE, Category.MATERIAL_300] = Color(255, 138, 101)
    table[Color.DEEP_ORANGE, Category.MATERIAL_400] = Color(255, 112, 67)
    table[Color.DEEP_ORANGE, Category.MATERIAL_500] = Color(255, 87, 34)
    table[Color.DEEP_ORANGE, Category.MATERIAL_600] = Color(244, 81, 30)
    table[Color.DEEP_ORANGE, Category.MATERIAL_700] = Color(230, 74, 25)
    table[Color.DEEP_ORANGE, Category.MATERIAL_800] = Color(216, 67, 21)
    table[Color.DEEP_ORANGE, Category.MATERIAL_900] = Color(191, 54, 12)
    table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_100] = Color(255, 158, 128)
    table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_200] = Color(255, 110, 64)
    table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_400] = Color(255, 61, 0)
    table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_700] = Color(221, 44, 0)

    table[Color.BROWN, Category.MATERIAL_50] = Color(239, 235, 233)
    table[Color.BROWN, Category.MATERIAL_100] = Color(215, 204, 200)
    table[Color.BROWN, Category.MATERIAL_200] = Color(188, 170, 164)
    table[Color.BROWN, Category.MATERIAL_300] = Color(161, 136, 127)
    table[Color.BROWN, Category.MATERIAL_400] = Color(141, 110, 99)
    table[Color.BROWN, Category.MATERIAL_500] = Color(121, 85, 72)
    table[Color.BROWN, Category.MATERIAL_600] = Color(109, 76, 65)
    table[Color.BROWN, Category.MATERIAL_700] = Color(93, 64, 55)
    table[Color.BROWN, Category.MATERIAL_800] = Color(78, 52, 46)
    table[Color.BROWN, Category.MATERIAL_900] = Color(62, 39, 35)

    table[Color.GREY, Category.MATERIAL_50] = Color(250, 250, 250)
    table[Color.GREY, Category.MATERIAL_100] = Color(245, 245, 245)
    table[Color.GREY, Category.MATERIAL_200] = Color(238, 238, 238)
    table[Color.GREY, Category.MATERIAL_300] = Color(224, 224, 224)
    table[Color.GREY, Category.MATERIAL_400] = Color(189, 189, 189)
    table[Color.GREY, Category.MATERIAL_500] = Color(158, 158, 158)
    table[Color.GREY, Category.MATERIAL_600] = Color(117, 117, 117)
    table[Color.GREY, Category.MATERIAL_700] = Color(97, 97, 97)
    table[Color.GREY, Category.MATERIAL_800] = Color(66, 66, 66)
    table[Color.GREY, Category.MATERIAL_900] = Color(33, 33, 33)

    table[Color.BLUE_GREY, Category.MATERIAL_50] = Color(236, 239, 241)
    table[Color.BLUE_GREY, Category.MATERIAL_100] = Color(207, 216, 220)
    table[Color.BLUE_GREY, Category.MATERIAL_200] = Color(176, 190, 197)
    table[Color.BLUE_GREY, Category.MATERIAL_300] = Color(144, 164, 174)
    table[Color.BLUE_GREY, Category.MATERIAL_400] = Color(120, 144, 156)
    table[Color.BLUE_GREY, Category.MATERIAL_500] = Color(96, 125, 139)
    table[Color.BLUE_GREY, Category.MATERIAL_600] = Color(84, 110, 122)
    table[Color.BLUE_GREY, Category.MATERIAL_700] = Color(69, 90, 100)
    table[Color.BLUE_GREY, Category.MATERIAL_800] = Color(55, 71, 79)
    table[Color.BLUE_GREY, Category.MATERIAL_900] = Color(38, 50, 56)
  }

  @JvmStatic
  fun getColor(name: Color, category: Category) = table[name, category]

  /**
   * Get the series of [java.awt.Color] by the given [Color].
   */
  @JvmStatic
  fun getColorSeries(name: Color) = table.row(name)

  /**
   * Get the set of [java.awt.Color] by the given [Category].
   */
  @JvmStatic
  fun getColorSet(category: Category): Map<Color, java.awt.Color> = table.column(category)

  // Keep these constants for back compatibility

  @JvmField val RED_50 = table[Color.RED, Category.MATERIAL_50]
  @JvmField val RED_100 = table[Color.RED, Category.MATERIAL_100]
  @JvmField val RED_200 = table[Color.RED, Category.MATERIAL_200]
  @JvmField val RED_300 = table[Color.RED, Category.MATERIAL_300]
  @JvmField val RED_400 = table[Color.RED, Category.MATERIAL_400]
  @JvmField val RED_500 = table[Color.RED, Category.MATERIAL_500]
  @JvmField val RED_600 = table[Color.RED, Category.MATERIAL_600]
  @JvmField val RED_700 = table[Color.RED, Category.MATERIAL_700]
  @JvmField val RED_800 = table[Color.RED, Category.MATERIAL_800]
  @JvmField val RED_900 = table[Color.RED, Category.MATERIAL_900]
  @JvmField val PINK_50 = table[Color.PINK, Category.MATERIAL_50]
  @JvmField val PINK_100 = table[Color.PINK, Category.MATERIAL_100]
  @JvmField val PINK_200 = table[Color.PINK, Category.MATERIAL_200]
  @JvmField val PINK_300 = table[Color.PINK, Category.MATERIAL_300]
  @JvmField val PINK_400 = table[Color.PINK, Category.MATERIAL_400]
  @JvmField val PINK_500 = table[Color.PINK, Category.MATERIAL_500]
  @JvmField val PINK_600 = table[Color.PINK, Category.MATERIAL_600]
  @JvmField val PINK_700 = table[Color.PINK, Category.MATERIAL_700]
  @JvmField val PINK_800 = table[Color.PINK, Category.MATERIAL_800]
  @JvmField val PINK_900 = table[Color.PINK, Category.MATERIAL_900]
  @JvmField val PURPLE_50 =  table[Color.PURPLE, Category.MATERIAL_50]
  @JvmField val PURPLE_100 = table[Color.PURPLE, Category.MATERIAL_100]
  @JvmField val PURPLE_200 = table[Color.PURPLE, Category.MATERIAL_200]
  @JvmField val PURPLE_300 = table[Color.PURPLE, Category.MATERIAL_300]
  @JvmField val PURPLE_400 = table[Color.PURPLE, Category.MATERIAL_400]
  @JvmField val PURPLE_500 = table[Color.PURPLE, Category.MATERIAL_500]
  @JvmField val PURPLE_600 = table[Color.PURPLE, Category.MATERIAL_600]
  @JvmField val PURPLE_700 = table[Color.PURPLE, Category.MATERIAL_700]
  @JvmField val PURPLE_800 = table[Color.PURPLE, Category.MATERIAL_800]
  @JvmField val PURPLE_900 = table[Color.PURPLE, Category.MATERIAL_900]
  @JvmField val DEEP_PURPLE_50 =  table[Color.DEEP_PURPLE, Category.MATERIAL_50]
  @JvmField val DEEP_PURPLE_100 = table[Color.DEEP_PURPLE, Category.MATERIAL_100]
  @JvmField val DEEP_PURPLE_200 = table[Color.DEEP_PURPLE, Category.MATERIAL_200]
  @JvmField val DEEP_PURPLE_300 = table[Color.DEEP_PURPLE, Category.MATERIAL_300]
  @JvmField val DEEP_PURPLE_400 = table[Color.DEEP_PURPLE, Category.MATERIAL_400]
  @JvmField val DEEP_PURPLE_500 = table[Color.DEEP_PURPLE, Category.MATERIAL_500]
  @JvmField val DEEP_PURPLE_600 = table[Color.DEEP_PURPLE, Category.MATERIAL_600]
  @JvmField val DEEP_PURPLE_700 = table[Color.DEEP_PURPLE, Category.MATERIAL_700]
  @JvmField val DEEP_PURPLE_800 = table[Color.DEEP_PURPLE, Category.MATERIAL_800]
  @JvmField val DEEP_PURPLE_900 = table[Color.DEEP_PURPLE, Category.MATERIAL_900]
  @JvmField val INDIGO_50 =  table[Color.INDIGO, Category.MATERIAL_50]
  @JvmField val INDIGO_100 = table[Color.INDIGO, Category.MATERIAL_100]
  @JvmField val INDIGO_200 = table[Color.INDIGO, Category.MATERIAL_200]
  @JvmField val INDIGO_300 = table[Color.INDIGO, Category.MATERIAL_300]
  @JvmField val INDIGO_400 = table[Color.INDIGO, Category.MATERIAL_400]
  @JvmField val INDIGO_500 = table[Color.INDIGO, Category.MATERIAL_500]
  @JvmField val INDIGO_600 = table[Color.INDIGO, Category.MATERIAL_600]
  @JvmField val INDIGO_700 = table[Color.INDIGO, Category.MATERIAL_700]
  @JvmField val INDIGO_800 = table[Color.INDIGO, Category.MATERIAL_800]
  @JvmField val INDIGO_900 = table[Color.INDIGO, Category.MATERIAL_900]
  @JvmField val BLUE_50 =  table[Color.BLUE, Category.MATERIAL_50]
  @JvmField val BLUE_100 = table[Color.BLUE, Category.MATERIAL_100]
  @JvmField val BLUE_200 = table[Color.BLUE, Category.MATERIAL_200]
  @JvmField val BLUE_300 = table[Color.BLUE, Category.MATERIAL_300]
  @JvmField val BLUE_400 = table[Color.BLUE, Category.MATERIAL_400]
  @JvmField val BLUE_500 = table[Color.BLUE, Category.MATERIAL_500]
  @JvmField val BLUE_600 = table[Color.BLUE, Category.MATERIAL_600]
  @JvmField val BLUE_700 = table[Color.BLUE, Category.MATERIAL_700]
  @JvmField val BLUE_800 = table[Color.BLUE, Category.MATERIAL_800]
  @JvmField val BLUE_900 = table[Color.BLUE, Category.MATERIAL_900]
  @JvmField val LIGHT_BLUE_50 =  table[Color.LIGHT_BLUE, Category.MATERIAL_50]
  @JvmField val LIGHT_BLUE_100 = table[Color.LIGHT_BLUE, Category.MATERIAL_100]
  @JvmField val LIGHT_BLUE_200 = table[Color.LIGHT_BLUE, Category.MATERIAL_200]
  @JvmField val LIGHT_BLUE_300 = table[Color.LIGHT_BLUE, Category.MATERIAL_300]
  @JvmField val LIGHT_BLUE_400 = table[Color.LIGHT_BLUE, Category.MATERIAL_400]
  @JvmField val LIGHT_BLUE_500 = table[Color.LIGHT_BLUE, Category.MATERIAL_500]
  @JvmField val LIGHT_BLUE_600 = table[Color.LIGHT_BLUE, Category.MATERIAL_600]
  @JvmField val LIGHT_BLUE_700 = table[Color.LIGHT_BLUE, Category.MATERIAL_700]
  @JvmField val LIGHT_BLUE_800 = table[Color.LIGHT_BLUE, Category.MATERIAL_800]
  @JvmField val LIGHT_BLUE_900 = table[Color.LIGHT_BLUE, Category.MATERIAL_900]
  @JvmField val CYAN_50 =  table[Color.CYAN, Category.MATERIAL_50]
  @JvmField val CYAN_100 = table[Color.CYAN, Category.MATERIAL_100]
  @JvmField val CYAN_200 = table[Color.CYAN, Category.MATERIAL_200]
  @JvmField val CYAN_300 = table[Color.CYAN, Category.MATERIAL_300]
  @JvmField val CYAN_400 = table[Color.CYAN, Category.MATERIAL_400]
  @JvmField val CYAN_500 = table[Color.CYAN, Category.MATERIAL_500]
  @JvmField val CYAN_600 = table[Color.CYAN, Category.MATERIAL_600]
  @JvmField val CYAN_700 = table[Color.CYAN, Category.MATERIAL_700]
  @JvmField val CYAN_800 = table[Color.CYAN, Category.MATERIAL_800]
  @JvmField val CYAN_900 = table[Color.CYAN, Category.MATERIAL_900]
  @JvmField val TEAL_50 = table[Color.TEAL, Category.MATERIAL_50]
  @JvmField val TEAL_100 = table[Color.TEAL, Category.MATERIAL_100]
  @JvmField val TEAL_200 = table[Color.TEAL, Category.MATERIAL_200]
  @JvmField val TEAL_300 = table[Color.TEAL, Category.MATERIAL_300]
  @JvmField val TEAL_400 = table[Color.TEAL, Category.MATERIAL_400]
  @JvmField val TEAL_500 = table[Color.TEAL, Category.MATERIAL_500]
  @JvmField val TEAL_600 = table[Color.TEAL, Category.MATERIAL_600]
  @JvmField val TEAL_700 = table[Color.TEAL, Category.MATERIAL_700]
  @JvmField val TEAL_800 = table[Color.TEAL, Category.MATERIAL_800]
  @JvmField val TEAL_900 = table[Color.TEAL, Category.MATERIAL_900]
  @JvmField val GREEN_50 = table[Color.GREEN, Category.MATERIAL_50]
  @JvmField val GREEN_100 = table[Color.GREEN, Category.MATERIAL_100]
  @JvmField val GREEN_200 = table[Color.GREEN, Category.MATERIAL_200]
  @JvmField val GREEN_300 = table[Color.GREEN, Category.MATERIAL_300]
  @JvmField val GREEN_400 = table[Color.GREEN, Category.MATERIAL_400]
  @JvmField val GREEN_500 = table[Color.GREEN, Category.MATERIAL_500]
  @JvmField val GREEN_600 = table[Color.GREEN, Category.MATERIAL_600]
  @JvmField val GREEN_700 = table[Color.GREEN, Category.MATERIAL_700]
  @JvmField val GREEN_800 = table[Color.GREEN, Category.MATERIAL_800]
  @JvmField val GREEN_900 = table[Color.GREEN, Category.MATERIAL_900]
  @JvmField val LIGHT_GREEN_50 = table[Color.LIGHT_GREEN, Category.MATERIAL_50]
  @JvmField val LIGHT_GREEN_100 = table[Color.LIGHT_GREEN, Category.MATERIAL_100]
  @JvmField val LIGHT_GREEN_200 = table[Color.LIGHT_GREEN, Category.MATERIAL_200]
  @JvmField val LIGHT_GREEN_300 = table[Color.LIGHT_GREEN, Category.MATERIAL_300]
  @JvmField val LIGHT_GREEN_400 = table[Color.LIGHT_GREEN, Category.MATERIAL_400]
  @JvmField val LIGHT_GREEN_500 = table[Color.LIGHT_GREEN, Category.MATERIAL_500]
  @JvmField val LIGHT_GREEN_600 = table[Color.LIGHT_GREEN, Category.MATERIAL_600]
  @JvmField val LIGHT_GREEN_700 = table[Color.LIGHT_GREEN, Category.MATERIAL_700]
  @JvmField val LIGHT_GREEN_800 = table[Color.LIGHT_GREEN, Category.MATERIAL_800]
  @JvmField val LIGHT_GREEN_900 = table[Color.LIGHT_GREEN, Category.MATERIAL_900]
  @JvmField val LIME_50 = table[Color.LIME, Category.MATERIAL_50]
  @JvmField val LIME_100 = table[Color.LIME, Category.MATERIAL_100]
  @JvmField val LIME_200 = table[Color.LIME, Category.MATERIAL_200]
  @JvmField val LIME_300 = table[Color.LIME, Category.MATERIAL_300]
  @JvmField val LIME_400 = table[Color.LIME, Category.MATERIAL_400]
  @JvmField val LIME_500 = table[Color.LIME, Category.MATERIAL_500]
  @JvmField val LIME_600 = table[Color.LIME, Category.MATERIAL_600]
  @JvmField val LIME_700 = table[Color.LIME, Category.MATERIAL_700]
  @JvmField val LIME_800 = table[Color.LIME, Category.MATERIAL_800]
  @JvmField val LIME_900 = table[Color.LIME, Category.MATERIAL_900]
  @JvmField val YELLOW_50 = table[Color.YELLOW, Category.MATERIAL_50]
  @JvmField val YELLOW_100 = table[Color.YELLOW, Category.MATERIAL_100]
  @JvmField val YELLOW_200 = table[Color.YELLOW, Category.MATERIAL_200]
  @JvmField val YELLOW_300 = table[Color.YELLOW, Category.MATERIAL_300]
  @JvmField val YELLOW_400 = table[Color.YELLOW, Category.MATERIAL_400]
  @JvmField val YELLOW_500 = table[Color.YELLOW, Category.MATERIAL_500]
  @JvmField val YELLOW_600 = table[Color.YELLOW, Category.MATERIAL_600]
  @JvmField val YELLOW_700 = table[Color.YELLOW, Category.MATERIAL_700]
  @JvmField val YELLOW_800 = table[Color.YELLOW, Category.MATERIAL_800]
  @JvmField val YELLOW_900 = table[Color.YELLOW, Category.MATERIAL_900]
  @JvmField val AMBER_50 = table[Color.AMBER, Category.MATERIAL_50]
  @JvmField val AMBER_100 = table[Color.AMBER, Category.MATERIAL_100]
  @JvmField val AMBER_200 = table[Color.AMBER, Category.MATERIAL_200]
  @JvmField val AMBER_300 = table[Color.AMBER, Category.MATERIAL_300]
  @JvmField val AMBER_400 = table[Color.AMBER, Category.MATERIAL_400]
  @JvmField val AMBER_500 = table[Color.AMBER, Category.MATERIAL_500]
  @JvmField val AMBER_600 = table[Color.AMBER, Category.MATERIAL_600]
  @JvmField val AMBER_700 = table[Color.AMBER, Category.MATERIAL_700]
  @JvmField val AMBER_800 = table[Color.AMBER, Category.MATERIAL_800]
  @JvmField val AMBER_900 = table[Color.AMBER, Category.MATERIAL_900]
  @JvmField val ORANGE_50 = table[Color.ORANGE, Category.MATERIAL_50]
  @JvmField val ORANGE_100 = table[Color.ORANGE, Category.MATERIAL_100]
  @JvmField val ORANGE_200 = table[Color.ORANGE, Category.MATERIAL_200]
  @JvmField val ORANGE_300 = table[Color.ORANGE, Category.MATERIAL_300]
  @JvmField val ORANGE_400 = table[Color.ORANGE, Category.MATERIAL_400]
  @JvmField val ORANGE_500 = table[Color.ORANGE, Category.MATERIAL_500]
  @JvmField val ORANGE_600 = table[Color.ORANGE, Category.MATERIAL_600]
  @JvmField val ORANGE_700 = table[Color.ORANGE, Category.MATERIAL_700]
  @JvmField val ORANGE_800 = table[Color.ORANGE, Category.MATERIAL_800]
  @JvmField val ORANGE_900 = table[Color.ORANGE, Category.MATERIAL_900]
  @JvmField val DEEP_ORANGE_50 = table[Color.DEEP_ORANGE, Category.MATERIAL_50]
  @JvmField val DEEP_ORANGE_100 = table[Color.DEEP_ORANGE, Category.MATERIAL_100]
  @JvmField val DEEP_ORANGE_200 = table[Color.DEEP_ORANGE, Category.MATERIAL_200]
  @JvmField val DEEP_ORANGE_300 = table[Color.DEEP_ORANGE, Category.MATERIAL_300]
  @JvmField val DEEP_ORANGE_400 = table[Color.DEEP_ORANGE, Category.MATERIAL_400]
  @JvmField val DEEP_ORANGE_500 = table[Color.DEEP_ORANGE, Category.MATERIAL_500]
  @JvmField val DEEP_ORANGE_600 = table[Color.DEEP_ORANGE, Category.MATERIAL_600]
  @JvmField val DEEP_ORANGE_700 = table[Color.DEEP_ORANGE, Category.MATERIAL_700]
  @JvmField val DEEP_ORANGE_800 = table[Color.DEEP_ORANGE, Category.MATERIAL_800]
  @JvmField val DEEP_ORANGE_900 = table[Color.DEEP_ORANGE, Category.MATERIAL_900]
  @JvmField val BROWN_50 = table[Color.BROWN, Category.MATERIAL_50]
  @JvmField val BROWN_100 = table[Color.BROWN, Category.MATERIAL_100]
  @JvmField val BROWN_200 = table[Color.BROWN, Category.MATERIAL_200]
  @JvmField val BROWN_300 = table[Color.BROWN, Category.MATERIAL_300]
  @JvmField val BROWN_400 = table[Color.BROWN, Category.MATERIAL_400]
  @JvmField val BROWN_500 = table[Color.BROWN, Category.MATERIAL_500]
  @JvmField val BROWN_600 = table[Color.BROWN, Category.MATERIAL_600]
  @JvmField val BROWN_700 = table[Color.BROWN, Category.MATERIAL_700]
  @JvmField val BROWN_800 = table[Color.BROWN, Category.MATERIAL_800]
  @JvmField val BROWN_900 = table[Color.BROWN, Category.MATERIAL_900]
  @JvmField val GREY_50 = table[Color.GREY, Category.MATERIAL_50]
  @JvmField val GREY_100 = table[Color.GREY, Category.MATERIAL_100]
  @JvmField val GREY_200 = table[Color.GREY, Category.MATERIAL_200]
  @JvmField val GREY_300 = table[Color.GREY, Category.MATERIAL_300]
  @JvmField val GREY_400 = table[Color.GREY, Category.MATERIAL_400]
  @JvmField val GREY_500 = table[Color.GREY, Category.MATERIAL_500]
  @JvmField val GREY_600 = table[Color.GREY, Category.MATERIAL_600]
  @JvmField val GREY_700 = table[Color.GREY, Category.MATERIAL_700]
  @JvmField val GREY_800 = table[Color.GREY, Category.MATERIAL_800]
  @JvmField val GREY_900 = table[Color.GREY, Category.MATERIAL_900]
  @JvmField val BLUE_GREY_50 = table[Color.BLUE_GREY, Category.MATERIAL_50]
  @JvmField val BLUE_GREY_100 = table[Color.BLUE_GREY, Category.MATERIAL_100]
  @JvmField val BLUE_GREY_200 = table[Color.BLUE_GREY, Category.MATERIAL_200]
  @JvmField val BLUE_GREY_300 = table[Color.BLUE_GREY, Category.MATERIAL_300]
  @JvmField val BLUE_GREY_400 = table[Color.BLUE_GREY, Category.MATERIAL_400]
  @JvmField val BLUE_GREY_500 = table[Color.BLUE_GREY, Category.MATERIAL_500]
  @JvmField val BLUE_GREY_600 = table[Color.BLUE_GREY, Category.MATERIAL_600]
  @JvmField val BLUE_GREY_700 = table[Color.BLUE_GREY, Category.MATERIAL_700]
  @JvmField val BLUE_GREY_800 = table[Color.BLUE_GREY, Category.MATERIAL_800]
  @JvmField val BLUE_GREY_900 = table[Color.BLUE_GREY, Category.MATERIAL_900]

  @JvmField val RED_ACCENT_100 = table[Color.RED, Category.MATERIAL_ACCENT_100]
  @JvmField val RED_ACCENT_200 = table[Color.RED, Category.MATERIAL_ACCENT_200]
  @JvmField val RED_ACCENT_400 = table[Color.RED, Category.MATERIAL_ACCENT_400]
  @JvmField val RED_ACCENT_700 = table[Color.RED, Category.MATERIAL_ACCENT_700]
  @JvmField val PINK_ACCENT_100 = table[Color.PINK, Category.MATERIAL_ACCENT_100]
  @JvmField val PINK_ACCENT_200 = table[Color.PINK, Category.MATERIAL_ACCENT_200]
  @JvmField val PINK_ACCENT_400 = table[Color.PINK, Category.MATERIAL_ACCENT_400]
  @JvmField val PINK_ACCENT_700 = table[Color.PINK, Category.MATERIAL_ACCENT_700]
  @JvmField val PURPLE_ACCENT_100 = table[Color.PURPLE, Category.MATERIAL_ACCENT_100]
  @JvmField val PURPLE_ACCENT_200 = table[Color.PURPLE, Category.MATERIAL_ACCENT_200]
  @JvmField val PURPLE_ACCENT_400 = table[Color.PURPLE, Category.MATERIAL_ACCENT_400]
  @JvmField val PURPLE_ACCENT_700 = table[Color.PURPLE, Category.MATERIAL_ACCENT_700]
  @JvmField val DEEP_PURPLE_ACCENT_100 = table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_100]
  @JvmField val DEEP_PURPLE_ACCENT_200 = table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_200]
  @JvmField val DEEP_PURPLE_ACCENT_400 = table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_400]
  @JvmField val DEEP_PURPLE_ACCENT_700 = table[Color.DEEP_PURPLE, Category.MATERIAL_ACCENT_700]
  @JvmField val INDIGO_ACCENT_100 = table[Color.INDIGO, Category.MATERIAL_ACCENT_100]
  @JvmField val INDIGO_ACCENT_200 = table[Color.INDIGO, Category.MATERIAL_ACCENT_200]
  @JvmField val INDIGO_ACCENT_400 = table[Color.INDIGO, Category.MATERIAL_ACCENT_400]
  @JvmField val INDIGO_ACCENT_700 = table[Color.INDIGO, Category.MATERIAL_ACCENT_700]
  @JvmField val BLUE_ACCENT_100 = table[Color.BLUE, Category.MATERIAL_ACCENT_100]
  @JvmField val BLUE_ACCENT_200 = table[Color.BLUE, Category.MATERIAL_ACCENT_200]
  @JvmField val BLUE_ACCENT_400 = table[Color.BLUE, Category.MATERIAL_ACCENT_400]
  @JvmField val BLUE_ACCENT_700 = table[Color.BLUE, Category.MATERIAL_ACCENT_700]
  @JvmField val LIGHT_BLUE_ACCENT_100 = table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_100]
  @JvmField val LIGHT_BLUE_ACCENT_200 = table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_200]
  @JvmField val LIGHT_BLUE_ACCENT_400 = table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_400]
  @JvmField val LIGHT_BLUE_ACCENT_700 = table[Color.LIGHT_BLUE, Category.MATERIAL_ACCENT_700]
  @JvmField val CYAN_ACCENT_100 = table[Color.CYAN, Category.MATERIAL_ACCENT_100]
  @JvmField val CYAN_ACCENT_200 = table[Color.CYAN, Category.MATERIAL_ACCENT_200]
  @JvmField val CYAN_ACCENT_400 = table[Color.CYAN, Category.MATERIAL_ACCENT_400]
  @JvmField val CYAN_ACCENT_700 = table[Color.CYAN, Category.MATERIAL_ACCENT_700]
  @JvmField val TEAL_ACCENT_100 = table[Color.TEAL, Category.MATERIAL_ACCENT_100]
  @JvmField val TEAL_ACCENT_200 = table[Color.TEAL, Category.MATERIAL_ACCENT_200]
  @JvmField val TEAL_ACCENT_400 = table[Color.TEAL, Category.MATERIAL_ACCENT_400]
  @JvmField val TEAL_ACCENT_700 = table[Color.TEAL, Category.MATERIAL_ACCENT_700]
  @JvmField val GREEN_ACCENT_100 = table[Color.GREEN, Category.MATERIAL_ACCENT_100]
  @JvmField val GREEN_ACCENT_200 = table[Color.GREEN, Category.MATERIAL_ACCENT_200]
  @JvmField val GREEN_ACCENT_400 = table[Color.GREEN, Category.MATERIAL_ACCENT_400]
  @JvmField val GREEN_ACCENT_700 = table[Color.GREEN, Category.MATERIAL_ACCENT_700]
  @JvmField val LIGHT_GREEN_ACCENT_100 = table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_100]
  @JvmField val LIGHT_GREEN_ACCENT_200 = table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_200]
  @JvmField val LIGHT_GREEN_ACCENT_400 = table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_400]
  @JvmField val LIGHT_GREEN_ACCENT_700 = table[Color.LIGHT_GREEN, Category.MATERIAL_ACCENT_700]
  @JvmField val LIME_ACCENT_100 = table[Color.LIME, Category.MATERIAL_ACCENT_100]
  @JvmField val LIME_ACCENT_200 = table[Color.LIME, Category.MATERIAL_ACCENT_200]
  @JvmField val LIME_ACCENT_400 = table[Color.LIME, Category.MATERIAL_ACCENT_400]
  @JvmField val LIME_ACCENT_700 = table[Color.LIME, Category.MATERIAL_ACCENT_700]
  @JvmField val YELLOW_ACCENT_100 = table[Color.YELLOW, Category.MATERIAL_ACCENT_100]
  @JvmField val YELLOW_ACCENT_200 = table[Color.YELLOW, Category.MATERIAL_ACCENT_200]
  @JvmField val YELLOW_ACCENT_400 = table[Color.YELLOW, Category.MATERIAL_ACCENT_400]
  @JvmField val YELLOW_ACCENT_700 = table[Color.YELLOW, Category.MATERIAL_ACCENT_700]
  @JvmField val AMBER_ACCENT_100 = table[Color.AMBER, Category.MATERIAL_ACCENT_100]
  @JvmField val AMBER_ACCENT_200 = table[Color.AMBER, Category.MATERIAL_ACCENT_200]
  @JvmField val AMBER_ACCENT_400 = table[Color.AMBER, Category.MATERIAL_ACCENT_400]
  @JvmField val AMBER_ACCENT_700 = table[Color.AMBER, Category.MATERIAL_ACCENT_700]
  @JvmField val ORANGE_ACCENT_100 = table[Color.ORANGE, Category.MATERIAL_ACCENT_100]
  @JvmField val ORANGE_ACCENT_200 = table[Color.ORANGE, Category.MATERIAL_ACCENT_200]
  @JvmField val ORANGE_ACCENT_400 = table[Color.ORANGE, Category.MATERIAL_ACCENT_400]
  @JvmField val ORANGE_ACCENT_700 = table[Color.ORANGE, Category.MATERIAL_ACCENT_700]
  @JvmField val DEEP_ORANGE_ACCENT_100 = table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_100]
  @JvmField val DEEP_ORANGE_ACCENT_200 = table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_200]
  @JvmField val DEEP_ORANGE_ACCENT_400 = table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_400]
  @JvmField val DEEP_ORANGE_ACCENT_700 = table[Color.DEEP_ORANGE, Category.MATERIAL_ACCENT_700]

  const val PRIMARY_MATERIAL_ATTR = "colorPrimary"
  const val PRIMARY_DARK_MATERIAL_ATTR = "colorPrimaryDark"
  const val ACCENT_MATERIAL_ATTR = "colorAccent"
}
