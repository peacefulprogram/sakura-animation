package com.jing.sakura.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.compose.screen.HomeScreen
import com.jing.sakura.compose.theme.SakuraTheme
import org.koin.android.ext.android.get


class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: HomeViewModel = get()
        setContent {
            SakuraTheme {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(
                            dimensionResource(id = R.dimen.screen_h_padding),
                            dimensionResource(id = R.dimen.screen_v_padding)
                        )
                        .fillMaxSize()
                ) {
                    CompositionLocalProvider(
                        androidx.tv.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                    ) {
                        HomeScreen(viewModel)
                    }

                }
            }
        }
    }
}