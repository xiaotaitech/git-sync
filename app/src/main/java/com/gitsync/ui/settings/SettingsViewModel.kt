package com.gitsync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    val pat: StateFlow<String> = prefsRepository.getPat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val defaultInterval: StateFlow<Int> = prefsRepository.getDefaultInterval()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun savePat(token: String) {
        viewModelScope.launch { prefsRepository.savePat(token) }
    }

    fun saveDefaultInterval(minutes: Int) {
        viewModelScope.launch { prefsRepository.saveDefaultInterval(minutes) }
    }
}
