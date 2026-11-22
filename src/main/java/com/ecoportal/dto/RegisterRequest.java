package com.ecoportal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Поля ролі тут навмисно немає: реєстрація завжди створює PARTICIPANT,
 * а будь-яка роль у тілі запиту ігнорується.
 */
public record RegisterRequest(
        @NotBlank(message = "Повне ім'я є обов'язковим")
        @Size(max = 150, message = "Повне ім'я не може перевищувати 150 символів")
        String fullName,

        @NotBlank(message = "Email є обов'язковим")
        @Email(message = "Некоректний формат email")
        @Size(max = 254, message = "Email не може перевищувати 254 символи")
        String email,

        @NotBlank(message = "Пароль є обов'язковим")
        @Size(min = 8, max = 72, message = "Пароль має містити від 8 до 72 символів")
        String password) {
}
