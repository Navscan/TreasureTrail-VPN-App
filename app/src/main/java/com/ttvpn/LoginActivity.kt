package com.ttvpn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var txtError: TextView
    private lateinit var progressBar: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)
        txtError = findViewById(R.id.txtError)
        progressBar = findViewById(R.id.progressBar)

        btnLogin.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val password = editPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                showError("Please enter email and password.")
                return@setOnClickListener
            }

            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        btnLogin.isEnabled = false
        progressBar.visibility = View.VISIBLE
        txtError.visibility = View.GONE

        executor.execute {
            val result = AuthClient.login(email, password)
            runOnUiThread {
                btnLogin.isEnabled = true
                progressBar.visibility = View.GONE

                when (result) {
                    is AuthResult.Success -> {
                        // CRITICAL FIX: Save the new Enterprise JWT token and tier, overwriting the Guest token
                        ConfigStore.saveAuth(this, result.token, result.tier)
                        EventLog.write(this, "OK", "Authenticated as $email")
                        
                        // Close LoginActivity and return to MainActivity
                        finish()
                    }
                    is AuthResult.Failure -> {
                        showError("Login failed: ${result.error}")
                    }
                    AuthResult.NetworkError -> {
                        showError("Network error. Could not reach backend.")
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        txtError.text = message
        txtError.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
