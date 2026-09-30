package com.hnrzzin.granaxp.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    private val functions: FirebaseFunctions = FirebaseFunctionsProvider.instance

    suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    suspend fun getUserProfile(userId: String): UserModel? {
        val snapshot = usersCollection.document(userId).get().await()
        return snapshot.toObject(UserModel::class.java)
    }

    suspend fun loginWithEmail(email: String, pass: String): String {

        Log.d("TraceLogin", "Antes do signIn")

        val result =
            auth.signInWithEmailAndPassword(email, pass).await()

        Log.d("TraceLogin", "Depois do signIn")

        return result.user!!.uid
    }

    suspend fun registerWithEmail(email: String, pass: String): String {
        Log.d("TraceRegister", "AuthRepository: Solicitando createUserWithEmailAndPassword...")
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        val uid = result.user?.uid ?: throw Exception("Falha ao criar conta no Auth.")
        Log.d("TraceRegister", "AuthRepository: UID criado: $uid | Projeto Firebase Auth: ${auth.app.options.projectId}")
        return uid
    }

    suspend fun createUserProfile(user: UserModel) {
        val docRef = usersCollection.document(user.id)
        Log.d("TraceRegister", "AuthRepository: Iniciando gravação.")
        Log.d("TraceRegister", "AuthRepository: Projeto Firestore: ${firestore.app.options.projectId}")
        Log.d("TraceRegister", "AuthRepository: Caminho de gravação (set): ${docRef.path}")
        Log.d("TraceRegister", "AuthRepository: Dados exatos sendo enviados: $user")

        try {
            docRef.set(user).await()
            Log.d("TraceRegister", "AuthRepository: GRAVAÇÃO CONFIRMADA! await() liberado para o caminho ${docRef.path}.")
        } catch (e: Exception) {
            Log.e("TraceRegister", "AuthRepository: FALHA na gravação para o caminho ${docRef.path}.", e)
            throw e
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun deleteAccount() {
        checkNotNull(auth.currentUser) { "Nenhum usuário autenticado." }
        val result = functions.getHttpsCallable("deleteAccount").call().await().data as? Map<*, *>
        check(result?.get("deleted") == true) { "A exclusão não foi confirmada pelo servidor." }
        auth.signOut()
    }
}
