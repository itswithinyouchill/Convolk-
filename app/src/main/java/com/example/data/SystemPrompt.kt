package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_prompts")
data class SystemPrompt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val promptText: String,
    val isBuiltIn: Boolean = false
)
