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
                select vv.animeId,max(vv.updateTime) updateTime, vv.sourceId
                from video_history vv 
                group by vv.animeId, vv.sourceId
            ) tmp
            on tmp.animeId = v.animeId
                and v.sourceId = tmp.sourceId
                and tmp.updateTime = v.updateTime
        order by v.updateTime desc
    """
    )
    fun queryHistory(): PagingSource<Int, VideoHistoryEntity>

    @Query("select * from video_history where animeId = :animeId and sourceId = :sourceId order by updateTime desc limit 1")
    fun queryLastHistoryOfAnimeId(animeId: String, sourceId: String): VideoHistoryEntity?

    @Query("select * from video_history where animeId = :animeId and episodeId = :episodeId and sourceId = :sourceId")
    fun queryHistoryByEpisodeId(
        episodeId: String,
        sourceId: String,
        animeId: String
    ): VideoHistoryEntity?

    @Query("delete from video_history")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveHistory(history: VideoHistoryEntity)

    @Query("delete from video_history where animeId = :animeId and sourceId = :sourceId")
    fun deleteHistoryByAnimeId(animeId: String, sourceId: String)
}