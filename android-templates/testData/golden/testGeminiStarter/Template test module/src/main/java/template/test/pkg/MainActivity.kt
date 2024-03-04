package template.test.pkg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ai.client.generativeai.GenerativeModel
import template.test.pkg.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val generativeModel = GenerativeModel(
                        modelName = "gemini-pro",
                        apiKey = BuildConfig.apiKey
                    )
                    val viewModel = SummarizeViewModel(generativeModel)
                    SummarizeRoute(viewModel)
                }
            }
        }
    }
}

@Composable
internal fun SummarizeRoute(
    summarizeViewModel: SummarizeViewModel = viewModel()
) {
    val summarizeUiState by summarizeViewModel.uiState.collectAsState()

    SummarizeScreen(summarizeUiState, onSummarizeClicked = { inputText ->
        summarizeViewModel.summarize(inputText)
    })
}

@Composable
fun SummarizeScreen(
    uiState: SummarizeUiState = SummarizeUiState.Initial,
    onSummarizeClicked: (String) -> Unit = {}
) {
    var prompt by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .padding(all = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row {
            TextField(
                value = prompt,
                label = { Text(stringResource(R.string.summarize_label)) },
                placeholder = { Text(stringResource(R.string.summarize_hint)) },
                onValueChange = { prompt = it },
                modifier = Modifier
                    .weight(8f)
            )
            TextButton(
                onClick = {
                    if (prompt.isNotBlank()) {
                        onSummarizeClicked(prompt)
                    }
                },

                modifier = Modifier
                    .weight(2f)
                    .padding(all = 4.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text(stringResource(R.string.action_go))
            }
        }
        when (uiState) {
            SummarizeUiState.Initial -> {
                // Nothing is shown
            }

            SummarizeUiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(all = 8.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    CircularProgressIndicator()
                }
            }

            is SummarizeUiState.Success -> {
                Row(modifier = Modifier.padding(all = 8.dp)) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = "Person Icon"
                    )
                    Text(
                        text = uiState.outputText,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            is SummarizeUiState.Error -> {
                Text(
                    text = uiState.errorMessage,
                    color = Color.Red,
                    modifier = Modifier.padding(all = 8.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showSystemUi = true)
fun SummarizeScreenPreview() {
    SummarizeScreen()
}