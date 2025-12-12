package com.example.bilidownloader.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("UPDATE users SET isLogin = 0")
    suspend fun clearAllLoginStatus()

    @Query("UPDATE users SET isLogin = 1 WHERE mid = :mid")
    suspend fun setLoginStatus(mid: Long)

    // 获取当前登录的用户
    @Query("SELECT * FROM users WHERE isLogin = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?
}