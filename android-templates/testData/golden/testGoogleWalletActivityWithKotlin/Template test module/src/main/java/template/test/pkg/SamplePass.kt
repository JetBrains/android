package template.test.pkg

import java.util.Date
import kotlin.random.Random

/**
 * A helper class that allows us to easily configure the JSON we will be sending to
 * [PayClient.savePasses].
 *
 * If you are using the [temporary issuer](https://wallet-lab-tools.web.app/issuers) tool, then you
 * can use this class with the parameters created on the linked website.
 */
data class SamplePass(
    private val issuerEmail: String,
    private val issuerId: String,
    private val passClass: String,
    private val passId: String
) {
    val toJson: String = """
    {
      "iss": "$issuerEmail",
      "aud": "google",
      "typ": "savetowallet",
      "iat": ${Date().time / 1000L},
      "origins": [],
      "payload": {
        "genericObjects": [
          {
            "id": "$issuerId.$passId",
            "classId": "$passClass",
            "genericType": "GENERIC_TYPE_UNSPECIFIED",
            "hexBackgroundColor": "#4285f4",
            "logo": {
              "sourceUri": {
                "uri": "https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/pass_google_logo.jpg"
              }
            },
            "cardTitle": {
              "defaultValue": {
                "language": "en",
                "value": "Google I/O '22  [DEMO ONLY]"
              }
            },
            "subheader": {
              "defaultValue": {
                "language": "en",
                "value": "Attendee"
              }
            },
            "header": {
              "defaultValue": {
                "language": "en",
                "value": "Nicholas Corder"
              }
            },
            "barcode": {
              "type": "QR_CODE",
              "value": "$passId"
            },
            "heroImage": {
              "sourceUri": {
                "uri": "https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/google-io-hero-demo-only.jpg"
              }
            },
            "textModulesData": [
              {
                "header": "POINTS",
                "body": "${Random.nextInt(0, 9999)}",
                "id": "points"
              },
              {
                "header": "CONTACTS",
                "body": "${Random.nextInt(1, 99)}",
                "id": "contacts"
              }
            ]
          }
        ]
      }
    }
    """
}