# Titan AI Cloud Companion API (mirrored from google3)

## How to port .proto files from google3
* Since our API uses API version selector (go/api-version-selector-user-guide) to manage versions (v1 alpha, v1 beta and v1), before we
  really manually copy over those .proto files and strip off those google api annotations or so, we need to generate the correct versioned
  proto files. For example, we want to get the version "v1main":
  ```shell
  blaze build //google/cloud/cloudaicompanion:v1main_proto
  ```

  Then we can find the generated files at `//blaze-genfiles/google/cloud/cloudaicompanion/v1/*.proto`.

## Change log
* 07/25/2024: Ported from last updates: cl/656054498 merged on 07/25/2024