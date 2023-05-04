package com.example.roomsampleproject;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.roomsampleproject.dao.WordDao;
import com.example.roomsampleproject.entity.Word;

import java.util.List;


public class WordRepository {


    private WordDao mWordDao;

    private List<Word> mAllWords;

    WordRepository(Application application){
        WordRoomDatabase db = WordRoomDatabase.getDatabase(application);
        mWordDao = db.wordDao();
        mAllWords = mWordDao.getAlphabetizedWords();
    }

    List<Word> getmAllWords(){
        return mAllWords;
    }


}
