package com.jing.sakura.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R


@Composable
fun Loading(text: String = "Loading"): Unit {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = text)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorTip(message: String, retry: () -> Unit = { }) {
    val focusRequester = remember {
        FocusRequester()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message)
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = retry,
            modifier = Modifier
                .focusRequester(focusRequester),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                    shape = MaterialTheme.shapes.extraLarge
                )
            ),
            shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.extraLarge),
            scale = ButtonScale.None,
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                focusedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        ) {
            Text(text = stringResource(R.string.button_retry))
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}