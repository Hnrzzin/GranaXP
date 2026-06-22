package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await

class UserRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users")

    suspend fun createUser(user: UserModel) {
        // Removemo o try/catch daqui de dentro para que a ViewModel
        // capture a falha e consiga exibir uma mensagem de erro na tela
        collection.document(userId).set(user).await()
    }

    suspend fun getUser(): UserModel? {
        val result = collection.document(userId).get().await()
        return result.toObject(UserModel::class.java)
    }

    // NOVA FUNÇÃO: Atualiza o modelo inteiro do usuário de uma vez.
    // Muito útil para a lógica de Level Up que altera XP, Nível e meta de XP junta.
    suspend fun updateUser(user: UserModel) {
        collection.document(userId).set(user).await()
    }

    suspend fun updateXP(newXP: Int) {
        collection.document(userId).update("xp", newXP).await()
    }

    suspend fun updateLevel(newLevel: Int) {
        collection.document(userId).update("level", newLevel).await()
    }

    suspend fun updateName(newName: String) {
        collection.document(userId).update("name", newName).await()
    }

    suspend fun deleteUser() {
        collection.document(userId).delete().await()
    }
}