package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.ChatRepository
import com.example.ui.ChatAppMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.ChatViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Retrieve database instance
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = ChatRepository(database.chatDao())
    val factory = ChatViewModelFactory(repository, applicationContext)
    val viewModel: ChatViewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          ChatAppMainScreen(viewModel = viewModel)
        }
      }
    }
  }
}

