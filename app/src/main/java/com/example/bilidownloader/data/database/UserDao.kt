package com.example.bilidownloader.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 用户账号 DAO.
 * 支持多账号管理和切换.
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    /** 重置所有用户的登录状态 (用于登出或切换前) */
    @Query("UPDATE users SET isLogin = 0")
    suspend fun clearAllLoginStatus()

    /** 设置指定用户为当前活跃账号 */
    @Query("UPDATE users SET isLogin = 1 WHERE mid = :mid")
    suspend fun setLoginStatus(mid: Long)

    @Query("SELECT * FROM users WHERE isLogin = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?
}