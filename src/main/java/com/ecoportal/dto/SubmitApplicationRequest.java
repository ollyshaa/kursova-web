package com.ecoportal.dto;

import jakarta.validation.constraints.Size;

public record SubmitApplicationRequest(
        @Size(max = 500, message = "Коментар не може перевищувати 500 символів")
        String comment) {
}
