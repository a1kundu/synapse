package `in`.arijitk.synapse.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.arijitk.synapse.ui.chat.ChatScreen
import `in`.arijitk.synapse.ui.chat.ChatViewModel
import `in`.arijitk.synapse.ui.chat.ModelSelectorChip
import org.jetbrains.compose.resources.painterResource
import synapse.composeapp.generated.resources.Res
import synapse.composeapp.generated.resources.app_icon

/**
 * Home Shell - Main container with top bar and chat screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    onNavigateToSettings: () -> Unit,
) {
    val chatViewModel: ChatViewModel = viewModel { ChatViewModel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = "Synapse",
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                },
                actions = {
                    ModelSelectorChip(
                        selectedModel = chatViewModel.selectedModel,
                        onModelSelected = chatViewModel::selectModel,
                        models = chatViewModel.availableModels,
                        isLoading = chatViewModel.isLoadingModels,
                        onRefresh = chatViewModel::refreshModels,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        ChatScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            viewModel = chatViewModel,
        )
    }
}
