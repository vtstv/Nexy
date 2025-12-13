package com.nexy.client.utils

import android.util.Patterns

/**
 * Validation utilities for user input
 */
object ValidationUtils {
    
    // Password requirements
    const val MIN_PASSWORD_LENGTH = 8
    const val REQUIRE_UPPERCASE = true
    const val REQUIRE_LOWERCASE = true
    const val REQUIRE_DIGIT = true
    const val REQUIRE_SPECIAL_CHAR = true
    
    // Username requirements
    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 30
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    
    /**
     * Validate email format
     */
    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult(false, "Email is required")
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return ValidationResult(false, "Invalid email format")
        }
        return ValidationResult(true)
    }
    
    /**
     * Validate password complexity
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.isBlank()) {
            return ValidationResult(false, "Password is required")
        }
        
        val errors = mutableListOf<String>()
        
        if (password.length < MIN_PASSWORD_LENGTH) {
            errors.add("at least $MIN_PASSWORD_LENGTH characters")
        }
        
        if (REQUIRE_UPPERCASE && !password.any { it.isUpperCase() }) {
            errors.add("one uppercase letter")
        }
        
        if (REQUIRE_LOWERCASE && !password.any { it.isLowerCase() }) {
            errors.add("one lowercase letter")
        }
        
        if (REQUIRE_DIGIT && !password.any { it.isDigit() }) {
            errors.add("one digit")
        }
        
        if (REQUIRE_SPECIAL_CHAR && !password.any { !it.isLetterOrDigit() }) {
            errors.add("one special character")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Password must contain: ${errors.joinToString(", ")}")
        }
    }
    
    /**
     * Validate username
     */
    fun validateUsername(username: String): ValidationResult {
        if (username.isBlank()) {
            return ValidationResult(false, "Username is required")
        }
        
        if (username.length < MIN_USERNAME_LENGTH) {
            return ValidationResult(false, "Username must be at least $MIN_USERNAME_LENGTH characters")
        }
        
        if (username.length > MAX_USERNAME_LENGTH) {
            return ValidationResult(false, "Username must be less than $MAX_USERNAME_LENGTH characters")
        }
        
        // Only allow alphanumeric and underscore
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return ValidationResult(false, "Username can only contain letters, numbers, and underscores")
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Get password requirements hint
     */
    fun getPasswordRequirementsHint(): String {
        val requirements = mutableListOf<String>()
        requirements.add("$MIN_PASSWORD_LENGTH+ characters")
        if (REQUIRE_UPPERCASE) requirements.add("uppercase")
        if (REQUIRE_LOWERCASE) requirements.add("lowercase")
        if (REQUIRE_DIGIT) requirements.add("digit")
        if (REQUIRE_SPECIAL_CHAR) requirements.add("special char")
        return requirements.joinToString(", ")
    }
    
    /**
     * Validate phone number format (international)
     */
    fun validatePhone(phone: String): ValidationResult {
        if (phone.isBlank()) {
            return ValidationResult(true) // Phone is optional
        }
        
        // Remove spaces, dashes, parentheses for validation
        val cleanPhone = phone.replace(Regex("[\\s\\-()]+"), "")
        
        // Should start with + and contain 10-15 digits
        if (!cleanPhone.matches(Regex("^\\+?[0-9]{10,15}$"))) {
            return ValidationResult(false, "Invalid phone format. Use international format: +1234567890")
        }
        
        return ValidationResult(true)
    }
}
