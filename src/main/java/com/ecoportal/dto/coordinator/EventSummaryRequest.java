package com.ecoportal.dto.coordinator;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EventSummaryRequest(
        @NotNull(message = "Фактична кількість учасників є обов'язковою")
        @Min(value = 0, message = "Фактична кількість учасників не може бути від'ємною")
        Integer actualParticipants,

        @NotNull(message = "Маса зібраних відходів є обов'язковою")
        @DecimalMin(value = "0", message = "Маса відходів не може бути від'ємною")
        @Digits(integer = 8, fraction = 2, message = "Маса відходів: до 8 цифр до коми та 2 після коми")
        BigDecimal wasteKg,

        @NotNull(message = "Кількість висаджених дерев є обов'язковою")
        @Min(value = 0, message = "Кількість дерев не може бути від'ємною")
        Integer treesPlanted,

        @NotNull(message = "Кількість годин є обов'язковою")
        @DecimalMin(value = "0", message = "Кількість годин не може бути від'ємною")
        @Digits(integer = 8, fraction = 2, message = "Кількість годин: до 8 цифр до коми та 2 після коми")
        BigDecimal hoursSpent,

        @Size(max = 1000, message = "Примітка не може перевищувати 1000 символів")
        String note) {
}
