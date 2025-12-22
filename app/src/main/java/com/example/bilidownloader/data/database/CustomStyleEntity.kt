package com.example.bilidownloader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_styles")
data class CustomStyleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val prompt: String
)