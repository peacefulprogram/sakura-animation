package com.jing.sakura.room

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchHistoryDao {

    @Query("select * from search_history order by searchTime desc")
    fun queryHistory(): PagingSource<Int, SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveHistory(history: SearchHistoryEntity)

    @Query("delete from search_history where keyword = :keyword")
    fun deleteHistory(keyword: String)

    @Query("delete from search_history")
    fun deleteAllHistory()
}