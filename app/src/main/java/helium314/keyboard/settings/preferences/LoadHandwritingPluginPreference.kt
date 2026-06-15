// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.filePicker
import java.io.File
import androidx.annotation.DrawableRes

@Composable
fun LoadHandwritingPluginPreference(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    onSuccess: (() -> Unit)? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    
    val hasPlugin = HandwritingLoader.hasPlugin(ctx)

    val launcher = filePicker { uri ->
        val success = HandwritingLoader.importPlugin(ctx, uri)
        showDialog = false
        if (success) {
            FeedbackManager.message(ctx, R.string.load_handwriting_plugin_success)
            onSuccess?.invoke()
        } else {
            FeedbackManager.message(ctx, R.string.load_handwriting_plugin_failed)
        }
    }

    Preference(
        name = title,
        description = summary,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = {
                if (hasPlugin) {
                    HandwritingLoader.removePlugin(ctx)
                    FeedbackManager.message(ctx, "Handwriting plugin removed")
                    onSuccess?.invoke()
                    showDialog = false
                }
            },
            confirmButtonText = if (hasPlugin) stringResource(R.string.load_handwriting_plugin_button_delete) else "",
            title = { Text(stringResource(R.string.load_handwriting_plugin)) },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.load_handwriting_plugin_message))
                }
            },
            neutralButtonText = if (!hasPlugin) stringResource(R.string.load_handwriting_plugin_button_load) else null,
            onNeutral = {
                if (!hasPlugin) {
                    showDialog = false
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/vnd.android.package-archive")
                    try {
                        launcher.launch(intent)
                    } catch (e: Exception) {
                        try {
                            intent.type = "*/*"
                            launcher.launch(intent)
                        } catch (_: Exception) {}
                    }
                }
            }
        )
    }
}
