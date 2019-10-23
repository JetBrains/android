package src.adux.votingapplib;

import android.app.Activity;
import android.content.Context;
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
import android.view.View;
import android.widget.RelativeLayout;

import src.adux.votingapplib.adapters.AdapterFragmentQ;
import src.adux.votingapplib.fragments.FragmentCheckAll;
import src.adux.votingapplib.fragments.FragmentEnd;
import src.adux.votingapplib.fragments.FragmentNumericFeedback;
import src.adux.votingapplib.fragments.FragmentSingleChoice;
import src.adux.votingapplib.fragments.FragmentWrittenFeedback;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.models.Question;
import src.adux.votingapplib.models.QuestionsList;
import src.adux.votingapplib.models.Survey;
import src.adux.votingapplib.widgets.CustomTypefaceSpan;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;

import httplib.HttpGetQuestion;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class SurveysActivity extends AppCompatActivity {
    private Survey mSurvey = new Survey();
    private ViewPager mPager;
    String response = null;
    private static final String SERVER_URL = "https://us-central1-asux-survey.cloudfunctions.net";
    private QuestionsList mHashes = new QuestionsList();
    private Question mQuestion = new Question();
    private int mReceivedAll = 0;
    private RelativeLayout mProgress;

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
        SpannableString str = new SpannableString("  Cast your vote");
        str.setSpan(new CustomTypefaceSpan("",font),0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        str.setSpan(new RelativeSizeSpan(1.2f),0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getSupportActionBar().setTitle(str);

        mProgress = (RelativeLayout) findViewById(R.id.progress);
        mProgress.setVisibility(View.VISIBLE);

        if (getIntent().getExtras() != null) {
            Bundle bundle = getIntent().getExtras();
            mHashes = new Gson().fromJson(bundle.getString("hashes"), QuestionsList.class);
            //mQuestion = new Gson().fromJson(bundle.getString("json_survey"), Question.class);
            getQuestions();
            System.out.println("Got it....");
        }
    }

    public void go_to_next() {
        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
    }

    private void startSurveyUI() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgress.setVisibility(View.GONE);
            }
        });

        final ArrayList<Fragment> arraylist_fragments = new ArrayList<>();
//
//        //- FILL -
        for(int questions = 0; questions < mHashes.getQuestionList().size();questions++) {
            Bundle xBundle = new Bundle();
            mQuestion = mSurvey.getQuestionList().get(questions);
            if(mQuestion.getChoices() != null) {
                mQuestion.setNumberOfItems(mQuestion.getChoices().size());
            }
            else
                mQuestion.setNumberOfItems(0);
            xBundle.putString("hash", mHashes.getQuestionList().get(questions));
            xBundle.putSerializable("data", mQuestion);
            xBundle.putSerializable("type", "SURVEY");

//            if (mQuestion.getQuestionType().equals("dot-select")){
//                FragmentRedGreen frag = new FragmentRedGreen();
//                frag.setArguments(xBundle);
//                arraylist_fragments.add(frag);
//            }

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
//
//
        //- END -
        FragmentEnd frag_end = new FragmentEnd();
        Bundle eBundle = new Bundle();
        eBundle.putSerializable("type", "SURVEY");
        frag_end.setArguments(eBundle);
        arraylist_fragments.add(frag_end);


        SurveysActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPager = (ViewPager) findViewById(R.id.pager);
                AdapterFragmentQ mPagerAdapter = new AdapterFragmentQ(getSupportFragmentManager(), arraylist_fragments);
                mPager.setAdapter(mPagerAdapter);
            }
        });

    }



    private void getQuestions(){
        Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < mHashes.getQuestionList().size(); i++) {
                            try {
                                response = "";
                                HttpGetQuestion httpClient = new HttpGetQuestion();
                                response = httpClient.run(SERVER_URL + "/question?id=" + mHashes.getQuestionList().get(i));
                                Log.d("Surveys activity", "Received Response : " + response);
                                if (validateCode(response) == true) {
                                    mSurvey.getQuestionList().add(new Gson().fromJson(response, Question.class));
                                    mReceivedAll++;
                                    if(mReceivedAll == mHashes.getQuestionList().size()) {
                                        startSurveyUI();
                                    }
                                } else {

                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

        });
        thread.start();
    }

    private boolean validateCode(String response){
        if(response == null || response.equalsIgnoreCase("") || response.contains("Error"))
            return false;
        return true;
    }



    @Override
    public void onBackPressed() {
        if (mPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            mPager.setCurrentItem(mPager.getCurrentItem() - 1);
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
}
