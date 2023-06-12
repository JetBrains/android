# Play Vitals API (mirrored from google3)

## How to port .proto files from google3
* Since our API uses API version selector (go/api-version-selector-user-guide) to manage versions (v1 alpha, v1 beta and v1), before we
  really manually copy over those .proto files and strip off those google api annotations or so, we need to generate the correct versioned
  proto files. For example, we want to get the version "v1beta1":
  ```shell
  blaze build //google/play/developer/reporting:v1beta1_anomalies_service_proto
  ```

  Then we can find the generated files at `//blaze-genfiles/google/play/developer/reporting/v1beta1/*.proto`.

## Change log
* 03/23/2023: Ported from last update: cl/518806368 merged on 03/23/2023