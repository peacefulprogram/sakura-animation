package com.jing.sakura.detail

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.jing.sakura.R
import com.jing.sakura.data.AnimePlayListEpisode

class DetailEpisodePresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
        val root = LayoutInflater.from(parent!!.context)
            .inflate(R.layout.detail_single_episode_layout, parent, false)
        val background = ContextCompat.getDrawable(
            parent.context,
            R.drawable.text_view_border
        ) as GradientDrawable
        background.mutate()
        root.background = background
        root.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                background.setStroke(1, ContextCompat.getColor(parent.context, R.color.rose300))
            } else {
                background.setStroke(1, ContextCompat.getColor(parent.context, R.color.gray400))
            }
        }
        return ViewHolder(root)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        val episode = item as AnimePlayListEpisode
        with(viewHolder!!.view) {
            findViewById<TextView>(R.id.detail_single_episode_name).text = episode.episode
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
//        TODO("Not yet implemented")
    }
}