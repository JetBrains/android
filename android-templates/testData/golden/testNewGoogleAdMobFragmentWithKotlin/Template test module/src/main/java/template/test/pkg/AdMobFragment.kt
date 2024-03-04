package template.test.pkg

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import template.test.pkg.R
import template.test.pkg.databinding.FragmentAdmobBinding
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.*

class AdMobFragment : Fragment() {
    private val TAG = "AdMobFragment"
    private var level: Int = 0
    private var interstitialAd: InterstitialAd? = null
    private lateinit var nextLevelButton: Button
    private lateinit var levelTextView: TextView
    private var _binding: FragmentAdmobBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAdmobBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create the next level button, which tries to show an interstitial when clicked.
        nextLevelButton = binding.nextLevelButton

        // Create the text view to show the level number.
        levelTextView = binding.level
        level = START_LEVEL
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val appContext = activity?.applicationContext ?: return

        nextLevelButton.let {
            it.isEnabled = false
            it.setOnClickListener { showInterstitial(appContext) }
            // Create the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        }
        MobileAds.initialize(appContext) { }
        // Load the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        loadInterstitialAd(appContext)
        Toast.makeText(appContext, TOAST_TEXT, Toast.LENGTH_LONG).show()
    }

    private fun loadInterstitialAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, getString(R.string.interstitial_ad_unit_id), adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    // The mInterstitialAd reference will be null until
                    // an ad is loaded.
                    interstitialAd = ad
                    nextLevelButton.isEnabled = true
                    Toast.makeText(context, "onAdLoaded()", Toast.LENGTH_SHORT).show()
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            // Called when fullscreen content is dismissed.
                            // Make sure to set your reference to null so you don't
                            // show it a second time.
                            interstitialAd = null
                            Log.d(TAG, "The ad was dismissed.")
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            // Called when fullscreen content failed to show.
                            // Make sure to set your reference to null so you don't
                            // show it a second time.
                            interstitialAd = null
                            Log.d(TAG, "The ad failed to show.")
                        }

                        override fun onAdShowedFullScreenContent() {
                            // Called when fullscreen content is shown.
                            Log.d(TAG, "The ad was shown.")
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    Log.i(TAG, loadAdError.message)
                    interstitialAd = null
                    nextLevelButton.isEnabled = true
                    val error = String.format(
                        Locale.ENGLISH,
                        "domain: %s, code: %d, message: %s",
                        loadAdError.domain,
                        loadAdError.code,
                        loadAdError.message
                    )
                    Toast.makeText(
                        getContext(),
                        "onAdFailedToLoad() with error: $error", Toast.LENGTH_SHORT
                    )
                        .show()
                }
            })
    }

    private fun showInterstitial(context: Context) {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (interstitialAd != null) {
            interstitialAd?.show(activity!!)
        } else {
            Toast.makeText(context, "Ad did not load", Toast.LENGTH_SHORT).show()
            goToNextLevel(context)
        }
    }

    private fun goToNextLevel(context: Context) {
        // Show the next level and reload the ad to prepare for the level after.
        levelTextView.text = context.getString(R.string.level_text, ++level)
        if (interstitialAd == null) {
            loadInterstitialAd(context)
        }
    }

    companion object {
        // Remove the below line after defining your own ad unit ID.
        private const val TOAST_TEXT =
            "Test ads are being shown. " + "To show live ads, replace the ad unit ID in res/values/strings.xml with your own ad unit ID."
        private const val START_LEVEL = 1
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}