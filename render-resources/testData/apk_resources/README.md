This is data for:

 * FileResourceReaderTest
 * ApkResourceRepositoryTest

The content of `apk-for-local-test.ap_` was produced by the following steps:

1. running `./gradlew :feature:interests:testDemoDebugUnitTest` in the
`nowinandroid` app source code
2. copying entries required for the tests from `feature/interests/build/intermediates/apk_for_local_test/demoDebugUnitTest/apk-for-local-test.ap_`
into this `apk-for-local-test.ap_` archive with `zip -U <path to demoDebugUnitTest folder>/apk-for-local-test.ap_ "res/drawable-anydpi-v24/ic_placeholder_default.xml" "resources.arsc" --out apk-for-local-test.ap_`
