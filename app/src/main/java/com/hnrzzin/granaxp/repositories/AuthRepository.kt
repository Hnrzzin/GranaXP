package com.hnrzzin.granaxp.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    // Retorna o ID do usuário logado, ou null se a sessão expirou/não existir
    suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    // Busca o perfil completo do usuário no banco de dados
    suspend fun getUserProfile(userId: String): UserModel? {
        val snapshot = usersCollection.document(userId).get().await()
        return snapshot.toObject(UserModel::class.java)
    }

    // Faz o login e retorna o ID do usuário autenticado
    suspend fun loginWithEmail(email: String, pass: String): String {
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        return result.user?.uid ?: throw Exception("Usuário não encontrado após login.")
    }

    // Cria a conta no Firebase Auth e retorna o ID gerado
    suspend fun registerWithEmail(email: String, pass: String): String {
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        return result.user?.uid ?: throw Exception("Falha ao criar conta no Auth.")
    }

    // Salva o modelo de usuário recém-criado no Firestore
    suspend fun createUserProfile(user: UserModel) {
        usersCollection.document(user.id).set(user).await()
    }

    // Encerra a sessão
    fun logout() {
        auth.signOut()
    }

    // Deleta os dados do Firestore e, em seguida, a conta do Auth
    suspend fun deleteAccount() {
        auth.currentUser?.let { user ->
            usersCollection.document(user.uid).delete().await()
            user.delete().await()
        }
    }
}