package com.example.roomsampleproject.dao;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.example.roomsampleproject.entity.Word;

import java.util.List;

@Dao
public interface WordDao {

    @Query("DELETE from word_table")
    void deleteAll();

    @Query("SELECT * FROM  word_table ORDER By word Asc")
    List<Word> getAlphabetizedWords();


}


