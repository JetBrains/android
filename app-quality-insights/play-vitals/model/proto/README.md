# Play Vitals API (mirrored from google3)

## How to port .proto files from google3
* Since our API uses API version selector (go/api-version-selector-user-guide) to manage versions (v1 alpha, v1 beta and v1), before we
  really manually copy over those .proto files and strip off those google api annotations or so, we need to generate the correct versioned
  proto files. For example, we want to get the version "v1beta1":
  ```shell
  blaze build //google/play/developer/reporting:v1beta1_anomalies_service_proto \
  //google/play/developer/reporting:v1beta1_vitals_errors_service_proto \
  //google/play/developer/reporting:v1beta1_vitals_service_proto \
  //google/play/developer/reporting:v1beta1_reporting_service_proto \
  //google/play/developer/reporting:v1beta1_releases_proto \
  //google/play/developer/reporting:v1beta1_device_proto \
  //google/play/developer/reporting:v1beta1_common_proto \
  //google/play/developer/reporting:v1beta1_metrics_proto
  ```

  Then we can find the generated files at `//blaze-genfiles/google/play/developer/reporting/v1beta1/*.proto`.

## Change log
* 10/12/2023: Ported from last update: cl/553453096 ~ cl/572929528 merged on 10/12/2023
* 07/06/2023: Ported from last update: cl/544613022 merged on 06/30/2023
* 04/24/2023: Ported from last update: cl/526112545 merged on 04/21/2023
* 04/18/2023: Ported from last update: cl/520011331 ~ cl/524242483 merged on 04/14/2023
* 03/23/2023: Ported from last update: cl/518806368 merged on 03/23/2023