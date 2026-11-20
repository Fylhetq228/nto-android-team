package ru.nto.guestmanagement.mobile.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel

private val codePattern = Regex("[A-Za-z0-9]{4}")

class LoginViewModel(private val container: AppContainer) : ViewModel() {
    private val api = RequestsApi(container.httpClient)

    var loading by mutableStateOf(false)
    var errorRes by mutableStateOf<Int?>(null)

    suspend fun checkSaved(onLoggedIn: () -> Unit) {
        if (container.session.current() != null) onLoggedIn()
    }

    fun login(code: String, onLoggedIn: () -> Unit) {
        if (loading) return
        loading = true
        errorRes = null
        viewModelScope.launch {
            when (api.auth(code)) {
                is ApiResult.Success -> {
                    container.session.save(code)
                    loading = false
                    onLoggedIn()
                }
                is ApiResult.Unauthorized -> {
                    errorRes = R.string.login_error_unknown_code
                    loading = false
                }
                is ApiResult.Network, is ApiResult.Failure -> {
                    errorRes = R.string.login_error_network
                    loading = false
                }
                else -> {
                    errorRes = R.string.login_error_other
                    loading = false
                }
            }
        }
    }
}

@Composable
fun LoginScreen(container: AppContainer, sessionExpired: Boolean, onLoggedIn: () -> Unit) {
    val vm: LoginViewModel = containerViewModel { LoginViewModel(container) }
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.checkSaved(onLoggedIn) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; vm.errorRes = null },
            label = { Text(stringResource(R.string.login_code_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TestIds.AUTH_CODE),
        )
        Button(
            onClick = { vm.login(code, onLoggedIn) },
            enabled = codePattern.matches(code) && !vm.loading,
            modifier = Modifier.testTag(TestIds.AUTH_SUBMIT),
        ) {
            Text(stringResource(R.string.login_submit))
        }
        if (vm.loading) {
            CircularProgressIndicator(modifier = Modifier.testTag(TestIds.AUTH_LOADING))
        }
        vm.errorRes?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.AUTH_ERROR),
            )
        }
        if (sessionExpired) {
            Text(
                text = stringResource(R.string.login_session_expired),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.AUTH_SESSION_EXPIRED),
            )
        }
    }
}
