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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.drawbridgeprox.emissarymobile.ui.theme.EmissaryMobileTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmissaryMobileTheme {
                val options = remember { mutableStateOf<List<String>>(emptyList()) }
                val localServiceProxies = remember { mutableStateOf<Map<String, ServerSocket>>(emptyMap()) }

                DrawbridgeApp(
                    connectToDrawbridge = { context, address ->
                        val tlsConfig = createTlsConfig(context)  // Ensure TLS config is created here
                        lifecycleScope.launch {
                            establishMTLSConnection(context, address, tlsConfig) { serverOptions ->
                                options.value = serverOptions
                                setUpLocalServiceProxies(serverOptions, localServiceProxies.value, address, tlsConfig)
                            }
                        }
                    },
                    options = options.value
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EmissaryMobileTheme{}
}

@Composable
fun DrawbridgeApp(connectToDrawbridge: (Context, String) -> Unit, options: List<String>) {
    val address = remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val drawbridgeFile = context.filesDir.toPath().resolve("bundle/drawbridge.txt")
        if (Files.exists(drawbridgeFile)) {
            address.value = Files.readAllLines(drawbridgeFile).getOrElse(0) { "" }
        }
    }

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
            onClick = {
                selectZipLauncher.launch(arrayOf("application/zip"))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Load Bundle ZIP")
        }

        Button(
            onClick = { connectToDrawbridge(context, address.value) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Connect to Drawbridge")
        }

        LazyColumn {
            items(options) { option ->
                val displayName = option.substring(3).trim()
                Text(text = displayName, modifier = Modifier.padding(vertical = 4.dp))
            }
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

            if (entry.isDirectory) {
                if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                    Files.delete(filePath)
                }
                Files.createDirectories(filePath)
            } else {
                val parentPath = filePath.parent
                if (Files.exists(parentPath) && !Files.isDirectory(parentPath)) {
                    Files.delete(parentPath)
                }
                Files.createDirectories(parentPath)
                val inputStream = zipFile.getInputStream(entry)
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                Log.i("writeFile", filePath.toString())
                inputStream.close()
            }
        }

        zipFile.close()
        Files.delete(tempFile)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun establishMTLSConnection(context: Context, drawbridgeAddress: String, tlsConfig: SSLContext, onResponse: (List<String>) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val sslSocketFactory: SSLSocketFactory = tlsConfig.socketFactory

            val (address, port) = drawbridgeAddress.split(":").let { it[0] to it[1].toInt() }

            val sslSocket = sslSocketFactory.createSocket() as SSLSocket
            sslSocket.connect(InetSocketAddress(address, port), 10000) // Set a 10-second timeout
            sslSocket.startHandshake()

            val inputStream = sslSocket.inputStream
            val outputStream = sslSocket.outputStream

            val writer = BufferedWriter(OutputStreamWriter(outputStream))
            val reader = BufferedReader(InputStreamReader(inputStream))

            writer.write("PS_LIST")
            writer.newLine()
            writer.flush()

            val response = reader.readLine()

            val options = response.split(",").filter { it.isNotBlank() }
            withContext(Dispatchers.Main) {
                onResponse(options)
            }

            reader.close()
            writer.close()
            sslSocket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun setUpLocalServiceProxies(services: List<String>, localServiceProxies: Map<String, ServerSocket>, drawbridgeAddress: String, tlsConfig: SSLContext) {
    val basePort = 10000
    services.forEach { serviceString ->
        val trimmedServiceString = serviceString.substringAfter("PS_LIST: ")
        val portOffset = trimmedServiceString.substring(0, 3).toInt()
        val protectedServiceName = trimmedServiceString.substring(3).trim()
        val localServiceProxyPort = basePort + portOffset

        try {
            val serverSocket = ServerSocket(localServiceProxyPort)
            localServiceProxies.plus(serviceString to serverSocket)
            println("• \"$protectedServiceName\" on localhost:$localServiceProxyPort")

            thread {
                while (true) {
                    try {
                        val clientSocket = serverSocket.accept()
                        println("Accepted connection from ${clientSocket.inetAddress}")

                        thread {
                            handleClientConnection(clientSocket, serviceString, drawbridgeAddress, tlsConfig)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            println("Emissary was unable to start the local proxy server")
            e.printStackTrace()
        }
    }
}

fun handleClientConnection(clientSocket: Socket, protectedServiceString: String, drawbridgeAddress: String, tlsConfig: SSLContext) {
    val sslSocketFactory = tlsConfig.socketFactory as SSLSocketFactory
    val (address, port) = drawbridgeAddress.split(":").let { it[0] to it[1].toInt() }

    try {
        val sslSocket = sslSocketFactory.createSocket() as SSLSocket
        sslSocket.connect(InetSocketAddress(address, port), 10000)  // Set a 10-second timeout
        sslSocket.startHandshake()

        val outputStream: OutputStream = sslSocket.outputStream
        val serviceMessage = "PS_CONN $protectedServiceString"
        outputStream.write(serviceMessage.toByteArray())
        outputStream.flush()

        // Forward data between client and Drawbridge
        thread { clientSocket.getInputStream().copyTo(sslSocket.getOutputStream()) }
        sslSocket.getInputStream().copyTo(clientSocket.getOutputStream())

        sslSocket.close()
        clientSocket.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun createTlsConfig(context: Context): SSLContext {
    try {
        val caFile = context.filesDir.toPath().resolve("put_certificates_and_key_from_drawbridge_here/ca.crt")
        val caInputStream = Files.newInputStream(caFile)
        val certFactory = CertificateFactory.getInstance("X.509")
        val caCert = certFactory.generateCertificate(caInputStream)
        caInputStream.close()

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("caCert", caCert)

        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
        tmf.init(keyStore)

        val clientCertFile = context.filesDir.toPath().resolve("put_certificates_and_key_from_drawbridge_here/emissary-mtls-tcp.crt")
        val clientCertInputStream = Files.newInputStream(clientCertFile)
        val clientCert = certFactory.generateCertificate(clientCertInputStream)
        clientCertInputStream.close()

        val privateKeyFile = context.filesDir.toPath().resolve("put_certificates_and_key_from_drawbridge_here/emissary-mtls-tcp.key")
        val privateKeyContent = Files.readAllLines(privateKeyFile).joinToString("\n")
        val privateKeyPem = privateKeyContent
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKeyDer = Base64.getDecoder().decode(privateKeyPem)

        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(privateKeyDer)
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(pkcs8EncodedKeySpec)

        val clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        clientKeyStore.load(null, null)
        clientKeyStore.setCertificateEntry("clientCert", clientCert)
        clientKeyStore.setKeyEntry("privateKey", privateKey, null, arrayOf(clientCert))

        val kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm()
        val kmf = KeyManagerFactory.getInstance(kmfAlgorithm)
        kmf.init(clientKeyStore, null)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

        return sslContext
    } catch (e: Exception) {
        throw RuntimeException("Failed to create SSLContext", e)
    }
}
