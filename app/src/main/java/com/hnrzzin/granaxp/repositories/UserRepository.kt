package com.hnrzzin.granaxp.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class UserRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users")

    suspend fun createUser(user: UserModel) {
        collection.document(userId).set(user).await()
    }

    suspend fun getUser(): UserModel? {
        val docRef = collection.document(userId)
        Log.d("TraceHome", "UserRepository: Iniciando leitura (get) para recuperar usuário.")
        Log.d("TraceHome", "UserRepository: UID: $userId | Projeto: ${db.app.options.projectId}")
        Log.d("TraceHome", "UserRepository: Caminho de leitura: ${docRef.path}")

        try {
            val snapshot = docRef.get().await()
            Log.d("TraceHome", "UserRepository: Resposta do Firebase recebida. Existe? ${snapshot.exists()}")

            if (snapshot.exists()) {
                Log.d("TraceHome", "UserRepository: Snapshot Data puro: ${snapshot.data}")
            } else {
                Log.w("TraceHome", "UserRepository: ALERTA - Documento NÃO EXISTE no banco!")
            }

            val result = snapshot.toObject(UserModel::class.java)
            Log.d("TraceHome", "UserRepository: Resultado após mapeamento (toObject): $result")
            return result
        } catch (e: Exception) {
            Log.e("TraceHome", "UserRepository: Erro durante a leitura no caminho ${docRef.path}", e)
            throw e
        }
    }

    suspend fun getDisplayUser(): UserModel? {
        val stored = getUser() ?: return null
        // A falha de catálogo não bloqueia painel financeiro nem edição do perfil.
        val total = try {
            val learning = LessonRepository(userId)
            educationalXpTotal(learning.getLessons(), learning.getLessonProgress())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("TraceHome", "Catálogo indisponível para XP educacional.", error)
            0
        }
        return educationalDisplayUser(stored, total)
    }

    suspend fun updateName(newName: String) {
        collection.document(userId).update("name", newName).await()
    }

}
