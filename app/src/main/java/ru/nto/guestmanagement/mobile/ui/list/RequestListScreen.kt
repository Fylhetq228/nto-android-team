package ru.nto.guestmanagement.mobile.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.R
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestSummary
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.formatDate
import ru.nto.guestmanagement.mobile.data.formatInterval
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

class RequestListViewModel(private val container: AppContainer) : ViewModel() {
    private val api = RequestsApi(container.httpClient)
    private val gate = RefreshGate()

    var filter by mutableStateOf<RequestStatus?>(null)
    var items by mutableStateOf<List<RequestSummary>?>(null)
    var initialError by mutableStateOf(false)
    var offline by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var sessionExpired by mutableStateOf(false)
    var loggedOut by mutableStateOf(false)

    fun refresh() {
        gate.launch(viewModelScope) { loadInternal() }
    }

    fun selectFilter(newFilter: RequestStatus?) {
        if (filter == newFilter) return
        filter = newFilter
        gate.cancel()
        refresh()
    }

    fun logout() {
        viewModelScope.launch {
            container.session.clear()
            loggedOut = true
        }
    }

    private suspend fun loadInternal() {
        loading = true
        try {
            val code = container.session.current()
            if (code == null) {
                loggedOut = true
                return
            }
            when (val result = api.list(code, filter)) {
                is ApiResult.Success -> {
                    items = result.value
                    initialError = false
                    offline = false
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpired = true
                }
                else -> {
                    if (items == null) initialError = true else offline = true
                }
            }
        } finally {
            loading = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestListScreen(
    container: AppContainer,
    onOpenDetails: (Long) -> Unit,
    onAddRequest: () -> Unit,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val vm: RequestListViewModel = containerViewModel { RequestListViewModel(container) }

    ResumeEffect { vm.refresh() }
    LaunchedEffect(vm.sessionExpired) { if (vm.sessionExpired) onSessionExpired() }
    LaunchedEffect(vm.loggedOut) { if (vm.loggedOut) onLogout() }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.list_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.refresh() }, modifier = Modifier.testTag(TestIds.REQUESTS_REFRESH)) {
                Text(stringResource(R.string.action_refresh))
            }
            TextButton(onClick = onAddRequest, modifier = Modifier.testTag(TestIds.REQUESTS_ADD)) {
                Text(stringResource(R.string.action_add))
            }
            TextButton(onClick = { vm.logout() }, modifier = Modifier.testTag(TestIds.REQUESTS_LOGOUT)) {
                Text(stringResource(R.string.action_logout))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = vm.filter == null,
                onClick = { vm.selectFilter(null) },
                label = { Text(stringResource(R.string.filter_all)) },
                modifier = Modifier.testTag(TestIds.REQUESTS_FILTER_ALL),
            )
            FilterChip(
                selected = vm.filter == RequestStatus.PENDING,
                onClick = { vm.selectFilter(RequestStatus.PENDING) },
                label = { Text(stringResource(R.string.filter_pending)) },
                modifier = Modifier.testTag(TestIds.REQUESTS_FILTER_PENDING),
            )
            FilterChip(
                selected = vm.filter == RequestStatus.APPROVED,
                onClick = { vm.selectFilter(RequestStatus.APPROVED) },
                label = { Text(stringResource(R.string.filter_approved)) },
                modifier = Modifier.testTag(TestIds.REQUESTS_FILTER_APPROVED),
            )
            FilterChip(
                selected = vm.filter == RequestStatus.REJECTED,
                onClick = { vm.selectFilter(RequestStatus.REJECTED) },
                label = { Text(stringResource(R.string.filter_rejected)) },
                modifier = Modifier.testTag(TestIds.REQUESTS_FILTER_REJECTED),
            )
            FilterChip(
                selected = vm.filter == RequestStatus.CANCELLED,
                onClick = { vm.selectFilter(RequestStatus.CANCELLED) },
                label = { Text(stringResource(R.string.filter_cancelled)) },
                modifier = Modifier.testTag(TestIds.REQUESTS_FILTER_CANCELLED),
            )
        }
        if (vm.loading) {
            CircularProgressIndicator()
        }
        if (vm.initialError) {
            Text(
                text = stringResource(R.string.list_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.REQUESTS_ERROR),
            )
        } else if (vm.offline) {
            Text(
                text = stringResource(R.string.offline),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.REQUESTS_OFFLINE),
            )
        }
        val list = vm.items
        if (list != null && !vm.initialError) {
            if (list.isEmpty()) {
                Text(
                    text = stringResource(R.string.list_empty),
                    modifier = Modifier.testTag(TestIds.REQUESTS_EMPTY),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(list) { index, item ->
                        RequestCard(index, item, onOpenDetails)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(index: Int, item: RequestSummary, onOpenDetails: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestIds.requestsPos(index))
            .semantics(mergeDescendants = false) {
                onClick(label = null, action = { onOpenDetails(item.id); true })
            }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = item.fullName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(TestIds.REQUEST_NAME),
        )
        Text(
            text = formatDate(item.validFrom),
            modifier = Modifier.testTag(TestIds.REQUEST_DATE),
        )
        Text(
            text = formatInterval(item.validFrom, item.validTo),
            modifier = Modifier.testTag(TestIds.REQUEST_TIME),
        )
        StatusLabel(item.status, tag = TestIds.REQUEST_STATUS)
    }
}
