package com.gitsync.ui.synclog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SyncLogViewModel @Inject constructor(
    syncLogDao: SyncLogDao
) : ViewModel() {

    val logs: StateFlow<List<SyncLogEntity>> = syncLogDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
