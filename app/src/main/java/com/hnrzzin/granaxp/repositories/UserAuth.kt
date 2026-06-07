package com.hnrzzin.granaxp.repositories
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class UserAuth {

    val auth = FirebaseAuth.getInstance()

    suspend fun createAuthUserWithEmail(
        email: String,
        password: String
    ): FirebaseUser?{
        try {

            val login = auth.createUserWithEmailAndPassword(email,password).await()
            println("Sucesso ao autenticar usuário")
            val user = login.user
            return user
        }catch (e: Exception){
            println("Falha ao autenticar usuário $e")
            return null

        }
    }
    suspend fun deleteUser(

    ){
        try {
            val user = auth.currentUser
            user?.delete()?.await()
            println("Sucesso ao deletar usuário")
        }catch (e: Exception){
            println("Falha ao deletar usuário $e")

        }
    }

    suspend fun loginWithGoogle(credential: Credential)
    : FirebaseUser?
    {
        if (credential is CustomCredential && credential.type == "TYPE_GOOGLE_ID_TOKEN_CREDENTIAL") {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            // Cria a credencial do Firebase com o Token do Google
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            // Faz login no Firebase
            try{
                val login = auth.signInWithCredential(firebaseCredential).await()
                println("Sucesso ao fazer login com a conta Google!")
                val user = login.user
                return user
            }catch (e: Exception){
                println("Falha ao fazer login com a conta Google $e!")
                return null
            }

        }
        return null
    }

    suspend fun loginWithEmail(
        email: String,
        password: String
    ): FirebaseUser?{
        try {
            val login = auth.signInWithEmailAndPassword(email,password).await()
            println("Sucesso ao fazer login do usuário")
            val user = login.user
            return user
        }catch (e: Exception){
            println("Falha ao fazer login do usuário $e")
            return null

        }
    }
}