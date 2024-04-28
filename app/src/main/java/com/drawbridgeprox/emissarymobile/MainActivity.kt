package com.drawbridgeprox.emissarymobile

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

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

    fun establishMTLSConnection(context: Context, drawbridgeAddress: String, drawbridgePort: Int, certificatePath: String) {
        try {
            // Load the CA certificate
            val caInputStream = FileInputStream(certificatePath)
            val certFactory = CertificateFactory.getInstance("X.509")
            val caCert = certFactory.generateCertificate(caInputStream)
            caInputStream.close()

            // Create a KeyStore containing our trusted CAs
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("ca", caCert)

            // Create a TrustManager that trusts the CAs in our KeyStore
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
            tmf.init(keyStore)

            // Create an SSLContext that uses our TrustManager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)

            // Create an SSLSocketFactory from the SSLContext
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

            // Create an SSLSocket using the SSLSocketFactory
            val sslSocket = sslSocketFactory.createSocket(drawbridgeAddress, drawbridgePort) as SSLSocket

            // Configure the SSLSocket
            val sslParameters = sslSocket.sslParameters
            sslParameters.endpointIdentificationAlgorithm = "HTTPS"
            sslSocket.sslParameters = sslParameters

            sslSocket.startHandshake()

            // Use the SSLSocket for communication
            // ...
        } catch (e: Exception) {
            Log.e("MTLSHelper", "Error establishing mTLS connection", e)
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
    val context = LocalContext.current

    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                saveZipToAppDirectory(context, it)
            }
        }
    )

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

        Button(
            onClick = {
                selectZipLauncher.launch(arrayOf("application/zip"))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Select ZIP")
        }
    }
}

private fun saveZipToAppDirectory(context: Context, uri: Uri) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = Files.createTempFile(context.cacheDir.toPath(), "temp", ".zip")
        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)

        val zipFile = ZipFile(tempFile.toFile())
        val entries = zipFile.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val filePath = context.filesDir.toPath().resolve(entry.name)

            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.parent)
                val inputStream = zipFile.getInputStream(entry)
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                inputStream.close()
            }
        }

        zipFile.close()
        Files.delete(tempFile)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
