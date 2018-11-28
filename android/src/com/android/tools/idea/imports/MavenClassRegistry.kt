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
package com.android.tools.idea.imports

/** Lookup from key class names to well known maven.google.com artifacts */
object MavenClassRegistry {
  /**
   * Given a simple class name, return the likely groupid:artifactid for the maven.google.com
   * artifact containing that class.
   *
   * This implementation only returns results for a few important classes, not an exhaustive search
   * from a full index.
   *
   * NOTE: This library should be returning the pre-AndroidX library names when applicable; the caller is
   * responsible for mapping this to the corresponding AndroidX library artifact if the project is
   * using it. (The reason for this is practical: we have an artifact map to map forwards, not backwards.)
   */
  fun findArtifact(className: String): String? {
    return when (className) {
      "Nullable",
      "NonNull" ->
        "com.android.support:support-annotations"

      // AndroidX libraries

      "RecyclerView",
      "LinearLayoutManager",
      "DiffUtil" ->
        "com.android.support:recyclerview-v7"

      "NavHost",
      "NavController" ->
        "android.arch.navigation:navigation-runtime"

      "NavHostFragment" ->
        "android.arch.navigation:navigation-fragment"

      "TextClassifier" ->
        "com.android.support:textclassifier"

      "PrintHelper" ->
        "com.android.support:print"

      "CoordinatorLayout" ->
        "com.android.support:coordinatorlayout"

      "MediaPlayer" ->
        "com.android.support:media2"

      "ExoPlayer",
      "ExoPlayerFactory" ->
        "com.android.support:media2-exoplayer"

      "Loader",
      "CursorLoader",
      "AsyncTaskLoader" ->
        "com.android.support:loader"

      "DrawerLayout" ->
        "com.android.support:drawerlayout"

      "SupportSQLiteQuery",
      "SupportSQLiteQueryBuilder",
      "SupportSQLiteOpenHelper",
      "SupportSQLiteDatabase" ->
        "android.arch.persistence:db"

      "Palette" ->
        "com.android.support:palette-v7"

      "WebViewCompat" ->
        "com.android.support:webkit"

      "Transition" ->
        "com.android.support:transition"

      "VersionedParcel",
      "VersionedParcelable" ->
        "com.android.support:versionedparcelable"

      "ViewPager" -> "com.android.support:viewpager"

      "SwipeRefreshLayout" -> "com.android.support:swiperefreshlayout"

      "MediaRouter",
      "MediaRouteButton",
      "MediaRouteActionProvider" ->
        "com.android.support:mediarouter-v7"

      "FingerprintDialogFragment",
      "BiometricPrompt",
      "BiometricFragment" ->
        "androidx.biometric:biometric"

      "HeifWriter", "HeifEncoder" ->
        "com.android.support:heifwriter"

      "ExifInterface" ->
        "com.android.support:exifinterface"

      "PagedList",
      "PagedListBuilder",
      "PagedListAdapter" -> "android.arch.paging:runtime"

      "VectorDrawableCompat", "PathInterpolatorCompat" ->
        "com.android.support:support-vector-drawable"

      "AnimatedVectorDrawableCompat" ->
        "com.android.support:animated-vector-drawable"

      "ItemKeyProvider",
      "ItemDetailsLookup",
      "MutableSelection",
      "Selection",
      "SelectionTracker",
      "StorageStrategy" ->
        "com.android.support:recyclerview-selection"

      "LiveData",
      "MutableLiveData",
      "Observer",
      "ComputableLiveData" ->
        "android.arch.lifecycle:livedata"

      "ViewModelProviders" ->
        "android.arch.lifecycle:extensions"

      "ViewModel",
      "ViewModelProvider" ->
        "android.arch.lifecycle:viewmodel"

      "ProcessLifecycleOwner." ->
        "androidx.lifecycle:lifecycle-process"

      "ConstraintLayout" -> "com.android.support.constraint:constraint-layout"

      "LocalBroadcastManager" -> "com.android.support:localbroadcastmanager"

      "Lifecycle" -> "android.arch.lifecycle:common"

      "ContentRecommendation" -> "com.android.support:recommendation"

      "Preference",
      "ListPreference",
      "PreferenceGroup",
      "PreferenceGroupAdapter",
      "PreferenceScreen",
      "PreferenceViewHolder" ->
        "com.android.support:preference-v7"

      "AppCompatActivity",
      "Toolbar" ->
        "com.android.support:appcompat-v7"

      "Data",
      "WorkManager" -> "android.arch.work:work-runtime"

      "RxWorker" -> "android.arch.work:work-rxjava2"

      "CardView" -> "com.android.support:cardview-v7"

      "ContentPager" -> "com.android.support:support-content"

      "CursorAdapter", "SimpleCursorAdapter", "CursorFilter" ->
        "com.android.support:cursoradapter"

      "DocumentFile",
      "RawDocumentFile",
      "SingleDocumentFile",
      "TreeDocumentFile" ->
        "com.android.support:documentfile"

      "EmojiCompat",
      "EmojiButton",
      "EmojiTextView",
      "FontRequestEmojiCompatConfig" ->
        "com.android.support:support-emoji"

      "DialogFragment",
      "Fragment",
      "ListFragment",
      "FragmentManager",
      "FragmentActivity",
      "FragmentTransaction" -> "androidx.fragment:fragment"

      "GridLayout" -> "com.android.support:gridlayout-v7"

      "FastOutLinearInInterpolator",
      "FastOutSlowInInterpolator",
      "LinearOutSlowInInterpolator",
      "LookupTableInterpolator" ->
        "com.android.support:interpolator"

      "Query",
      "Entity",
      "Dao",
      "Insert",
      "Delete",
      "Database",
      "Room",
      "RoomDatabase" ->
        "android.arch.persistence.room:runtime"

      "SliceAction",
      "ListBuilder",
      "GridRowBuilder",
      "MessagingBuilder" ->
        "com.android.support:slices-builders"

      "SliceManager" -> "com.android.support:slices-core"

      "SliceView",
      "SliceUtils" ->
        "com.android.support:slices-view"

      "SlidingPaneLayout" ->
        "com.android.support:slidingpanelayout"

      "CustomTabsIntent" -> "com.android.support:customtabs"

      "MenuCompat",
      "MenuItemCompat",
      "DrawableCompat",
      "ContextCompat",
      "GravityCompat",
      "ServiceCompat",
      "BitmapCompat",
      "NotificationCompat",
      "NotificationManagerCompat",
      "FileProvider",
      "JobIntentService" ->
        // Most compat classes are here
        "com.android.support:support-compat"

      // Material Design library
      "Snackbar",
      "FloatingActionButton",
      "BottomAppBar",
      "TabLayout",
      "AppBarLayout",
      "BottomNavigationView",
      "CheckableImageButton" ->
        "com.android.support:design"

      // Firebase Libraries

      "AppInviteInvitation" -> "com.google.firebase:firebase-invites"
      "AdView", "AdRequest" -> "com.google.firebase:firebase-ads"
      "FirebaseAnalytics" -> "com.google.firebase:firebase-core"
      "FirebaseMessagingService" -> "com.google.firebase:firebase-messaging"
      "FirebaseAuth" -> "com.google.firebase:firebase-auth"
      "FirebaseDatabase" -> "com.google.firebase:firebase-database"
      "FirebaseStorage", "StorageReference" -> "com.google.firebase:firebase-storage:16.0.3"
      "FirebaseRemoteConfig" -> "com.google.firebase:firebase-config"
      "FirebaseCrash" -> "com.google.firebase:firebase-crash"
      "FirebaseDynamicLinks" -> "com.google.firebase:firebase-invites"
      "FirebaseFirestore" -> "com.google.firebase:firebase-firestore"
      "FirebaseVisionFaceDetectorOptions" -> "com.google.firebase:firebase-ml-vision-face-model"
      "FirebaseVisionLabelDetector" -> "com.google.firebase:firebase-ml-vision-image-label-model"

      "FirebaseVisionImage",
      "FirebaseVisionText",
      "FirebaseVisionBarcode",
      "FirebaseVision",
      "FirebaseVisionCloudDetectorOptions",
      "FirebaseVisionBarcodeDetectorOptions" ->
        "com.google.firebase:firebase-ml-vision"

      // Play Services Libraries

      "SignInButton",
      "GoogleSignIn",
      "GoogleSignInAccount",
      "GoogleSignInOptions" -> "com.google.android.gms:play-services-auth"

      "Awareness" -> "com.google.android.gms:play-services-awareness"

      "CastOptions",
      "CastContext" ->
        "com.google.android.gms:play-services-cast"

      "Fitness",
      "FitnessOptions" ->
        "com.google.android.gms:play-services-fitness"

      "LocationRequest",
      "LocationServices" ->
        "com.google.android.gms:play-services-location"

      "GoogleMap",
      "CameraUpdateFactory",
      "OnMapReadyCallback",
      "SupportMapFragment",
      "LatLng",
      "MarkerOptions" ->
        "com.google.android.gms:play-services-maps"

      "Nearby" -> "com.google.android.gms:play-services-nearby"

      "Games" -> "com.google.android.gms:play-services-games"

      "PaymentsClient", "Wallet" -> "com.google.android.gms:play-services-wallet"

      "BillingClient" -> "com.android.billingclient:billing"

      else -> null
    }
  }

  // For the given runtime artifact, if it also requires an annotation processor, provide it
  fun findAnnotationProcessor(artifact: String): String? {
    return when (artifact) {
      "androidx.room:room-runtime",
      "android.arch.persistence.room:runtime" -> "android.arch.persistence.room:compiler"
      else -> null
    }
  }

  fun findKtxLibrary(artifact: String): String? {
    return when (artifact) {
      "android.arch.work:work-runtime" -> "android.arch.work:work-runtime-ktx"
      "android.arch.navigation:navigation-runtime" -> "android.arch.navigation:navigation-runtime-ktx"
      "android.arch.navigation:navigation-fragment" -> "android.arch.navigation:navigation-fragment-ktx"
      "android.arch.navigation:navigation-common" -> "android.arch.navigation:navigation-common-ktx"
      "android.arch.navigation:navigation-ui" -> "android.arch.navigation:;navigation-ui-ktx"
      "androidx.fragment:fragment" -> "androidx.fragment:fragment-ktx"
      "androidx.collection:collection" -> "androidx.collection:collection-ktx"
      "androidx.sqlite:sqlite" -> "androidx.sqlite:sqlite-ktx"
      "androidx.palette:palette" -> "androidx.palette:palette-ktx"
      "androidx.dynamicanimation:dynamicanimation" -> "androidx.dynamicanimation:dynamicanimation-ktx"
      "androidx.activity:activity" -> "androidx.activity:activity-ktx"
      "androidx.paging:paging-runtime" -> "androidx.paging:paging-runtime-ktx"
      "androidx.paging:paging-common" -> "androidx.paging:paging-common-ktx"
      "androidx.paging:paging-rxjava2" -> "androidx.paging:paging-rxjava2-ktx"
      "androidx.core:core" -> "androidx.core:core-ktx"
      "androidx.preference:preference" -> "androidx.preference:preference-ktx"
      "androidx.lifecycle:lifecycle-viewmodel" -> "androidx.lifecycle:lifecycle-viewmodel-ktx"
      "androidx.lifecycle:lifecycle-reactivestreams" -> "androidx.lifecycle:lifecycle-reactivestreams-ktx"
      "androidx.lifecycle:lifecycle-livedata" -> "androidx.lifecycle:lifecycle-livedata-ktx"
      "androidx.lifecycle:lifecycle-livedata-core" -> "androidx.lifecycle:lifecycle-livedata-core-ktx"
      "androidx.slice:slice-builders" -> "androidx.slice:slice-builders-ktx"
      else -> null
    }
  }
}