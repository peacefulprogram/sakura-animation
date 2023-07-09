package com.jing.sakura.presenter

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import coil.size.Scale
import com.jing.sakura.R
import com.jing.sakura.data.AnimeData
import com.jing.sakura.extend.getColorWithAlpha

class AnimeCardPresenter(
    private val cardWidth: Int,
    private val cardHeight: Int,
    private val infoBgColor: Int? = null
) : Presenter() {


    override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
        val imageCardView = ImageCardView(parent!!.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            cardType = BaseCardView.CARD_TYPE_INFO_OVER
            setInfoAreaBackgroundColor(
                infoBgColor ?: ContextCompat.getColor(parent.context, R.color.gray900)
                    .getColorWithAlpha(0.5f)
            )
            mainImageView.layoutParams = BaseCardView.LayoutParams(cardWidth, cardHeight)
        }
        return ViewHolder(imageCardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        val anime = item as AnimeData
        with(viewHolder?.view as ImageCardView) {
            mainImageView.load(anime.imageUrl) {
                this.scale(Scale.FIT)
                size(cardWidth, cardHeight)
                allowHardware(false)
            }
            titleText = arrayOf(anime.title, anime.currentEpisode).filter { it.isNotEmpty() }
                .joinToString("\n")

        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        with(viewHolder?.view as ImageCardView) {
            mainImage = null
        }
    }
}