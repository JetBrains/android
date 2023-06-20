package src.adux.votingapplib.models.PollAnswers;

import com.google.gson.Gson;


import java.util.ArrayList;
import java.util.LinkedHashMap;

//Singleton AnswerManager ........

public class AnswerManager {
    private volatile static AnswerManager uniqueInstance;
    private LinkedHashMap<String, String> answered_hashmap = new LinkedHashMap<>();
    private String answered_list = new String();
    private boolean mIsGreen = true;


    private AnswerManager() {
    }

    public void reset() {
        answered_list = new String();
    }

    public void put_answer(String key, String value) {
        answered_hashmap.put(key, value);
    }

    public void put_answer2(String value) {
        answered_list = value;
    }

    public String get_answer(){
        return answered_list;
    }

    public void put_answer1(String key, ArrayList<Integer> value) {
        //answered_hashmap1.put(key, value);
    }

    public String get_json_object() {
//        Gson gson = new Gson();
//        return gson.toJson(answered_hashmap1,LinkedHashMap.class);
        return null;
    }

    public String get_json_object_string() {
        Gson gson = new Gson();
        return gson.toJson(answered_hashmap,LinkedHashMap.class);
    }

    public ArrayList<Integer> getChoices(String key){
        //return answered_hashmap1.get(key);
        return null;
    }

    @Override
    public String toString() {
        return String.valueOf(answered_hashmap);
    }

    public static AnswerManager getInstance() {
        if (uniqueInstance == null) {
            synchronized (AnswerManager.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new AnswerManager();
                }
            }
        }
        return uniqueInstance;
    }
}
