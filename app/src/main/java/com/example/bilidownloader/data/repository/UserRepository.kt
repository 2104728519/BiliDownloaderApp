package com.example.bilidownloader.data.repository

import com.example.bilidownloader.data.database.UserDao
import com.example.bilidownloader.data.database.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户账号仓库.
 *
 * 负责管理多账号数据的增删改查，以及维护当前活跃账号的状态 ("Login Status").
 * 实际上是对 [UserDao] 的一层封装，遵循单一数据源原则.
 */
class UserRepository(private val userDao: UserDao) {

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    suspend fun getCurrentUser(): UserEntity? = userDao.getCurrentUser()

    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)

    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)

    /** 清除所有用户的登录标记 (用于登出/切换) */
    suspend fun clearAllLoginStatus() = userDao.clearAllLoginStatus()

    /** 激活指定用户的登录状态 */
    suspend fun setLoginStatus(mid: Long) = userDao.setLoginStatus(mid)
}