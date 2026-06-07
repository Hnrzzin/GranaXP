package com.hnrzzin.granaxp.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await

class UserRepository {


    val db = FirebaseFirestore.getInstance()
    val collection = db.collection("Users")

    suspend fun createUser(
        user: UserModel,
    ) {
        try {
            collection.add(user).await()
            println("Sucesso ao adicionar usuário")
        } catch (e: Exception) {
            println("Falha ao adicionar usuário: $e")
        }

    }
    suspend fun updateXP(
        userId: String,
        newXP: Int

    ) {
        val map = mapOf("userXP" to newXP)
        try {
            collection
                .document(userId)
                .update(map).await()
            println("Sucesso ao atualizar o XP")
        } catch (e: Exception) {
            println("Falha ao atualizar o XP: $e")
        }
    }

    suspend fun updateLevel(
        userId: String,
        newLevel: Int
    ) {
        val map = mapOf("userLevel" to newLevel)
        try {
            collection
                .document(userId)
                .update(map).await()
            println("Sucesso ao atualizar o nível da conta")
        } catch (e: Exception) {
            println("Falha ao atualizar o nível da conta: $e")
        }
    }

    suspend fun deleteUser(
        userId: String
    ) {
        try {
            collection
                .document(userId)
                .delete().await()
            println("Sucesso ao deletar o usuário")
        } catch (e: Exception) {
            println("Falha ao deletar o usuário: $e")
        }
    }

    suspend fun getUser(
        userId: String
    ): UserModel? {
        try {
            val getAll = collection.document(userId)
                .get().await()
            if (getAll.exists()) {
                val user = getAll.toObject(UserModel::class.java)
                println("Sucesso ao buscar o usuário")
                return user
            } else {
                println("Nenhum usuário encontrado!")
                return null
            }

        } catch (e: Exception) {
            println("Falha ao buscar o usuário $e")
            return null
        }
    }
}
