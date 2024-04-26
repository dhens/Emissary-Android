package com.drawbridgeprox.emissarymobile

import android.content.Context
import android.os.Bundle
import android.util.Log
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
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

    fun downloadFile(context: Context, url: String, drawbridgeAddress: String): String? {
        var filePath: String? = null
        val thread = Thread {
            try {

                val drawbridgeBundleURL = URL(drawbridgeAddress)
                val certFileURL = URL("$drawbridgeBundleURL/")
                val connection = certFileURL.openConnection()
                connection.connect()

                val inputStream = connection.getInputStream()
                val file = File(context.filesDir, "cert-ca.crt")
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.close()
                inputStream.close()

                filePath = file.absolutePath
                Log.d("FileDownloader", "File downloaded successfully: $filePath")
            } catch (e: Exception) {
                Log.e("FileDownloader", "Error downloading file", e)
            }
        }

        thread.start()
        thread.join()

        return filePath
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
