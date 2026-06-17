package com.hnrzzin.granaxp.ui.theme.states

import com.hnrzzin.granaxp.model.UserModel

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    data class Success(val user: UserModel) : ProfileState()
    data class Error(val exception: Exception) : ProfileState()
}

sealed class ProfileUpdateState {
    object Idle : ProfileUpdateState()
    object Loading : ProfileUpdateState()
    data class Success(val message: String) : ProfileUpdateState()
    data class Error(val exception: Exception) : ProfileUpdateState()
}
