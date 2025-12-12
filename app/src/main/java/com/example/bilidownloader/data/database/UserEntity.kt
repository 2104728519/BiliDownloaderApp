package com.example.bilidownloader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val mid: Long, // B站的用户ID (mid)
    val name: String,          // 昵称
    val face: String,          // 头像 URL
    val sessData: String,      // 核心 Cookie
    val biliJct: String,       // CSRF Token (退出登录用)
    val isLogin: Boolean = false // 标记是否为当前活跃账号
)