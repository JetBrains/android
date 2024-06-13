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
package com.android.tools.idea.uibuilder.handlers

import com.android.AndroidXConstants.ACTION_MENU_VIEW
import com.android.AndroidXConstants.APP_BAR_LAYOUT
import com.android.AndroidXConstants.BOTTOM_NAVIGATION_VIEW
import com.android.AndroidXConstants.BROWSE_FRAGMENT
import com.android.AndroidXConstants.CARD_VIEW
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_CHAIN
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_HELPER
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_LAYER
import com.android.AndroidXConstants.COLLAPSING_TOOLBAR_LAYOUT
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE
import com.android.AndroidXConstants.COORDINATOR_LAYOUT
import com.android.AndroidXConstants.DETAILS_FRAGMENT
import com.android.AndroidXConstants.DRAWER_LAYOUT
import com.android.AndroidXConstants.FLOATING_ACTION_BUTTON
import com.android.AndroidXConstants.GRID_LAYOUT_V7
import com.android.AndroidXConstants.MOTION_LAYOUT
import com.android.AndroidXConstants.NAVIGATION_VIEW
import com.android.AndroidXConstants.NESTED_SCROLL_VIEW
import com.android.AndroidXConstants.PLAYBACK_OVERLAY_FRAGMENT
import com.android.AndroidXConstants.RECYCLER_VIEW
import com.android.AndroidXConstants.SEARCH_FRAGMENT
import com.android.AndroidXConstants.SNACKBAR
import com.android.AndroidXConstants.TABLE_CONSTRAINT_LAYOUT
import com.android.AndroidXConstants.TAB_ITEM
import com.android.AndroidXConstants.TAB_LAYOUT
import com.android.AndroidXConstants.TEXT_INPUT_LAYOUT
import com.android.AndroidXConstants.TOOLBAR_V7
import com.android.AndroidXConstants.VIEW_PAGER
import com.android.SdkConstants.ABSOLUTE_LAYOUT
import com.android.SdkConstants.ABS_LIST_VIEW
import com.android.SdkConstants.ADAPTER_VIEW
import com.android.SdkConstants.ADAPTER_VIEW_ANIMATOR
import com.android.SdkConstants.ADAPTER_VIEW_FLIPPER
import com.android.SdkConstants.AD_VIEW
import com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.BOTTOM_APP_BAR
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.CHECKED_TEXT_VIEW
import com.android.SdkConstants.CHECK_BOX
import com.android.SdkConstants.CHIP
import com.android.SdkConstants.CHIP_GROUP
import com.android.SdkConstants.CHRONOMETER
import com.android.SdkConstants.DIALER_FILTER
import com.android.SdkConstants.EDIT_TEXT
import com.android.SdkConstants.EXPANDABLE_LIST_VIEW
import com.android.SdkConstants.FLEXBOX_LAYOUT
import com.android.SdkConstants.FQCN_LINEAR_LAYOUT
import com.android.SdkConstants.FQCN_RELATIVE_LAYOUT
import com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.GESTURE_OVERLAY_VIEW
import com.android.SdkConstants.GRID_LAYOUT
import com.android.SdkConstants.GRID_VIEW
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.IMAGE_BUTTON
import com.android.SdkConstants.IMAGE_SWITCHER
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.MAP_VIEW
import com.android.SdkConstants.MATERIAL_BUTTON
import com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.PROGRESS_BAR
import com.android.SdkConstants.PreferenceTags
import com.android.SdkConstants.QUICK_CONTACT_BADGE
import com.android.SdkConstants.RADIO_BUTTON
import com.android.SdkConstants.RATING_BAR
import com.android.SdkConstants.RELATIVE_LAYOUT
import com.android.SdkConstants.REQUEST_FOCUS
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.SEARCH_VIEW
import com.android.SdkConstants.SEEK_BAR
import com.android.SdkConstants.SPACE
import com.android.SdkConstants.SPINNER
import com.android.SdkConstants.STACK_VIEW
import com.android.SdkConstants.SURFACE_VIEW
import com.android.SdkConstants.SWITCH
import com.android.SdkConstants.TABLE_LAYOUT
import com.android.SdkConstants.TABLE_ROW
import com.android.SdkConstants.TAB_HOST
import com.android.SdkConstants.TAG_GROUP
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TAG_MENU
import com.android.SdkConstants.TAG_SELECTOR
import com.android.SdkConstants.TEXTURE_VIEW
import com.android.SdkConstants.TEXT_CLOCK
import com.android.SdkConstants.TEXT_SWITCHER
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.TOGGLE_BUTTON
import com.android.SdkConstants.VIDEO_VIEW
import com.android.SdkConstants.VIEW
import com.android.SdkConstants.VIEW_ANIMATOR
import com.android.SdkConstants.VIEW_FLIPPER
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_GROUP
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_MERGE
import com.android.SdkConstants.VIEW_PAGER2
import com.android.SdkConstants.VIEW_STUB
import com.android.SdkConstants.VIEW_SWITCHER
import com.android.SdkConstants.VIEW_TAG
import com.android.SdkConstants.WEB_VIEW
import com.android.SdkConstants.ZOOM_BUTTON
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.handlers.absolute.AbsoluteLayoutHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintHelperHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutBarrierHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutChainHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutFlowHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutGuidelineHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutLayerHandler
import com.android.tools.idea.uibuilder.handlers.coordinator.CoordinatorLayoutHandler
import com.android.tools.idea.uibuilder.handlers.flexbox.FlexboxLayoutHandler
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler
import com.android.tools.idea.uibuilder.handlers.google.AdViewHandler
import com.android.tools.idea.uibuilder.handlers.google.MapViewHandler
import com.android.tools.idea.uibuilder.handlers.grid.GridLayoutHandler
import com.android.tools.idea.uibuilder.handlers.grid.GridLayoutV7Handler
import com.android.tools.idea.uibuilder.handlers.leanback.BrowseFragmentHandler
import com.android.tools.idea.uibuilder.handlers.leanback.DetailsFragmentHandler
import com.android.tools.idea.uibuilder.handlers.leanback.PlaybackOverlayFragmentHandler
import com.android.tools.idea.uibuilder.handlers.leanback.SearchFragmentHandler
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutHandler
import com.android.tools.idea.uibuilder.handlers.preference.CheckBoxPreferenceHandler
import com.android.tools.idea.uibuilder.handlers.preference.EditTextPreferenceHandler
import com.android.tools.idea.uibuilder.handlers.preference.ListPreferenceHandler
import com.android.tools.idea.uibuilder.handlers.preference.MultiSelectListPreferenceHandler
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceCategoryHandler
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceScreenHandler
import com.android.tools.idea.uibuilder.handlers.preference.RingtonePreferenceHandler
import com.android.tools.idea.uibuilder.handlers.preference.SwitchPreferenceHandler
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler
import com.android.tools.idea.uibuilder.menu.GroupHandler
import com.android.tools.idea.uibuilder.menu.MenuHandler
import com.android.tools.idea.uibuilder.statelist.SelectorHandler

private val STANDARD_HANDLER = ViewHandler()
private val TEXT_HANDLER = TextViewHandler()
private val NO_PREVIEW_HANDLER = NoPreviewHandler()

private object BasicViewHandlerProvider : ViewHandlerProvider {
  override fun findHandler(viewTag: String): ViewHandler? =
    when (viewTag) {
      ABSOLUTE_LAYOUT,
      WEB_VIEW -> AbsoluteLayoutHandler()
      ABS_LIST_VIEW,
      ADAPTER_VIEW_ANIMATOR,
      ADAPTER_VIEW_FLIPPER,
      GRID_VIEW,
      VIEW_GROUP -> ViewGroupHandler()
      ADAPTER_VIEW,
      STACK_VIEW -> AdapterViewHandler()
      AD_VIEW -> AdViewHandler()
      AUTO_COMPLETE_TEXT_VIEW -> AutoCompleteTextViewHandler()
      BOTTOM_APP_BAR -> BottomAppBarHandler()
      BUTTON,
      MATERIAL_BUTTON -> ButtonHandler()
      CHECKED_TEXT_VIEW -> CheckedTextViewHandler()
      CHECK_BOX,
      RADIO_BUTTON -> CheckBoxHandler()
      CHIP -> ChipHandler()
      CHIP_GROUP -> ChipGroupHandler()
      CHRONOMETER -> ChronometerHandler()
      DIALER_FILTER,
      FQCN_RELATIVE_LAYOUT,
      RELATIVE_LAYOUT -> RelativeLayoutHandler()
      EDIT_TEXT -> EditTextHandler()
      EXPANDABLE_LIST_VIEW ->
        ListViewHandler() // TODO: Find out why this fails to load by class name
      FQCN_LINEAR_LAYOUT,
      LINEAR_LAYOUT,
      SEARCH_VIEW -> LinearLayoutHandler()
      FRAME_LAYOUT,
      GESTURE_OVERLAY_VIEW,
      TEXT_SWITCHER,
      VIEW_ANIMATOR,
      VIEW_FLIPPER,
      VIEW_SWITCHER -> FrameLayoutHandler()
      GRID_LAYOUT -> GridLayoutHandler()
      HORIZONTAL_SCROLL_VIEW -> HorizontalScrollViewHandler()
      IMAGE_BUTTON -> ImageButtonHandler()
      IMAGE_SWITCHER -> ImageSwitcherHandler()
      IMAGE_VIEW,
      QUICK_CONTACT_BADGE -> ImageViewHandler()
      MAP_VIEW -> MapViewHandler()
      MULTI_AUTO_COMPLETE_TEXT_VIEW,
      TEXT_VIEW -> TEXT_HANDLER
      PROGRESS_BAR -> ProgressBarHandler()
      RATING_BAR -> RatingBarHandler()
      REQUEST_FOCUS -> RequestFocusHandler()
      SCROLL_VIEW -> ScrollViewHandler()
      SEEK_BAR -> SeekBarHandler()
      SPACE -> SpaceHandler()
      SPINNER -> SpinnerHandler()
      SURFACE_VIEW,
      TEXTURE_VIEW,
      VIDEO_VIEW -> NO_PREVIEW_HANDLER
      SWITCH -> SwitchHandler()
      TABLE_LAYOUT -> TableLayoutHandler()
      TABLE_ROW -> TableRowHandler()
      TAB_HOST -> TabHostHandler()
      TAG_GROUP -> GroupHandler()
      TAG_LAYOUT -> LayoutHandler()
      TAG_MENU -> MenuHandler()
      TAG_SELECTOR -> SelectorHandler()
      TEXT_CLOCK -> STANDARD_HANDLER
      TOGGLE_BUTTON -> ToggleButtonHandler()
      VIEW -> STANDARD_HANDLER
      VIEW_FRAGMENT -> FragmentHandler()
      VIEW_INCLUDE -> IncludeHandler()
      VIEW_MERGE -> MergeHandler()
      VIEW_STUB -> ViewStubHandler()
      VIEW_TAG -> ViewTagHandler()
      ZOOM_BUTTON -> ZoomButtonHandler()
      else -> null
    }
}

private object PreferencesViewHandlerProvider : ViewHandlerProvider {
  override fun findHandler(viewTag: String): ViewHandler? =
    when (viewTag) {
      PreferenceTags.CHECK_BOX_PREFERENCE -> CheckBoxPreferenceHandler()
      PreferenceTags.EDIT_TEXT_PREFERENCE -> EditTextPreferenceHandler()
      PreferenceTags.LIST_PREFERENCE -> ListPreferenceHandler()
      PreferenceTags.MULTI_SELECT_LIST_PREFERENCE -> MultiSelectListPreferenceHandler()
      PreferenceTags.PREFERENCE_CATEGORY -> PreferenceCategoryHandler()
      PreferenceTags.PREFERENCE_SCREEN -> PreferenceScreenHandler()
      PreferenceTags.RINGTONE_PREFERENCE -> RingtonePreferenceHandler()
      PreferenceTags.SWITCH_PREFERENCE -> SwitchPreferenceHandler()
      PreferenceTags.SWITCH_PREFERENCE_COMPAT -> SwitchPreferenceHandler()
      else -> null
    }
}

private object AndroidxViewHandlerProvider : ViewHandlerProvider {
  override fun findHandler(viewTag: String): ViewHandler? =
    when {
      ACTION_MENU_VIEW.isEquals(viewTag) -> ActionMenuViewHandler()
      APP_BAR_LAYOUT.isEquals(viewTag) -> AppBarLayoutHandler()
      BOTTOM_NAVIGATION_VIEW.isEquals(viewTag) -> BottomNavigationViewHandler()
      BROWSE_FRAGMENT.isEquals(viewTag) -> BrowseFragmentHandler()
      CARD_VIEW.isEquals(viewTag) -> CardViewHandler()
      CLASS_CONSTRAINT_LAYOUT_BARRIER.isEquals(viewTag) -> ConstraintLayoutBarrierHandler()
      CLASS_CONSTRAINT_LAYOUT_CHAIN.isEquals(viewTag) -> ConstraintLayoutChainHandler()
      CLASS_CONSTRAINT_LAYOUT_HELPER.isEquals(viewTag) -> ConstraintHelperHandler()
      CLASS_CONSTRAINT_LAYOUT_LAYER.isEquals(viewTag) -> ConstraintLayoutLayerHandler()
      CLASS_CONSTRAINT_LAYOUT_FLOW.isEquals(viewTag) -> ConstraintLayoutFlowHandler()
      COLLAPSING_TOOLBAR_LAYOUT.isEquals(viewTag) -> CollapsingToolbarLayoutHandler()
      CONSTRAINT_LAYOUT_GUIDELINE.isEquals(viewTag) -> ConstraintLayoutGuidelineHandler()
      CONSTRAINT_LAYOUT.isEquals(viewTag) -> ConstraintLayoutHandler()
      COORDINATOR_LAYOUT.isEquals(viewTag) -> CoordinatorLayoutHandler()
      DETAILS_FRAGMENT.isEquals(viewTag) -> DetailsFragmentHandler()
      DRAWER_LAYOUT.isEquals(viewTag) -> DrawerLayoutHandler()
      FLOATING_ACTION_BUTTON.isEquals(viewTag) -> FloatingActionButtonHandler()
      GRID_LAYOUT_V7.isEquals(viewTag) -> GridLayoutV7Handler()
      MOTION_LAYOUT.isEquals(viewTag) -> MotionLayoutHandler()
      NAVIGATION_VIEW.isEquals(viewTag) -> NavigationViewHandler()
      NESTED_SCROLL_VIEW.isEquals(viewTag) -> NestedScrollViewHandler()
      PLAYBACK_OVERLAY_FRAGMENT.isEquals(viewTag) -> PlaybackOverlayFragmentHandler()
      RECYCLER_VIEW.isEquals(viewTag) -> RecyclerViewHandler()
      SEARCH_FRAGMENT.isEquals(viewTag) -> SearchFragmentHandler()
      SNACKBAR.isEquals(viewTag) -> STANDARD_HANDLER
      TAB_ITEM.isEquals(viewTag) -> TabItemHandler()
      TAB_LAYOUT.isEquals(viewTag) -> TabLayoutHandler()
      TABLE_CONSTRAINT_LAYOUT.isEquals(viewTag) -> ConstraintLayoutHandler()
      TEXT_INPUT_LAYOUT.isEquals(viewTag) -> TextInputLayoutHandler()
      TOOLBAR_V7.isEquals(viewTag) -> ToolbarHandler()
      VIEW_PAGER.isEquals(viewTag) -> ViewPagerHandler()
      // ViewPager2 only exists on the androidx namespace so it does not need isEquals to check both
      // old and new names.
      VIEW_PAGER2 == viewTag -> ViewPager2Handler()
      // FragmentContainerView only exists  on the androidx namespace
      FRAGMENT_CONTAINER_VIEW == viewTag -> FragmentContainerViewHandler()
      else -> null
    }
}

private object FlexboxViewHandlerProvider : ViewHandlerProvider {
  override fun findHandler(viewTag: String): ViewHandler? =
    if (FLEXBOX_LAYOUT == viewTag) {
      if (FlexboxLayoutHandler.FLEXBOX_ENABLE_FLAG) FlexboxLayoutHandler()
      else ViewHandlerManager.NONE
    } else {
      null
    }
}

internal object BuiltinViewHandlerProvider : ViewHandlerProvider {
  private val providers =
    sequenceOf(
      BasicViewHandlerProvider,
      PreferencesViewHandlerProvider,
      AndroidxViewHandlerProvider,
      FlexboxViewHandlerProvider,
    )

  override fun findHandler(viewTag: String): ViewHandler? =
    providers.map { it.findHandler(viewTag) }.filterNotNull().firstOrNull()
}
