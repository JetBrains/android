package src.adux.votingapplib;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import src.adux.votingapplib.Interfaces.UIChangeListener;
import src.adux.votingapplib.adapters.AdapterFragmentQ;
import src.adux.votingapplib.fragments.FragmentCheckAll;
import src.adux.votingapplib.fragments.FragmentEnd;
import src.adux.votingapplib.fragments.FragmentGreenRedDots;
import src.adux.votingapplib.fragments.FragmentDollars;
import src.adux.votingapplib.fragments.FragmentNumericFeedback;
import src.adux.votingapplib.fragments.FragmentSingleChoice;
import src.adux.votingapplib.fragments.FragmentWrittenFeedback;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.models.Question;
import src.adux.votingapplib.widgets.CustomTypefaceSpan;
import com.google.gson.Gson;

import java.util.ArrayList;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class SurveyActivity extends AppCompatActivity implements UIChangeListener{
    private Question mQuestion;
    private ViewPager mPager;
    private String mHash = null;
    private Fragment mCurrentFragment;
    private Fragment mEndFragment;
    private Fragment mNextFragment;
    private Bundle mMainFragmentArgs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_survey);

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/google_sans_medium.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );

        Typeface font = Typeface.createFromAsset(getAssets(), "fonts/google_sans_medium.ttf");
        SpannableString str = new SpannableString("  Vote");
        str.setSpan(new CustomTypefaceSpan("",font),0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        str.setSpan(new RelativeSizeSpan(1.2f),0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getSupportActionBar().setTitle(str);


        if (getIntent().getExtras() != null) {
            Bundle bundle = getIntent().getExtras();
            mHash = bundle.getString("hash");
            //System.out.println("&&&&&&&&&&&&& Intent " + mHash + " &&&&&&&&&&&&&");
            mQuestion = new Gson().fromJson(bundle.getString("json_survey"), Question.class);
        }


        Log.i("json Object = ", String.valueOf(mQuestion));
        final ArrayList<Fragment> arraylist_fragments = new ArrayList<>();

        //- FILL -
        if(mQuestion.getChoices() != null) {
            mQuestion.setNumberOfItems(mQuestion.getChoices().size());
        }
        else
            mQuestion.setNumberOfItems(0);

        Bundle xBundle = new Bundle();
        xBundle.putSerializable("data", mQuestion);
        xBundle.putString("hash", mHash);

        if (mQuestion.getQuestionType().equals("dot-select")){
            FragmentGreenRedDots frag = new FragmentGreenRedDots();
            frag.setArguments(xBundle);
            arraylist_fragments.add(frag);
            mCurrentFragment = frag;
//            FragmentGreen fragGreen = new FragmentGreen();
//            fragGreen.setArguments(xBundle);
//            FragmentRed fragRed = new FragmentRed();
//            fragRed.setArguments(xBundle);
//            arraylist_fragments.add(fragGreen);
//            arraylist_fragments.add(fragRed);
//            mCurrentFragment = fragGreen;
//            mNextFragment = fragRed;
        }

        if (mQuestion.getQuestionType().equals("check-all")){
            FragmentCheckAll frag = new FragmentCheckAll();
            frag.setArguments(xBundle);
            arraylist_fragments.add(frag);
        }

        if (mQuestion.getQuestionType().equals("numeric-feedback")) {
            FragmentNumericFeedback frag = new FragmentNumericFeedback();
            frag.setArguments(xBundle);
            arraylist_fragments.add(frag);
        }


        if (mQuestion.getQuestionType().equals("single-choice")) {
            FragmentSingleChoice frag = new FragmentSingleChoice();
            frag.setArguments(xBundle);
            arraylist_fragments.add(frag);

        }

        if (mQuestion.getQuestionType().equals("written-feedback")) {
            FragmentWrittenFeedback frag = new FragmentWrittenFeedback();
            frag.setArguments(xBundle);
            arraylist_fragments.add(frag);
        }

        if (mQuestion.getQuestionType().equals("dollars")) {
            FragmentDollars frag = new FragmentDollars();
            frag.setArguments(xBundle);
            arraylist_fragments.add(frag);

        }

        //        //- START -
//        if (!mSurveyPojo.getSurveyProperties().getSkipIntro()) {
//            FragmentStart frag_start = new FragmentStart();
//            Bundle sBundle = new Bundle();
//            sBundle.putSerializable("survery_properties", mSurveyPojo.getSurveyProperties());
//            sBundle.putString("style", style_string);
//            frag_start.setArguments(sBundle);
//            arraylist_fragments.add(frag_start);
//        }

//        for (Question mQuestion : mSurveyPojo.getQuestions()) {
//
//            if (mQuestion.getQuestionType().equals("String")) {
//                FragmentWrittenFeedback frag = new FragmentWrittenFeedback();
//                Bundle xBundle = new Bundle();
//                xBundle.putSerializable("data", mQuestion);
//                xBundle.putString("style", style_string);
//                frag.setArguments(xBundle);
//                arraylist_fragments.add(frag);
//            }
//
//            if (mQuestion.getQuestionType().equals("MultiSelectGreenRed")) {
//                FragmentGreenRed frag = new FragmentGreenRed();
//                if(hash != null) {
//                    mQuestion.setNumberOfItems(Integer.parseInt(hash[1]));
//                    mQuestion.setLeastMax(Integer.parseInt(hash[2]));
//                    mQuestion.setMostMax(Integer.parseInt(hash[2]));
//                }
//                Bundle xBundle = new Bundle();
//                xBundle.putSerializable("data", mQuestion);
//                xBundle.putString("style", style_string);
//                xBundle.putString("DotType", "Green");
//                frag.setArguments(xBundle);
//                arraylist_fragments.add(frag);
//            }
//
//
//            if (mQuestion.getQuestionType().equals("Checkboxes")) {
//                FragmentSingleChoice frag = new FragmentSingleChoice();
//                Bundle xBundle = new Bundle();
//                xBundle.putSerializable("data", mQuestion);
//                xBundle.putString("style", style_string);
//                frag.setArguments(xBundle);
//                arraylist_fragments.add(frag);
//            }
//
//            if (mQuestion.getQuestionType().equals("Number")) {
//                FragmentNumericFeedback frag = new FragmentNumericFeedback();
//                Bundle xBundle = new Bundle();
//                xBundle.putSerializable("data", mQuestion);
//                xBundle.putString("style", style_string);
//                frag.setArguments(xBundle);
//                arraylist_fragments.add(frag);
//            }
//
//            if (mQuestion.getQuestionType().equals("StringMultiline")) {
//                FragmentMultiline frag = new FragmentMultiline();
//                Bundle xBundle = new Bundle();
//                xBundle.putSerializable("data", mQuestion);
//                xBundle.putString("style", style_string);
//                frag.setArguments(xBundle);
//                arraylist_fragments.add(frag);
//            }
//
//        }
//


        //- END -
        FragmentEnd frag_end = new FragmentEnd();
        Bundle eBundle = new Bundle();
        frag_end.setArguments(eBundle);
        mEndFragment = frag_end;
        arraylist_fragments.add(frag_end);


        mPager = (ViewPager) findViewById(R.id.pager);
        AdapterFragmentQ mPagerAdapter = new AdapterFragmentQ(getSupportFragmentManager(), arraylist_fragments);
        mPager.setAdapter(mPagerAdapter);
    }

    public void go_to_next() {
        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
    }


    @Override
    public void onBackPressed() {
        if (mPager.getCurrentItem() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(
                    "Are you sure?")
                    .setMessage("You might lose the data that you have selected. Do you wish to proceed?")
                    .setCancelable(false)
                    .setPositiveButton("Ok",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    SurveyActivity.super.onBackPressed();
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    dialog.cancel();
                                }
                            }).create().show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(
                    "Are you sure?")
                    .setMessage("You might lose the data that you have selected. Do you wish to proceed?")
                    .setCancelable(false)
                    .setPositiveButton("Ok",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    mPager.setCurrentItem(mPager.getCurrentItem() - 1);
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {
                                    dialog.cancel();
                                }
                            }).create().show();

        }
    }

    public void event_survey_completed(AnswerManager instance) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra("answers", instance.get_answer());
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public void sendMessage(String text) {
        //((FragmentRed) mNextFragment).onUIUpdate(text);
    }
}
