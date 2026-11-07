package com.ttvpn

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
    private lateinit var btnToggleMode: TextView
    private lateinit var txtError: TextView
    private lateinit var progressBar: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        txtError = findViewById(R.id.txtError)
        progressBar = findViewById(R.id.progressBar)

        btnToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            txtError.visibility = View.GONE
            if (isLoginMode) {
                btnLogin.text = "SIGN IN"
                btnToggleMode.text = "Need an account? Register"
            } else {
                btnLogin.text = "REGISTER"
                btnToggleMode.text = "Already have an account? Sign In"
            }
        }

        btnLogin.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val password = editPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                showError("Please enter email and password.")
                return@setOnClickListener
            }

            performAction(email, password)
        }
    }

    private fun performAction(email: String, password: String) {
        btnLogin.isEnabled = false
        btnToggleMode.isEnabled = false
        progressBar.visibility = View.VISIBLE
        txtError.visibility = View.GONE

        executor.execute {
            val result = if (isLoginMode) {
                AuthClient.login(email, password)
            } else {
                AuthClient.register(email, password)
            }

            runOnUiThread {
                btnLogin.isEnabled = true
                btnToggleMode.isEnabled = true
                progressBar.visibility = View.GONE

                when (result) {
                    is AuthResult.Success -> {
                        ConfigStore.saveAuth(this, result.token, result.tier)
                        EventLog.write(this, "OK", "Authenticated as $email")
                        finish()
                    }
                    is AuthResult.Failure -> {
                        if (!isLoginMode && result.error == "email_exists") {
                            showError("Email already registered. Try logging in.")
                        } else {
                            showError("Failed: ${result.error}")
                        }
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
