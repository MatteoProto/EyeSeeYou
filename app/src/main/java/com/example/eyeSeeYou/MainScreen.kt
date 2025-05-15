package com.example.eyeSeeYou

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(vocalAssistant: VocalAssistant) {
    Scaffold(
        containerColor = Color(0xFF121212) // dark gray personalizzato
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderTitle()
            Spacer(modifier = Modifier.height(60.dp))
            SpeakButton(vocalAssistant)
        }
    }
}

@Composable
fun HeaderTitle() {
    Text(
        text = "EyeSeeYou",
        fontSize = 42.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White
    )
}

@Composable
fun SpeakButton(vocalAssistant: VocalAssistant) {
    Button(
        onClick = {
            vocalAssistant.speak("You are inside Eye See You application. Stay safe.")
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
    ) {
        Text(
            text = "IN APP",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}