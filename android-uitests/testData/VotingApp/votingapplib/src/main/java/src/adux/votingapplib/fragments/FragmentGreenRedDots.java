package src.adux.votingapplib.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;

import httplib.HttpPostAnswer;
import src.adux.votingapplib.R;
import src.adux.votingapplib.SurveyActivity;
import src.adux.votingapplib.SurveysActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.models.PollAnswers.AnswerRedGreen.AnswerRedGreen;
import src.adux.votingapplib.models.PollAnswers.AnswerRedGreen.Response;
import src.nex3z.togglebuttongroup.PollManager;
import src.adux.votingapplib.models.Question;
import src.adux.votingapplib.models.Session;
import src.nex3z.togglebuttongroup.MultiSelectToggleGroup;
import src.nex3z.togglebuttongroup.SingleSelectToggleGroup;
import src.nex3z.togglebuttongroup.button.CircularToggle;
import src.nex3z.togglebuttongroup.button.RectangularToggle;


public class FragmentGreenRedDots extends Fragment{

    private static final String LOG_TAG = FragmentGreenRedDots.class.getSimpleName();
    private static final String SERVER_URL = "https://us-central1-asux-survey.cloudfunctions.net";
    private Question mQuestion;
    private FragmentActivity mContext;
    private Button mButtonContinue;
    private TextView mLayoutTitle;
    private MultiSelectToggleGroup mDots;
    private SingleSelectToggleGroup mDotsColorChoice;
    private RectangularToggle mRedState, mGreenState;

    private String mOptionNames ="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private AnswerRedGreen mAnswerRedGreen;
    private Response mResponse;
    private String mQid;
    private String mUid;
    private String mType = "";
    private Session mSession;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_red_green_dots, container, false);

        mLayoutTitle = (TextView) rootView.findViewById(R.id.dots_title);
        mDots = (MultiSelectToggleGroup) rootView.findViewById(R.id.dots_layout);
        mButtonContinue = (Button) rootView.findViewById(R.id.button_continue);
        mDotsColorChoice = (SingleSelectToggleGroup) rootView.findViewById(R.id.dots_color);
        mButtonContinue.setAlpha(0.5f);

        mQid = getArguments().getString("hash");
        mType = getArguments().getString("type");
        mSession = new Session(this.getContext());
        mUid = mSession.getUserID();

        mAnswerRedGreen = new AnswerRedGreen();
        mResponse = new Response();
        mResponse.setGreen(new ArrayList<Integer>());
        mResponse.setRed(new ArrayList<Integer>());
        mAnswerRedGreen.setQid(mQid);
        mAnswerRedGreen.setUid(mUid);

        mButtonContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mDots.getCheckedIds().size() < (mQuestion.getGreenDots() + mQuestion.getRedDots())){
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {dialog.cancel();}
                                };
                            AlertDialog.Builder builder = new AlertDialog.Builder(FragmentGreenRedDots.this.getActivity());
                            builder.setTitle("Dots remaining")
                                    .setMessage("You need to select " + (mQuestion.getGreenDots() - PollManager.getInstance().getNumberGreensUsed()) + " more green dots and "+ (mQuestion.getRedDots() - PollManager.getInstance().getNumberRedsUsed()) + " more Red dots to continue.")
                                    .setPositiveButton(android.R.string.ok, listener)
                                    .show();
                            }
                        });
                    } else {
                        // multisurvey
                        if (mType == null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(FragmentGreenRedDots.this.getActivity());
                            builder.setTitle(
                                    "Submit response?")
                                    .setMessage("Do you wish to submit your response?")
                                    .setCancelable(false)
                                    .setPositiveButton("Ok",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,
                                                                    int id) {
                                                    setResponse();
                                                    mAnswerRedGreen.setResponse(mResponse);
                                                    AnswerManager.getInstance().put_answer2(new Gson().toJson(mAnswerRedGreen, AnswerRedGreen.class));
                                                    ((SurveyActivity) mContext).event_survey_completed(AnswerManager.getInstance());
                                                }
                                            })
                                    .setNegativeButton("Cancel",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,
                                                                    int id) {
                                                    dialog.cancel();
                                                }
                                            }).create().show();
                        }else {
                            // postAnswer();
                            ((SurveysActivity) mContext).go_to_next();
                        }
                }
            }
        });
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        mQuestion = (Question) getArguments().getSerializable("data");
        mAnswerRedGreen.setQtype(mQuestion.getQuestionType());
        mLayoutTitle.setText(mQuestion != null ? "Now selecting : Green dots"  : "");

        PollManager.getInstance().initializeDotStates(mQuestion.getNumberOfItems());
        PollManager.getInstance().setFragment(true);
        PollManager.getInstance().setToggleState(true);
        PollManager.getInstance().setAreGreensOver(false);
        PollManager.getInstance().setAreRedsOver(false);
        PollManager.getInstance().setmMaxGreen(mQuestion.getGreenDots());
        PollManager.getInstance().setmMaxRed(mQuestion.getRedDots());


        GradientDrawable mGradient = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), src.nex3z.togglebuttongroup.R.drawable.bg_circle);
        mGradient.setColor(Color.parseColor("#4AB97F"));


        for(int i = 0; i < mQuestion.getNumberOfItems(); i++){
            CircularToggle toggle = new CircularToggle(mContext);
            toggle.setTextSize(24);
            toggle.setText(Character.toString(mOptionNames.charAt(i%26)));
            toggle.setCheckedImageDrawable(mGradient);
            mDots.addView(toggle);
        }

        mDots.setOnCheckedChangeListener(new MultiSelectToggleGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedStateChanged(MultiSelectToggleGroup group, int checkedId, boolean isChecked) {
                Log.v(LOG_TAG, "onCheckedStateChanged(): Green = " + group.getCheckedIds());
                int checked = moderate(checkedId,mQuestion.getNumberOfItems(),group.getChildAt(0).getId());

                // change the states for the dots
                // if uncheck state then just change the state
                if(isChecked == false){
                    PollManager.getInstance().setDotstates(checked,0);
                } else {
                    // if check, and green then set state to 1
                    if(mGreenState.isChecked()){
                        PollManager.getInstance().setDotstates(checked,1);
                    }
                    // if check and red then set state to 2
                    else {
                        PollManager.getInstance().setDotstates(checked,2);
                    }
                }

                // reflect that on the green title
                mGreenState.setText(("Green : " + String.valueOf(mQuestion.getGreenDots()-PollManager.getInstance().getNumberGreensUsed())).toUpperCase());

                // reflect that on the red title
                mRedState.setText(("Red : " + String.valueOf(mQuestion.getRedDots()-PollManager.getInstance().getNumberRedsUsed())).toUpperCase());


                 // if green dots used up,
                //      && redDots not zero - switch to red dots
                int usedG, usedR;
                usedG = mQuestion.getGreenDots()-PollManager.getInstance().getNumberGreensUsed();
                usedR = mQuestion.getRedDots()-PollManager.getInstance().getNumberRedsUsed();
                if(usedG == 0 && usedR == 0 ) {
                    setViewAndChildrenEnabled(mDots,false);
                } else if(mGreenState.isChecked() && usedG == 0){
                    setViewAndChildrenEnabled(mDots,false);
                } else if(mRedState.isChecked() && usedR == 0) {
                    setViewAndChildrenEnabled(mDots,false);
                } else {
                    setViewAndChildrenEnabled(mDots,true);
                }


                // if red dots used up
                //      && greenDots not zero - switch to red dots

                //check if all reg and green dots are used up, change state accordingly
                if(((mQuestion.getGreenDots() + mQuestion.getRedDots()) - group.getCheckedIds().size()) == 0)
                    mButtonContinue.setAlpha(1);
                else {
                    mButtonContinue.setAlpha(0.5f);
                }
            }
        });



        GradientDrawable mGreenGradient = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), src.nex3z.togglebuttongroup.R.drawable.bg_rectangle);
        mGreenGradient.setColor(Color.parseColor("#4AB97F"));

        GradientDrawable mGreenGradient1 = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), src.nex3z.togglebuttongroup.R.drawable.bg_rectangle);
        mGreenGradient1.setColor(Color.parseColor("#ED495D"));

        mGreenState = new RectangularToggle(mContext);
        mGreenState.setTextSize(16);
        mGreenState.setText(("Green : "+ mQuestion.getGreenDots()).toUpperCase());

        mGreenState.setChecked(true);
        mGreenState.setCheckedImageDrawable(mGreenGradient);
        mDotsColorChoice.addView(mGreenState);

        mRedState = new RectangularToggle(mContext);
        mRedState.setTextSize(16);
        mRedState.setText(("Red : "+ mQuestion.getRedDots()).toUpperCase());
        mRedState.setCheckedImageDrawable(mGreenGradient1);
        mDotsColorChoice.addView(mRedState);
        mDotsColorChoice.setOnCheckedChangeListener(new SingleSelectToggleGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(SingleSelectToggleGroup group, int checkedId) {
                if(mGreenState.isChecked()){
                    mLayoutTitle.setText("Now selecting : Green dots");
                    Log.v(LOG_TAG, "onCheckedChangedColor() Green : checkedId = " + checkedId);
                    PollManager.getInstance().setToggleState(true);
                    if(mQuestion.getGreenDots() - PollManager.getInstance().getNumberGreensUsed() == 0){
                        setViewAndChildrenEnabled(mDots,false);
                    } else {
                        setViewAndChildrenEnabled(mDots,true);
                    }
                } else {
                    mLayoutTitle.setText("Now selecting : Red dots");
                    Log.v(LOG_TAG, "onCheckedChangedColor() Red : checkedId = " + checkedId);
                    PollManager.getInstance().setToggleState(false);
                    if(mQuestion.getRedDots() - PollManager.getInstance().getNumberRedsUsed() == 0){
                        setViewAndChildrenEnabled(mDots,false);
                    } else {
                        setViewAndChildrenEnabled(mDots,true);
                    }
                }

            }
        });
    }

    private static void setViewAndChildrenEnabled(View view, boolean enabled) {
        int arr[] = PollManager.getInstance().getDotsStates();
        if(enabled) {
            view.setEnabled(enabled);
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    setViewAndChildrenEnabled(child, enabled);
                }
            }
        } else {
            view.setEnabled(enabled);
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    if(arr[i]==0) {
                        View child = viewGroup.getChildAt(i);
                        setViewAndChildrenEnabled(child, enabled);
                    }
                }
            }
        }
    }


    // convert randomly generated id's to start from 0
    private Integer moderate(int value,int max, int firstChild){
        return ((value - firstChild) % max);
    }


    // setting response before sending
    private void setResponse(){
        int arr[] = PollManager.getInstance().getDotsStates();
        for(int i= 0;i<arr.length;i++){
            if(arr[i]==1){
                mResponse.getGreen().add(Integer.valueOf(i));
            } else if (arr[i] == 2){
                mResponse.getRed().add(Integer.valueOf(i));
            }
        }
    }


    // This will happen in the red layout
    private void postAnswer(){
        final String answers_json = AnswerManager.getInstance().get_answer();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpPostAnswer httpPostClient = new HttpPostAnswer();
                try {
                    Log.d("****", "****************** WE HAVE ANSWERS ******************");
                    Log.d(LOG_TAG, "Posting data : " + answers_json);
                    Log.d("****", "****************** WE HAVE ANSWERS ******************");
                    httpPostClient.post(SERVER_URL + "/answer", answers_json);
                } catch (IOException e) {
                    e.printStackTrace();
                    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    };
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentGreenRedDots.this.getActivity());
                    builder.setTitle("Error")
                            .setMessage("Some problem")
                            .setPositiveButton(android.R.string.ok, listener)
                            .show();
                }
            }
        });
        thread.start();
    }
}