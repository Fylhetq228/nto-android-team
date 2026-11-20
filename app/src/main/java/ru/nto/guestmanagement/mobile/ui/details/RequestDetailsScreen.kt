package ru.nto.guestmanagement.mobile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestDetails
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.formatDateTime
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

class RequestDetailsViewModel(
    private val container: AppContainer,
    private val requestId: Long,
) : ViewModel() {
    private val api = RequestsApi(container.httpClient)
    private val gate = RefreshGate()

    var details by mutableStateOf<RequestDetails?>(null)
    var initialError by mutableStateOf(false)
    var offline by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var sessionExpired by mutableStateOf(false)
    var cancelErrorRes by mutableStateOf<Int?>(null)
    var cancelInFlight by mutableStateOf(false)

    fun refresh() {
        gate.launch(viewModelScope) { loadInternal() }
    }

    fun cancel() {
        if (cancelInFlight) return
        cancelInFlight = true
        cancelErrorRes = null
        viewModelScope.launch {
            val code = container.session.current()
            if (code == null) {
                sessionExpired = true
                cancelInFlight = false
                return@launch
            }
            when (val result = api.cancel(code, requestId)) {
                is ApiResult.Success -> {
                    details = result.value
                    cancelErrorRes = null
                }
                is ApiResult.Conflict -> {
                    cancelErrorRes = R.string.details_cancel_conflict
                    refresh()
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpired = true
                }
                is ApiResult.Network -> {
                    cancelErrorRes = R.string.details_cancel_network
                }
                else -> {
                    cancelErrorRes = R.string.details_cancel_other
                }
            }
            cancelInFlight = false
        }
    }

    private suspend fun loadInternal() {
        loading = true
        try {
            val code = container.session.current()
            if (code == null) {
                sessionExpired = true
                return
            }
            when (val result = api.get(code, requestId)) {
                is ApiResult.Success -> {
                    details = result.value
                    initialError = false
                    offline = false
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpired = true
                }
                else -> {
                    if (details == null) initialError = true else offline = true
                }
            }
        } finally {
            loading = false
        }
    }
}

@Composable
fun RequestDetailsScreen(
    container: AppContainer,
    requestId: Long,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val vm: RequestDetailsViewModel = containerViewModel { RequestDetailsViewModel(container, requestId) }
    var confirmOpen by rememberSaveable { mutableStateOf(false) }

    ResumeEffect { vm.refresh() }
    LaunchedEffect(vm.sessionExpired) { if (vm.sessionExpired) onSessionExpired() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.refresh() }, modifier = Modifier.testTag(TestIds.DETAILS_REFRESH)) {
                Text(stringResource(R.string.action_refresh))
            }
            TextButton(onClick = onBack, modifier = Modifier.testTag(TestIds.DETAILS_BACK)) {
                Text(stringResource(R.string.action_back))
            }
        }
        if (vm.loading) {
            CircularProgressIndicator()
        }
        if (vm.initialError) {
            Text(
                text = stringResource(R.string.details_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.DETAILS_ERROR),
            )
            Button(onClick = { vm.refresh() }, modifier = Modifier.testTag(TestIds.DETAILS_RETRY)) {
                Text(stringResource(R.string.action_retry))
            }
        } else if (vm.offline) {
            Text(
                text = stringResource(R.string.offline),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.DETAILS_OFFLINE),
            )
        }
        vm.details?.let { d ->
            Text(text = d.id.toString(), modifier = Modifier.testTag(TestIds.DETAILS_ID))
            Text(text = d.fullName, modifier = Modifier.testTag(TestIds.DETAILS_NAME))
            Text(text = d.phone, modifier = Modifier.testTag(TestIds.DETAILS_PHONE))
            d.organization?.let {
                Text(text = it, modifier = Modifier.testTag(TestIds.DETAILS_ORGANIZATION))
            }
            Text(text = d.purpose, modifier = Modifier.testTag(TestIds.DETAILS_PURPOSE))
            Text(text = formatDateTime(d.validFrom), modifier = Modifier.testTag(TestIds.DETAILS_VALID_FROM))
            Text(text = formatDateTime(d.validTo), modifier = Modifier.testTag(TestIds.DETAILS_VALID_TO))
            StatusLabel(d.status, tag = TestIds.DETAILS_STATUS)
            Text(text = formatDateTime(d.createdAt), modifier = Modifier.testTag(TestIds.DETAILS_CREATED_AT))
            Text(text = formatDateTime(d.updatedAt), modifier = Modifier.testTag(TestIds.DETAILS_UPDATED_AT))
            if (d.status == RequestStatus.REJECTED) {
                Text(text = stringResource(R.string.details_label_comment))
                Text(text = d.comment ?: "", modifier = Modifier.testTag(TestIds.DETAILS_COMMENT))
            }
            vm.cancelErrorRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_ERROR),
                )
            }
            if (d.status == RequestStatus.PENDING) {
                Button(
                    onClick = { confirmOpen = true },
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL),
                ) {
                    Text(stringResource(R.string.details_cancel))
                }
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(stringResource(R.string.details_cancel_title)) },
            text = { Text(stringResource(R.string.details_cancel_text)) },
            confirmButton = {
                Button(
                    onClick = { confirmOpen = false; vm.cancel() },
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_CONFIRM),
                ) {
                    Text(stringResource(R.string.details_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmOpen = false },
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_DISMISS),
                ) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }
}
