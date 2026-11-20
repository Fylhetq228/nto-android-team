package ru.nto.guestmanagement.mobile.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import ru.nto.guestmanagement.mobile.data.CreateRequestBody
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.toApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val phonePattern = Regex("\\+7\\d{10}")
private val inputDateFormatter =
    DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
private val inputTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

private fun parseInputDate(value: String): LocalDate? =
    try { LocalDate.parse(value.trim(), inputDateFormatter) } catch (e: Exception) { null }

private fun parseInputTime(value: String): LocalTime? =
    try { LocalTime.parse(value.trim(), inputTimeFormatter) } catch (e: Exception) { null }

private fun validateAll(
    fullName: String, phone: String, organization: String, purpose: String,
    date: String, timeFrom: String, timeTo: String, now: LocalDateTime,
): Map<String, Int> {
    val errors = LinkedHashMap<String, Int>()
    if (fullName.trim().length !in 5..150) errors["fullName"] = R.string.form_error_full_name
    if (!phonePattern.matches(phone.trim())) errors["phone"] = R.string.form_error_phone
    if (organization.trim().length > 150) errors["organization"] = R.string.form_error_organization
    if (purpose.trim().length !in 5..200) errors["purpose"] = R.string.form_error_purpose
    val parsedDate = parseInputDate(date)
    if (parsedDate == null) errors["date"] = R.string.form_error_date
    val from = parseInputTime(timeFrom)
    if (from == null) errors["validFrom"] = R.string.form_error_time_from_format
    val to = parseInputTime(timeTo)
    if (to == null) errors["validTo"] = R.string.form_error_time_to_format
    if (parsedDate != null && from != null) {
        val validFrom = LocalDateTime.of(parsedDate, from)
        if (validFrom.isBefore(now)) errors["validFrom"] = R.string.form_error_time_from_past
        if (to != null) {
            val validTo = LocalDateTime.of(parsedDate, to)
            if (!validTo.isAfter(validFrom)) errors["validTo"] = R.string.form_error_time_to_order
        }
    }
    return errors
}

class RequestFormViewModel(private val container: AppContainer) : ViewModel() {
    private val api = RequestsApi(container.httpClient)

    var submitting by mutableStateOf(false)
    var commonErrorRes by mutableStateOf<Int?>(null)
    var serverErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var sessionExpired by mutableStateOf(false)

    fun submit(body: CreateRequestBody, onCreated: () -> Unit) {
        if (submitting) return
        submitting = true
        commonErrorRes = null
        serverErrors = emptyMap()
        viewModelScope.launch {
            val code = container.session.current()
            if (code == null) {
                sessionExpired = true
                submitting = false
                return@launch
            }
            when (val result = api.create(code, body)) {
                is ApiResult.Success -> {
                    submitting = false
                    onCreated()
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpired = true
                    submitting = false
                }
                is ApiResult.FieldErrors -> {
                    serverErrors = result.errors
                    submitting = false
                }
                is ApiResult.Conflict -> {
                    commonErrorRes = R.string.form_error_duplicate
                    submitting = false
                }
                is ApiResult.Network -> {
                    commonErrorRes = R.string.form_error_network
                    submitting = false
                }
                else -> {
                    commonErrorRes = R.string.form_error_other
                    submitting = false
                }
            }
        }
    }
}

@Composable
fun RequestFormScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val vm: RequestFormViewModel = containerViewModel { RequestFormViewModel(container) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var organization by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var timeFrom by rememberSaveable { mutableStateOf("") }
    var timeTo by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.sessionExpired) { if (vm.sessionExpired) onSessionExpired() }

    val clientErrors = if (attempted) {
        validateAll(fullName, phone, organization, purpose, date, timeFrom, timeTo,
            container.timeProvider.now())
    } else {
        emptyMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.form_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.testTag(TestIds.FORM_BACK)) {
                Text(stringResource(R.string.action_back))
            }
        }
        FormTextField(
            tag = TestIds.FORM_FULL_NAME,
            label = R.string.form_full_name,
            value = fullName,
            onChange = { fullName = it; vm.serverErrors = vm.serverErrors - "fullName" },
            errorText = clientErrors["fullName"]?.let { stringResource(it) } ?: vm.serverErrors["fullName"],
            errorTag = TestIds.FORM_ERROR_FULL_NAME,
        )
        FormTextField(
            tag = TestIds.FORM_PHONE,
            label = R.string.form_phone,
            value = phone,
            onChange = { phone = it; vm.serverErrors = vm.serverErrors - "phone" },
            errorText = clientErrors["phone"]?.let { stringResource(it) } ?: vm.serverErrors["phone"],
            errorTag = TestIds.FORM_ERROR_PHONE,
        )
        FormTextField(
            tag = TestIds.FORM_ORGANIZATION,
            label = R.string.form_organization,
            value = organization,
            onChange = { organization = it; vm.serverErrors = vm.serverErrors - "organization" },
            errorText = clientErrors["organization"]?.let { stringResource(it) } ?: vm.serverErrors["organization"],
            errorTag = TestIds.FORM_ERROR_ORGANIZATION,
        )
        FormTextField(
            tag = TestIds.FORM_PURPOSE,
            label = R.string.form_purpose,
            value = purpose,
            onChange = { purpose = it; vm.serverErrors = vm.serverErrors - "purpose" },
            errorText = clientErrors["purpose"]?.let { stringResource(it) } ?: vm.serverErrors["purpose"],
            errorTag = TestIds.FORM_ERROR_PURPOSE,
        )
        FormTextField(
            tag = TestIds.FORM_DATE,
            label = R.string.form_date,
            value = date,
            onChange = { date = it; vm.serverErrors = vm.serverErrors - "date" },
            errorText = clientErrors["date"]?.let { stringResource(it) },
            errorTag = TestIds.FORM_ERROR_DATE,
        )
        FormTextField(
            tag = TestIds.FORM_TIME_FROM,
            label = R.string.form_time_from,
            value = timeFrom,
            onChange = { timeFrom = it; vm.serverErrors = vm.serverErrors - "validFrom" },
            errorText = clientErrors["validFrom"]?.let { stringResource(it) } ?: vm.serverErrors["validFrom"],
            errorTag = TestIds.FORM_ERROR_VALID_FROM,
        )
        FormTextField(
            tag = TestIds.FORM_TIME_TO,
            label = R.string.form_time_to,
            value = timeTo,
            onChange = { timeTo = it; vm.serverErrors = vm.serverErrors - "validTo" },
            errorText = clientErrors["validTo"]?.let { stringResource(it) } ?: vm.serverErrors["validTo"],
            errorTag = TestIds.FORM_ERROR_VALID_TO,
        )
        vm.commonErrorRes?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(TestIds.FORM_ERROR_COMMON),
            )
        }
        if (vm.submitting) {
            CircularProgressIndicator(modifier = Modifier.testTag(TestIds.FORM_LOADING))
        }
        Button(
            onClick = {
                attempted = true
                val errors = validateAll(
                    fullName, phone, organization, purpose, date, timeFrom, timeTo,
                    container.timeProvider.now(),
                )
                if (errors.isEmpty()) {
                    val parsedDate = parseInputDate(date)!!
                    val body = CreateRequestBody(
                        fullName = fullName.trim(),
                        phone = phone.trim(),
                        organization = organization.trim().ifEmpty { null },
                        purpose = purpose.trim(),
                        validFrom = LocalDateTime.of(parsedDate, parseInputTime(timeFrom)!!)
                            .withSecond(0).withNano(0).toApi(),
                        validTo = LocalDateTime.of(parsedDate, parseInputTime(timeTo)!!)
                            .withSecond(0).withNano(0).toApi(),
                    )
                    vm.submit(body, onCreated)
                }
            },
            enabled = !vm.submitting,
            modifier = Modifier.testTag(TestIds.FORM_SUBMIT),
        ) {
            Text(stringResource(R.string.form_submit))
        }
    }
}

@Composable
private fun FormTextField(
    tag: String,
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    errorText: String?,
    errorTag: String,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            isError = errorText != null,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(errorTag),
            )
        }
    }
}
