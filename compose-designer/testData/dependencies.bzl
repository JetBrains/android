"""Dependencies for Compose Gradle projects."""

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

# Dependencies for OnboardingAuth IBM project
ONBOARDING_AUTH_CORE_KTX_VERSION = "1.16.0"
ONBOARDING_AUTH_ACTIVITY_COMPOSE_VERSION = "1.7.2"
ONBOARDING_AUTH_LIFECYCLE_VERSION = "2.8.7"
ONBOARDING_AUTH_COMPOSE_BOM_VERSION = "2024.09.00"
ONBOARDING_AUTH_MATERIAL3_VERSION = "1.3.0"
ONBOARDING_AUTH_ADAPTIVE_VERSION = "1.0.0"
ONBOARDING_AUTH_COMPOSE_UI_VERSION = "1.7.0"
ONBOARDING_AUTH_COLLECTION_KTX_VERSION = "1.4.2"

ONBOARDING_AUTH_IBM_PROJECT_DEP = [
    # Direct dependencies
    "@maven//:androidx.core.core-ktx_" + ONBOARDING_AUTH_CORE_KTX_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.activity.activity-compose_" + ONBOARDING_AUTH_ACTIVITY_COMPOSE_VERSION,
    "@maven//:androidx.compose.compose-bom_" + ONBOARDING_AUTH_COMPOSE_BOM_VERSION,
    "@maven//:androidx.compose.material3.material3_" + ONBOARDING_AUTH_MATERIAL3_VERSION,
    "@maven//:androidx.compose.material3.adaptive.adaptive_" + ONBOARDING_AUTH_ADAPTIVE_VERSION,
    "@maven//:androidx.compose.ui.ui-text-google-fonts_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,

    # Transitive dependencies
    "@maven//:androidx.lifecycle.lifecycle-viewmodel_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-process_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-common-java8_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.lifecycle.lifecycle-runtime-compose_" + ONBOARDING_AUTH_LIFECYCLE_VERSION,
    "@maven//:androidx.compose.material.material_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.material.material-icons-extended_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.collection.collection-ktx_" + ONBOARDING_AUTH_COLLECTION_KTX_VERSION,
    "@maven//:androidx.compose.ui.ui-tooling_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.ui.ui-tooling-preview_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.ui.ui-test-manifest_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.ui.ui_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.runtime.runtime_" + ONBOARDING_AUTH_COMPOSE_UI_VERSION,
    "@maven//:androidx.compose.runtime.runtime-saveable_1.7.1",
]
