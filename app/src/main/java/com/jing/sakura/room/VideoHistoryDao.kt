package com.jing.sakura.room

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoHistoryDao {

    @Query(
        """
        select v.* 
        from video_history v 
        inner join (
                select vv.animeId,max(vv.updateTime) updateTime
                from video_history vv 
                group by vv.animeId
            ) tmp
            on tmp.animeId = v.animeId
                and tmp.updateTime = v.updateTime
        order by v.updateTime desc
    """
    )
    fun queryHistory(): PagingSource<Int, VideoHistoryEntity>

    @Query("select * from video_history where animeId = :animeId order by updateTime desc limit 1")
    fun queryLastHistoryOfAnimeId(animeId: String): VideoHistoryEntity?

    @Query("select * from video_history where episodeId = :episodeId")
    fun queryHistoryByEpisodeId(episodeId: String): VideoHistoryEntity?

    @Query("delete from video_history")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveHistory(history: VideoHistoryEntity)
}