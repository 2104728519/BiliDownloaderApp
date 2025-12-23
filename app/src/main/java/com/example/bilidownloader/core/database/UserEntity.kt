package com.example.bilidownloader.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户账号实体.
 *
 * 存储关键的鉴权信息，包括 Cookie (SESSDATA) 和 CSRF Token (bili_jct).
 * @property isLogin 标记当前是否为活跃账号.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val mid: Long,
    val name: String,
    val face: String,
    val sessData: String,
    val biliJct: String,
    val isLogin: Boolean = false
)