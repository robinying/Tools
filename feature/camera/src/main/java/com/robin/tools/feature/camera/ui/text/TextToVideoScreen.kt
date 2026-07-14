package com.robin.tools.feature.camera.ui.text

import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.camera.R
import com.robin.tools.feature.camera.editor.TextCardVideoGenerator
import com.robin.tools.feature.camera.storage.CameraFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToVideoScreen(
    onBack: () -> Unit,
    onGenerated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val fileManager = remember { CameraFileManager(context) }
    var text by remember { mutableStateOf("") }
    var durationSec by remember { mutableFloatStateOf(3f) }
    var busy by remember { mutableStateOf(false) }

    fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.camera_text_to_video_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.record_back)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimension.lg),
            verticalArrangement = Arrangement.spacedBy(Dimension.md)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text(stringResource(R.string.ttv_hint)) },
                enabled = !busy
            )
            Text(
                stringResource(R.string.ttv_duration, durationSec.toInt()),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = durationSec,
                onValueChange = { durationSec = it },
                valueRange = 1f..10f,
                steps = 8,
                enabled = !busy
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (text.isBlank()) {
                        Toast.makeText(context, R.string.ttv_empty, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    hideKeyboard()
                    busy = true
                    Log.i("TextCardVideo", "generate clicked textLen=${text.trim().length} duration=${durationSec.toInt()}")
                    scope.launch {
                        val out = fileManager.createOutputFile("textcard")
                        Log.i("TextCardVideo", "output path=${out.absolutePath}")
                        val ok = withContext(Dispatchers.Default) {
                            TextCardVideoGenerator().generate(
                                text = text.trim(),
                                outputFile = out,
                                durationSec = durationSec.toInt()
                            )
                        }
                        busy = false
                        Log.i("TextCardVideo", "generate result ok=$ok size=${out.length()}")
                        if (ok) {
                            Toast.makeText(context, R.string.ttv_done, Toast.LENGTH_SHORT).show()
                            onGenerated(out.absolutePath)
                        } else {
                            Toast.makeText(context, R.string.ttv_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.ttv_generate))
                }
            }
            Text(
                stringResource(R.string.ttv_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
