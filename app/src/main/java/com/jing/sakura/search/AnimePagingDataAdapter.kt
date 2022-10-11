package com.jing.sakura.search

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import coil.load
import com.jing.sakura.R
import com.jing.sakura.data.AnimeData
import com.jing.sakura.databinding.SearchAnimeItemBinding

class AnimePagingDataAdapter(
    private val onFocus: (Int) -> Unit = {},
    private val onAnimeClick: (AnimeData) -> Unit
) : PagingDataAdapter<AnimeData, ViewHolder>(
    diffComparator
) {

    companion object {

        private val diffComparator = object : DiffUtil.ItemCallback<AnimeData>() {
            override fun areItemsTheSame(oldItem: AnimeData, newItem: AnimeData): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: AnimeData, newItem: AnimeData): Boolean {
                return oldItem == newItem
            }

        }
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val anime = getItem(position)!!
        with(holder.itemView.tag as SearchAnimeItemBinding) {
            cover.load(anime.imageUrl)
            val initBackground = root.background
            root.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onFocus(position)
                }
                val values = if (hasFocus) floatArrayOf(1f, 1.2f) else floatArrayOf(1.2f, 1f)
                ValueAnimator.ofFloat(*values).apply {
                    addUpdateListener {
                        cover.scaleX = it.animatedValue as Float
                        cover.scaleY = it.animatedValue as Float
                    }
                    duration = 200L
                    start()
                }

                root.background =
                    if (hasFocus) ContextCompat.getDrawable(
                        root.context,
                        R.drawable.text_view_border
                    ) else initBackground
            }
            root.setOnClickListener {
                onAnimeClick(anime)
            }
            animeName.text = anime.title
            episode.text = anime.currentEpisode
            tags.text = anime.tags
            description.text = anime.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SearchAnimeItemBinding.inflate(LayoutInflater.from(parent.context))
        binding.root.tag = binding
        return object : ViewHolder(binding.root) {}
    }
}