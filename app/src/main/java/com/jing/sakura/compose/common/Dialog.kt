package com.jing.sakura.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConfirmDeleteDialog(
    text: String,
    onDeleteClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember {
        FocusRequester()
    }

    AlertDialog(onDismissRequest = onCancel, confirmButton = {
        Button(
            onClick = onDeleteAllClick,
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                focusedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    BorderStroke(
                        2.dp, MaterialTheme.colorScheme.border
                    )
                )
            ),
            shape = ButtonDefaults.shape(shape = androidx.compose.material3.MaterialTheme.shapes.medium)
        ) {
            Text(text = stringResource(R.string.button_delete_all))
        }
    }, dismissButton = {
        Button(
            modifier = Modifier.focusRequester(focusRequester),
            onClick = onDeleteClick,
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                focusedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    BorderStroke(
                        2.dp, MaterialTheme.colorScheme.border
                    )
                )
            ),
            shape = ButtonDefaults.shape(shape = androidx.compose.material3.MaterialTheme.shapes.medium),
        ) {
            Text(text = stringResource(R.string.button_delete))
        }
    }, text = {
        Text(
            text = text, modifier = Modifier.focusable()
        )
    })
    LaunchedEffect(Unit){
        focusRequester.requestFocus()
    }

}