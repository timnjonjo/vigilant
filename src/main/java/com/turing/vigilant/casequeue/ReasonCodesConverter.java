package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.ReasonCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/** Persists the reason codes as a comma-separated string in a single column. */
@Converter
public class ReasonCodesConverter implements AttributeConverter<List<ReasonCode>, String> {

    @Override
    public String convertToDatabaseColumn(List<ReasonCode> reasonCodes) {
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            return "";
        }
        return reasonCodes.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
    }

    @Override
    public List<ReasonCode> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(","))
                .map(String::trim)
                .map(ReasonCode::valueOf)
                .toList();
    }
}
