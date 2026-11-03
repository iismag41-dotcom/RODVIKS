package com.rodviks.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodviks.app.ui.theme.BlueDeep
import com.rodviks.app.ui.theme.BluePrimary
import com.rodviks.app.viewmodel.PhoneAuthMode
import com.rodviks.app.viewmodel.PhoneAuthStage
import com.rodviks.app.viewmodel.PhoneAuthUiState
import com.rodviks.app.viewmodel.PhoneAuthViewModel

@Composable
fun PhoneAuthScreen(
    viewModel: PhoneAuthViewModel,
    onAuthorized: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.stage) {
        if (state.stage == PhoneAuthStage.DONE) onAuthorized()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RodviksLogo()
            Spacer(Modifier.height(28.dp))

            AnimatedContent(
                targetState = state.stage,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "authStage"
            ) { stage ->
                when (stage) {
                    PhoneAuthStage.FORM -> FormStep(viewModel, state)
                    PhoneAuthStage.USERNAME -> UsernameStep(viewModel, state)
                    PhoneAuthStage.DONE -> Box(Modifier.height(400.dp))
                }
            }
        }
    }
}

@Composable
private fun RodviksLogo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(BluePrimary, BlueDeep))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "R",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("RODVIKS", style = MaterialTheme.typography.titleLarge, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Быстрый мессенджер",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorText(state: PhoneAuthUiState) {
    state.error?.let { err ->
        Spacer(Modifier.height(8.dp))
        Text(
            err,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MainButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BlueDeep),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, fontSize = 16.sp)
        }
    }
}

// ---------- Login / register form ----------

@Composable
private fun FormStep(viewModel: PhoneAuthViewModel, state: PhoneAuthUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (state.mode == PhoneAuthMode.LOGIN) "Вход" else "Регистрация",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.mode == PhoneAuthMode.LOGIN) "Войдите по номеру телефона"
            else "Создайте аккаунт по номеру телефона",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text("Номер телефона") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            prefix = { Text("+7 ") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (state.mode == PhoneAuthMode.LOGIN) viewModel.submit()
            }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (state.mode == PhoneAuthMode.REGISTER) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.passwordRepeat,
                onValueChange = viewModel::onPasswordRepeatChange,
                label = { Text("Повторите пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        ErrorText(state)
        Spacer(Modifier.height(20.dp))
        MainButton(
            text = if (state.mode == PhoneAuthMode.LOGIN) "Войти" else "Создать аккаунт",
            loading = state.loading,
            onClick = viewModel::submit
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = viewModel::switchMode, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (state.mode == PhoneAuthMode.LOGIN) "Нет аккаунта? Зарегистрироваться"
                else "Уже есть аккаунт? Войти",
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------- First-login username setup ----------

@Composable
private fun UsernameStep(viewModel: PhoneAuthViewModel, state: PhoneAuthUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Придумайте имя",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Другие пользователи найдут вас по этому имени",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Имя пользователя") },
            singleLine = true,
            supportingText = { Text("Латиница, цифры, подчёркивание") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text("Отображаемое имя (необязательно)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        ErrorText(state)
        Spacer(Modifier.height(20.dp))
        MainButton("Продолжить", state.loading, viewModel::saveUsername)
    }
}
