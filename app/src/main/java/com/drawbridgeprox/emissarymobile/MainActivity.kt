package com.drawbridgeprox.emissarymobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drawbridgeprox.emissarymobile.ui.theme.EmissaryMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmissaryMobileTheme {}
            DrawbridgeApp(
                connectToDrawbridge = { address ->
                    // TODO: Implement the actual logic to connect to the Drawbridge
                    // This is where you would typically make an API call or perform any necessary operations
                    // For demonstration purposes, we'll just display a Toast message
                    // test
                    Toast.makeText(this, "Connecting to Drawbridge at $address", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EmissaryMobileTheme{}
}

@Composable
fun DrawbridgeApp(connectToDrawbridge: (String) -> Unit) {
    val address = remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        OutlinedTextField(
            value = address.value,
            onValueChange = { address.value = it },
            label = { Text("Drawbridge Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { connectToDrawbridge(address.value) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Connect to Drawbridge")
        }
    }
}
