/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose

/**
 * The default RGB values of IntelliJ Int UI's color palette. Used as typed fallbacks for
 * theme lookups.
 */
@Suppress("unused") // Just a reference, we don't need to actually use all these
object IntUiPaletteDefaults {

  // Derived from platform/platform-resources/src/themes/expUI/expUI_dark.theme.json
  object Dark {
    const val Gray1 = 0xFF1E1F22.toInt()
    const val Gray2 = 0xFF2B2D30.toInt()
    const val Gray3 = 0xFF393B40.toInt()
    const val Gray4 = 0xFF43454A.toInt()
    const val Gray5 = 0xFF4E5157.toInt()
    const val Gray6 = 0xFF5A5D63.toInt()
    const val Gray7 = 0xFF6F737A.toInt()
    const val Gray8 = 0xFF868A91.toInt()
    const val Gray9 = 0xFF9DA0A8.toInt()
    const val Gray10 = 0xFFB4B8BF.toInt()
    const val Gray11 = 0xFFCED0D6.toInt()
    const val Gray12 = 0xFFDFE1E5.toInt()
    const val Gray13 = 0xFFF0F1F2.toInt()
    const val Gray14 = 0xFFFFFFFF.toInt()

    const val Blue1 = 0xFF25324D.toInt()
    const val Blue2 = 0xFF2E436E.toInt()
    const val Blue3 = 0xFF35538F.toInt()
    const val Blue4 = 0xFF375FAD.toInt()
    const val Blue5 = 0xFF366ACE.toInt()
    const val Blue6 = 0xFF3574F0.toInt()
    const val Blue7 = 0xFF467FF2.toInt()
    const val Blue8 = 0xFF548AF7.toInt()
    const val Blue9 = 0xFF6B9BFA.toInt()
    const val Blue10 = 0xFF83ACFC.toInt()
    const val Blue11 = 0xFF99BBFF.toInt()

    const val Green1 = 0xFF253627.toInt()
    const val Green2 = 0xFF273828.toInt()
    const val Green3 = 0xFF375239.toInt()
    const val Green4 = 0xFF436946.toInt()
    const val Green5 = 0xFF4E8052.toInt()
    const val Green6 = 0xFF57965C.toInt()
    const val Green7 = 0xFF5FAD65.toInt()
    const val Green8 = 0xFF73BD79.toInt()
    const val Green9 = 0xFF89CC8E.toInt()
    const val Green10 = 0xFFA0DBA5.toInt()
    const val Green11 = 0xFFB9EBBD.toInt()
    const val Green12 = 0xFFD4FAD7.toInt()

    const val Yellow1 = 0xFF3D3223.toInt()
    const val Yellow2 = 0xFF5E4D33.toInt()
    const val Yellow3 = 0xFF826A41.toInt()
    const val Yellow4 = 0xFF9E814A.toInt()
    const val Yellow5 = 0xFFBA9752.toInt()
    const val Yellow6 = 0xFFD6AE58.toInt()
    const val Yellow7 = 0xFFF2C55C.toInt()
    const val Yellow8 = 0xFFF5D273.toInt()
    const val Yellow9 = 0xFFF7DE8B.toInt()
    const val Yellow10 = 0xFFFCEBA4.toInt()
    const val Yellow11 = 0xFFFFF6BD.toInt()

    const val Red1 = 0xFF402929.toInt()
    const val Red2 = 0xFF472B2B.toInt()
    const val Red3 = 0xFF5E3838.toInt()
    const val Red4 = 0xFF7A4343.toInt()
    const val Red5 = 0xFF9C4E4E.toInt()
    const val Red6 = 0xFFBD5757.toInt()
    const val Red7 = 0xFFDB5C5C.toInt()
    const val Red8 = 0xFFE37774.toInt()
    const val Red9 = 0xFFEB938D.toInt()
    const val Red10 = 0xFFF2B1AA.toInt()
    const val Red11 = 0xFFF7CCC6.toInt()
    const val Red12 = 0xFFFAE3DE.toInt()

    const val Orange1 = 0xFF45322B.toInt()
    const val Orange2 = 0xFF614438.toInt()
    const val Orange3 = 0xFF825845.toInt()
    const val Orange4 = 0xFFA36B4E.toInt()
    const val Orange5 = 0xFFC27A53.toInt()
    const val Orange6 = 0xFFE08855.toInt()
    const val Orange7 = 0xFFE5986C.toInt()
    const val Orange8 = 0xFFF0AC81.toInt()
    const val Orange9 = 0xFFF5BD98.toInt()
    const val Orange10 = 0xFFFACEAF.toInt()
    const val Orange11 = 0xFFFFDFC7.toInt()

    const val Purple1 = 0xFF2F2936.toInt()
    const val Purple2 = 0xFF3B3147.toInt()
    const val Purple3 = 0xFF433358.toInt()
    const val Purple4 = 0xFF583D7A.toInt()
    const val Purple5 = 0xFF6C469C.toInt()
    const val Purple6 = 0xFF8150BE.toInt()
    const val Purple7 = 0xFF955AE0.toInt()
    const val Purple8 = 0xFFA571E6.toInt()
    const val Purple9 = 0xFFB589EC.toInt()
    const val Purple10 = 0xFFC4A0F3.toInt()
    const val Purple11 = 0xFFD4B8F9.toInt()
    const val Purple12 = 0xFFE4CEFF.toInt()

    const val Teal1 = 0xFF1D3838.toInt()
    const val Teal2 = 0xFF1D3D3B.toInt()
    const val Teal3 = 0xFF1E4D4A.toInt()
    const val Teal4 = 0xFF20635D.toInt()
    const val Teal5 = 0xFF21786F.toInt()
    const val Teal6 = 0xFF238E82.toInt()
    const val Teal7 = 0xFF24A394.toInt()
    const val Teal8 = 0xFF42B1A4.toInt()
    const val Teal9 = 0xFF60C0B5.toInt()
    const val Teal10 = 0xFF7DCEC5.toInt()
    const val Teal11 = 0xFF9BDDD6.toInt()
    const val Teal12 = 0xFFB9EBE6.toInt()
  }

  // Derived from platform/platform-resources/src/themes/expUI/expUI_light.theme.json
  object Light {
    const val Gray1 = 0xFF000000.toInt()
    const val Gray2 = 0xFF27282E.toInt()
    const val Gray3 = 0xFF383A42.toInt()
    const val Gray4 = 0xFF494B57.toInt()
    const val Gray5 = 0xFF5A5D6B.toInt()
    const val Gray6 = 0xFF6C707E.toInt()
    const val Gray7 = 0xFF818594.toInt()
    const val Gray8 = 0xFFA8ADBD.toInt()
    const val Gray9 = 0xFFC9CCD6.toInt()
    const val Gray10 = 0xFFD3D5DB.toInt()
    const val Gray11 = 0xFFDFE1E5.toInt()
    const val Gray12 = 0xFFEBECF0.toInt()
    const val Gray13 = 0xFFF7F8FA.toInt()
    const val Gray14 = 0xFFFFFFFF.toInt()

    const val Blue1 = 0xFF2E55A3.toInt()
    const val Blue2 = 0xFF315FBD.toInt()
    const val Blue3 = 0xFF3369D6.toInt()
    const val Blue4 = 0xFF3574F0.toInt()
    const val Blue5 = 0xFF4682FA.toInt()
    const val Blue6 = 0xFF588CF3.toInt()
    const val Blue7 = 0xFF709CF5.toInt()
    const val Blue8 = 0xFF88ADF7.toInt()
    const val Blue9 = 0xFFA0BDF8.toInt()
    const val Blue10 = 0xFFC2D6FC.toInt()
    const val Blue11 = 0xFFD4E2FF.toInt()
    const val Blue12 = 0xFFEDF3FF.toInt()
    const val Blue13 = 0xFFF5F8FE.toInt()

    const val Green1 = 0xFF1E6B33.toInt()
    const val Green2 = 0xFF1F7536.toInt()
    const val Green3 = 0xFF1F8039.toInt()
    const val Green4 = 0xFF208A3C.toInt()
    const val Green5 = 0xFF369650.toInt()
    const val Green6 = 0xFF55A76A.toInt()
    const val Green7 = 0xFF89C398.toInt()
    const val Green8 = 0xFFAFDBB8.toInt()
    const val Green9 = 0xFFC5E5CC.toInt()
    const val Green10 = 0xFFE3F7E7.toInt()
    const val Green11 = 0xFFF2FCF3.toInt()

    const val Yellow1 = 0xFFA46704.toInt()
    const val Yellow2 = 0xFFC27D04.toInt()
    const val Yellow3 = 0xFFDF9303.toInt()
    const val Yellow4 = 0xFFFFAF0F.toInt()
    const val Yellow5 = 0xFFFDBD3D.toInt()
    const val Yellow6 = 0xFFFED277.toInt()
    const val Yellow7 = 0xFFFEE6B1.toInt()
    const val Yellow8 = 0xFFFFF1D1.toInt()
    const val Yellow9 = 0xFFFFF5DB.toInt()
    const val Yellow10 = 0xFFFFFAEB.toInt()

    const val Red1 = 0xFFAD2B38.toInt()
    const val Red2 = 0xFFBC303E.toInt()
    const val Red3 = 0xFFCC3645.toInt()
    const val Red4 = 0xFFDB3B4B.toInt()
    const val Red5 = 0xFFE55765.toInt()
    const val Red6 = 0xFFE46A76.toInt()
    const val Red7 = 0xFFED99A1.toInt()
    const val Red8 = 0xFFF2B6BB.toInt()
    const val Red9 = 0xFFFAD4D8.toInt()
    const val Red10 = 0xFFFFEBEC.toInt()
    const val Red11 = 0xFFFFF2F3.toInt()
    const val Red12 = 0xFFFFF7F7.toInt()

    const val Orange1 = 0xFFA14916.toInt()
    const val Orange2 = 0xFFB85516.toInt()
    const val Orange3 = 0xFFCE6117.toInt()
    const val Orange4 = 0xFFE56D17.toInt()
    const val Orange5 = 0xFFEC8F4C.toInt()
    const val Orange6 = 0xFFF2B181.toInt()
    const val Orange7 = 0xFFF9D2B6.toInt()
    const val Orange8 = 0xFFFFEFE3.toInt()
    const val Orange9 = 0xFFFFF4EB.toInt()

    const val Teal1 = 0xFF096A6E.toInt()
    const val Teal2 = 0xFF077A7F.toInt()
    const val Teal3 = 0xFF058B90.toInt()
    const val Teal4 = 0xFF039BA1.toInt()
    const val Teal5 = 0xFF3FB3B8.toInt()
    const val Teal6 = 0xFF7BCCCF.toInt()
    const val Teal7 = 0xFFB6E4E5.toInt()
    const val Teal8 = 0xFFDAF4F5.toInt()
    const val Teal9 = 0xFFF2FCFC.toInt()

    const val Purple1 = 0xFF55339C.toInt()
    const val Purple2 = 0xFF643CB8.toInt()
    const val Purple3 = 0xFF7444D4.toInt()
    const val Purple4 = 0xFF834DF0.toInt()
    const val Purple5 = 0xFFA177F4.toInt()
    const val Purple6 = 0xFFBFA1F8.toInt()
    const val Purple7 = 0xFFDCCBFB.toInt()
    const val Purple8 = 0xFFEFE5FF.toInt()
    const val Purple9 = 0xFFF5EDFF.toInt()
    const val Purple10 = 0xFFFAF5FF.toInt()
  }
}