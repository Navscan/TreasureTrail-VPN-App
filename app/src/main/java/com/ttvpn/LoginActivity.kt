package com.ttvpn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var txtError: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // If already logged in, skip to MainActivity
        if (ConfigStore.getAuthToken(this) != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        txtError = findViewById(R.id.txtError)

        btnLogin.setOnClickListener { 
            val email = editEmail.text.toString().trim()
            val pass = editPassword.text.toString().trim()
            if (validate(email, pass)) doAuth(true, email, pass)
        }

        btnRegister.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val pass = editPassword.text.toString().trim()
            if (validate(email, pass)) doAuth(false, email, pass)
        }
    }

    private fun validate(email: String, pass: String): Boolean {
        if (email.isEmpty() || pass.isEmpty()) {
            showError("Please enter email and password")
            return false
        }
        if (pass.length < 6) {
            showError("Password must be at least 6 characters")
            return false
        }
        return true
    }

    private fun doAuth(isLogin: Boolean, email: String, pass: String) {
        btnLogin.isEnabled = false
        btnRegister.isEnabled = false
        txtError.visibility = View.GONE

        executor.execute {
            val result = if (isLogin) AuthClient.login(email, pass) else AuthClient.register(email, pass)
            
            handler.post {
                btnLogin.isEnabled = true
                btnRegister.isEnabled = true

                when (result) {
                    is AuthResult.Success -> {
                        ConfigStore.saveAuth(this, result.token, result.tier)
                        EventLog.write(this, "OK", "Authenticated as $email")
                        goToMain()
                    }
                    is AuthResult.Failure -> {
                        showError(result.error.replace("_", " "))
                    }
                    AuthResult.NetworkError -> {
                        showError("Network error. Check connection.")
                    }
                }
            }
        }
    }

    private fun showError(msg: String) {
        txtError.text = msg
        txtError.setTextColor(ContextCompat.getColor(this, R.color.state_red))
        txtError.visibility = View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
