"""Dependencies for SimpleComposeApplication projects."""

COMPOSE_UI_VERSION = "1.8.0-alpha06"
LIFECYCLE_VERSION = "2.8.7"
APPCOMPAT_VERSION = "1.7.0"
ACTIVITY_COMPOSE_VERSION = "1.9.3"
CORE_KTX_VERSION = "1.13.1"
COLLECTION_VERSION = "1.5.0-alpha06"
EMOJI2_VIEWS_HELPER_VERSION = "1.4.0"

# Dependencies for SimpleComposeApplication
SIMPLE_COMPOSE_APPLICATION_DEPS = [
    # Direct dependencies
    "@maven//:androidx.appcompat.appcompat_" + APPCOMPAT_VERSION,
    "@maven//:androidx.activity.activity-compose_" + ACTIVITY_COMPOSE_VERSION,
    "@maven//:androidx.compose.ui.ui_" + COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.material.material_" + COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.ui.ui-tooling_" + COMPOSE_UI_VERSION,

    # Transitive dependencies
    "@maven//:androidx.core.core-ktx_" + CORE_KTX_VERSION,
    "@maven//:androidx.collection.collection_" + COLLECTION_VERSION,
    "@maven//:androidx.compose.foundation.foundation_" + COMPOSE_UI_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_" + LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-livedata_" + LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-process_" + LIFECYCLE_VERSION,
    "@maven//:androidx.emoji2.emoji2-views-helper_" + EMOJI2_VIEWS_HELPER_VERSION,
    "@maven//:androidx.collection.collection-ktx_" + COLLECTION_VERSION,
    "@maven//:androidx.compose.animation.animation_" + COMPOSE_UI_VERSION,
]
