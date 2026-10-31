package com.example.superznalci

import android.view.View
import android.widget.ImageView
import coil.load
import coil.transform.RoundedCornersTransformation

object ImageQuiz {

    fun url(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else ApiConfig.IMAGE_BASE_URL + path.trimStart('/')

    fun bind(view: ImageView, path: String?) {
        if (path.isNullOrBlank()) {
            view.setImageDrawable(null)
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.load(url(path)) {
            crossfade(true)
            transformations(RoundedCornersTransformation(QuizUi.dp(view.context, 16).toFloat()))
            listener(onError = { _, _ -> view.visibility = View.GONE })
        }
    }
}